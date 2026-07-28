package io.tolgee.intellij.toolwindow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.tolgee.intellij.project.TolgeeProjectLink

class AddProjectAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val linked = project != null && TolgeeProjectLink.getInstance(project).isLinked
        e.presentation.isEnabled = !linked
        if (linked) {
            // The tooltip title comes from `text`; `description` alone isn't
            // reliably shown for disabled toolbar buttons.
            e.presentation.text = "Only one connection is possible at the moment"
            e.presentation.description = e.presentation.text
        } else {
            e.presentation.text = "Add Tolgee Project"
            e.presentation.description = "Configure a Tolgee instance and link a project"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        AddProjectDialog(project).show()
    }
}
