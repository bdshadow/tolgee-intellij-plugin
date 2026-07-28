package io.tolgee.intellij.push

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import io.tolgee.intellij.api.TolgeeApiClient
import io.tolgee.intellij.project.TolgeeKeyCache
import io.tolgee.intellij.project.TolgeeProjectLink
import io.tolgee.intellij.project.requireConfiguredLink
import io.tolgee.intellij.settings.TolgeeAppSettings
import io.tolgee.intellij.util.TranslationFiles

class PushAction : AnAction() {
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

            val confirm = Messages.showYesNoDialog(
                project,
                "Push ${files.size} translation file(s) to Tolgee project '${link.tolgeeProjectName}'?\n" +
                    "Existing translations will be overwritten.",
                "Push to Tolgee",
                Messages.getQuestionIcon(),
            )
            if (confirm != Messages.YES) return

            val task = object : Task.Backgroundable(project, "Pushing translations to Tolgee", true) {
                private var pushed = 0
                private var err: Throwable? = null

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = false
                    val client = TolgeeApiClient(settings.instanceUrl, settings.apiKey)
                    files.forEachIndexed { idx, lf ->
                        indicator.fraction = idx.toDouble() / files.size
                        indicator.text = if (lf.namespace == null) "Pushing ${lf.language}…"
                        else "Pushing ${lf.namespace}/${lf.language}…"
                        try {
                            client.importFlatJson(
                                projectId = link.tolgeeProjectId,
                                languageTag = lf.language,
                                flatJsonBytes = lf.file.contentsToByteArray(),
                                namespace = lf.namespace,
                                overrideExisting = true,
                            )
                            pushed++
                        } catch (e: Exception) {
                            err = e
                            return
                        }
                    }
                    TolgeeKeyCache.getInstance(project).refreshAsync()
                }

                override fun onFinished() {
                    ApplicationManager.getApplication().invokeLater {
                        if (err != null) {
                            Messages.showErrorDialog(project, err!!.message ?: "Push failed", "Tolgee")
                        } else {
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup("Tolgee")
                                .createNotification("Pushed $pushed file(s) to Tolgee.", NotificationType.INFORMATION)
                                .notify(project)
                        }
                    }
                }
            }
            task.queue()
        }
    }
}
