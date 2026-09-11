package com.dbocharov.tolgee.toolwindow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.dbocharov.tolgee.project.TolgeeProjectLink

class EditProjectAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible =
            project != null && TolgeeProjectLink.getInstance(project).isLinked
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        AddProjectDialog(project).show()
    }
}
