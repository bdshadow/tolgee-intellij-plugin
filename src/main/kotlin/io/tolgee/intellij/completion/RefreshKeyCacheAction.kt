package io.tolgee.intellij.completion

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import io.tolgee.intellij.project.TolgeeKeyCache
import io.tolgee.intellij.project.TolgeeProjectLink
import io.tolgee.intellij.settings.TolgeeAppSettings

class RefreshKeyCacheAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        val project = e.project
        val link = project?.let { TolgeeProjectLink.getInstance(it) }
        e.presentation.isEnabled = project != null && link?.isLinked == true && TolgeeAppSettings.getInstance().isConfigured
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        TolgeeKeyCache.getInstance(project).refreshAsync { result ->
            ApplicationManager.getApplication().invokeLater {
                result.fold(
                    onSuccess = { count ->
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Tolgee")
                            .createNotification("Refreshed Tolgee keys: $count cached.", NotificationType.INFORMATION)
                            .notify(project)
                    },
                    onFailure = { Messages.showErrorDialog(project, it.message ?: "Refresh failed", "Tolgee") },
                )
            }
        }
    }
}
