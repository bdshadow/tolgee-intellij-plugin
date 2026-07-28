package io.tolgee.intellij.toolwindow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import io.tolgee.intellij.project.TolgeeProjectLink

class UnlinkProjectAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && TolgeeProjectLink.getInstance(project).isLinked
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val link = TolgeeProjectLink.getInstance(project)
        if (!link.isLinked) return
        val confirm = Messages.showYesNoDialog(
            project,
            "Unlink from Tolgee project '${link.tolgeeProjectName}'?",
            "Unlink",
            Messages.getQuestionIcon(),
        )
        if (confirm != Messages.YES) return
        link.unlink()
        link.fireChanged()
    }
}
