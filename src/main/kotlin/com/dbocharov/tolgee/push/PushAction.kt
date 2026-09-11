package com.dbocharov.tolgee.push

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.dbocharov.tolgee.api.TolgeeApiClient
import com.dbocharov.tolgee.project.TolgeeKeyCache
import com.dbocharov.tolgee.project.TolgeeProjectLink
import com.dbocharov.tolgee.project.isTolgeeReady
import com.dbocharov.tolgee.project.requireConfiguredLink
import com.dbocharov.tolgee.settings.TolgeeAppSettings
import com.dbocharov.tolgee.util.TolgeeFilterMath
import com.dbocharov.tolgee.util.TolgeeNotifications
import com.dbocharov.tolgee.util.TranslationFiles
import java.nio.file.Files
import java.nio.file.Paths

class PushAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = isTolgeeReady(e.project)
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
            val dir = TranslationFiles.resolveDir(project, link.translationsPath)
            if (dir == null || !dir.isDirectory) {
                Messages.showWarningDialog(
                    project,
                    "Translations directory '${link.translationsPath}' does not exist.",
                    "Tolgee",
                )
                return
            }
            val nsFilter: Set<String>? = nsOverride ?: link.namespaces.toSet().takeIf { it.isNotEmpty() }
            val langFilter: Set<String>? = langOverride ?: link.languages.toSet().takeIf { it.isNotEmpty() }
            val files = TranslationFiles.listAllLanguageFiles(dir).filter { lf ->
                (nsFilter == null || (lf.namespace ?: "") in nsFilter) &&
                    (langFilter == null || lf.language in langFilter)
            }
            if (files.isEmpty()) {
                Messages.showWarningDialog(
                    project,
                    "No <lang>.json files (or <namespace>/<lang>.json) in ${link.translationsPath}.",
                    "Tolgee",
                )
                return
            }

            val previewLimit = 15
            val fileLabels = files.map(TolgeeFilterMath::labelFor)
            val preview = fileLabels.take(previewLimit).joinToString("\n") { "  • $it" }
            val tail = if (fileLabels.size > previewLimit) "\n  … and ${fileLabels.size - previewLimit} more" else ""
            val confirm = Messages.showYesNoDialog(
                project,
                "Push ${files.size} translation file(s) to Tolgee project '${link.tolgeeProjectName}'? " +
                    "Existing translations will be overwritten.\n\n$preview$tail",
                "Push to Tolgee",
                Messages.getQuestionIcon(),
            )
            if (confirm != Messages.YES) return

            // Any file the user is editing may still have unsaved changes in the editor buffer;
            // flush them to disk (must run on EDT) before the background task reads bytes.
            FileDocumentManager.getInstance().saveAllDocuments()

            val task = object : Task.Backgroundable(project, "Pushing translations to Tolgee", true) {
                private var pushed = 0
                private val silentDrops = mutableListOf<String>()
                private var err: Throwable? = null
                private var startedAt = 0L

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = false
                    startedAt = System.nanoTime()
                    log.info("Push started (project=${link.tolgeeProjectId}, files=${files.size})")
                    val client = TolgeeApiClient(settings.instanceUrl, settings.apiKey)
                    files.forEachIndexed { idx, lf ->
                        indicator.fraction = idx.toDouble() / files.size
                        indicator.text = if (lf.namespace == null) "Pushing ${lf.language}…"
                        else "Pushing ${lf.namespace}/${lf.language}…"
                        try {
                            // Read straight from disk: VirtualFile.contentsToByteArray caches, and can
                            // serve stale bytes when the file was written outside IntelliJ.
                            val bytes = Files.readAllBytes(Paths.get(lf.file.path))
                            val acknowledged = client.importFlatJson(
                                projectId = link.tolgeeProjectId,
                                languageTag = lf.language,
                                flatJsonBytes = bytes,
                                namespace = lf.namespace,
                                overrideExisting = true,
                            )
                            if (acknowledged) pushed++
                            else silentDrops += TolgeeFilterMath.labelFor(lf)
                        } catch (e: Exception) {
                            err = e
                            return
                        }
                    }
                    TolgeeKeyCache.getInstance(project).refreshAsync()
                }

                override fun onFinished() {
                    val ms = if (startedAt != 0L) (System.nanoTime() - startedAt) / 1_000_000 else -1
                    if (err != null) {
                        log.warn("Push failed after ${ms}ms (pushed=$pushed): ${err!!.message}", err)
                    } else {
                        log.info("Push done: $pushed file(s), ${silentDrops.size} silently dropped, in ${ms}ms")
                    }
                    ApplicationManager.getApplication().invokeLater {
                        if (err != null) {
                            Messages.showErrorDialog(project, err!!.message ?: "Push failed", "Tolgee")
                        } else if (silentDrops.isNotEmpty()) {
                            TolgeeNotifications.warn(
                                project,
                                "Pushed $pushed file(s). ${silentDrops.size} file(s) were silently dropped by the Tolgee server " +
                                    "(likely a missing API key scope or unknown language): ${silentDrops.joinToString(", ")}. " +
                                    "See idea.log for the response bodies.",
                            )
                        } else {
                            TolgeeNotifications.info(project, "Pushed $pushed file(s) to Tolgee.")
                        }
                    }
                }

            }
            task.queue()
        }

        private val log = Logger.getInstance(PushAction::class.java)
    }
}
