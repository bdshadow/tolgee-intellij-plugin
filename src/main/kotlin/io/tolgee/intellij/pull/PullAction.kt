package io.tolgee.intellij.pull

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsFileListenerContextHelper
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import io.tolgee.intellij.api.TolgeeApiClient
import io.tolgee.intellij.api.TolgeeKey
import io.tolgee.intellij.project.TolgeeKeyCache
import io.tolgee.intellij.project.TolgeeProjectLink
import io.tolgee.intellij.project.requireConfiguredLink
import io.tolgee.intellij.settings.TolgeeAppSettings
import io.tolgee.intellij.util.TranslationFiles
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class PullAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        val project = e.project
        val link = project?.let { TolgeeProjectLink.getInstance(it) }
        e.presentation.isEnabled = project != null && link?.isLinked == true && TolgeeAppSettings.getInstance().isConfigured
    }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        runFor(project)
    }

    companion object {
        /**
         * @param nsOverride  null = use link.namespaces (empty = all); non-null = restrict to
         *                    exactly these. `""` in the set matches root-level (no namespace).
         * @param langOverride same semantics for languages.
         */
        fun runFor(
            project: Project,
            nsOverride: Set<String>? = null,
            langOverride: Set<String>? = null,
        ) {
            val link = requireConfiguredLink(project) ?: return
            val settings = TolgeeAppSettings.getInstance()
            val destPath = link.translationsPath
            notify(project, "Pulling into <project>/$destPath …")

            val task = object : Task.Backgroundable(project, "Pulling translations from Tolgee", true) {
                private var writtenFiles = 0
                private var keyCount = 0
                private var err: Throwable? = null
                private var startedAt = 0L

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    startedAt = System.nanoTime()
                    val nsScope = nsOverride?.let { "ns=${it.size}" } ?: "ns=link"
                    val langScope = langOverride?.let { "lang=${it.size}" } ?: "lang=link"
                    log.info("Pull started (project=${link.tolgeeProjectId}, dest=$destPath, $nsScope, $langScope)")
                    val client = TolgeeApiClient(settings.instanceUrl, settings.apiKey)

                    val nsFilter: Set<String>? = nsOverride ?: link.namespaces.toSet().takeIf { it.isNotEmpty() }
                    val langFilter: Set<String>? = langOverride ?: link.languages.toSet().takeIf { it.isNotEmpty() }

                    // The /translations endpoint returns only a subset of languages when `languages`
                    // is unspecified (typically base + one). To honour "all languages", resolve the
                    // full project language list here and pass it explicitly.
                    val requestedLangs: List<String> = try {
                        if (langFilter != null) {
                            langFilter.toList()
                        } else {
                            val all = client.listProjectLanguages(link.tolgeeProjectId)
                            // Keep the saved base language fresh — the user may have changed it on
                            // the server since the last Add/Edit round-trip.
                            all.firstOrNull { it.base }?.tag?.let { newBase ->
                                if (newBase.isNotBlank() && newBase != link.baseLanguage) {
                                    link.baseLanguage = newBase
                                }
                            }
                            all.map { it.tag }
                        }
                    } catch (e: Exception) {
                        err = e
                        return
                    }

                    val allKeys: List<TolgeeKey> = try {
                        client.listAllKeys(link.tolgeeProjectId, requestedLangs)
                    } catch (e: Exception) {
                        err = e
                        return
                    }
                    val filtered = allKeys.filter { k ->
                        nsFilter == null || (k.keyNamespace ?: "") in nsFilter
                    }
                    keyCount = filtered.size
                    if (filtered.isEmpty()) {
                        err = IllegalStateException(
                            "No keys returned from Tolgee (project ${link.tolgeeProjectId}). " +
                                "Check API key scopes and Namespaces filter in the project config."
                        )
                        return
                    }

                    val buckets = linkedMapOf<Pair<String, String>, MutableMap<String, JsonElement>>()
                    for (key in filtered) {
                        val ns = key.keyNamespace.orEmpty()
                        for ((lang, tr) in key.translations) {
                            if (langFilter != null && lang !in langFilter) continue
                            val text = tr.text ?: continue
                            buckets.getOrPut(ns to lang) { linkedMapOf() }[key.keyName] = JsonPrimitive(text)
                        }
                    }
                    if (buckets.isEmpty()) {
                        err = IllegalStateException("Keys were found but no translations matched the Languages filter.")
                        return
                    }

                    val rootDir = TranslationFiles.ensureDir(project, destPath)

                    // Best-effort VCS suppression for the files we're about to add.
                    val toIgnore = buckets.keys.map { (ns, lang) ->
                        val rel = if (ns.isEmpty()) "$lang.json" else "$ns/$lang.json"
                        VcsUtil.getFilePath("${rootDir.path}/$rel", false)
                    }
                    try {
                        project.getService(VcsFileListenerContextHelper::class.java).ignoreAdded(toIgnore)
                    } catch (_: Throwable) {
                        // Helper changed class↔interface across platform versions; safe to skip.
                    }

                    val nsDirs = mutableMapOf<String, VirtualFile>("" to rootDir)
                    for ((coord, flat) in buckets) {
                        val (ns, lang) = coord
                        indicator.text = if (ns.isEmpty()) "Writing $lang…" else "Writing $ns/$lang…"
                        val dir = nsDirs.getOrPut(ns) {
                            WriteAction.computeAndWait<VirtualFile, RuntimeException> {
                                VfsUtil.createDirectoryIfMissing(rootDir, ns)
                                    ?: error("Failed to create namespace directory $ns")
                            }
                        }
                        try {
                            TranslationFiles.writeFlatJson(dir, lang, flat)
                            writtenFiles++
                        } catch (e: Exception) {
                            err = e
                            return
                        }
                    }
                    // Project view / file index miss nested creates from a background
                    // WriteAction — force a sync recursive refresh to catch them.
                    VfsUtil.markDirtyAndRefresh(false, true, true, rootDir)
                    TolgeeKeyCache.getInstance(project).refreshAsync()
                }

                override fun onFinished() {
                    val ms = if (startedAt != 0L) (System.nanoTime() - startedAt) / 1_000_000 else -1
                    if (err != null) {
                        log.warn("Pull failed after ${ms}ms: ${err!!.message}", err)
                    } else {
                        log.info("Pull done: $keyCount key(s), $writtenFiles file(s) in ${ms}ms")
                    }
                    ApplicationManager.getApplication().invokeLater {
                        if (err != null) {
                            Messages.showErrorDialog(project, err!!.message ?: "Pull failed", "Tolgee")
                        } else {
                            notify(
                                project,
                                "Pulled $keyCount key(s) into $writtenFiles file(s) under <project>/$destPath.",
                            )
                            TolgeeProjectLink.getInstance(project).fireChanged()
                        }
                    }
                }
            }
            task.queue()
        }

        private fun notify(project: Project, message: String) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Tolgee")
                .createNotification(message, NotificationType.INFORMATION)
                .notify(project)
        }

        private val log = Logger.getInstance(PullAction::class.java)
    }
}
