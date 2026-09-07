package io.tolgee.intellij.completion

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import io.tolgee.intellij.project.TolgeeKeyCache
import io.tolgee.intellij.project.isTolgeeReady
import io.tolgee.intellij.util.TolgeeNotifications

class RefreshKeyCacheAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = isTolgeeReady(e.project)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        TolgeeKeyCache.getInstance(project).refreshAsync { result ->
            ApplicationManager.getApplication().invokeLater {
                result.fold(
                    onSuccess = { count -> TolgeeNotifications.info(project, "Refreshed Tolgee keys: $count cached.") },
                    onFailure = { Messages.showErrorDialog(project, it.message ?: "Refresh failed", "Tolgee") },
                )
            }
        }
    }
}
