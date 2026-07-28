package io.tolgee.intellij.toolwindow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class TolgeeToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TolgeeToolWindowPanel(project)

        val am = ActionManager.getInstance()
        val group = DefaultActionGroup().apply {
            am.getAction("Tolgee.AddProject")?.let { add(it) }
            am.getAction("Tolgee.Unlink")?.let { add(it) }
            addSeparator()
            am.getAction("Tolgee.Push")?.let { add(it) }
            am.getAction("Tolgee.Pull")?.let { add(it) }
            am.getAction("Tolgee.RefreshKeys")?.let { add(it) }
        }
        val actionToolbar = am.createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, true).apply {
            targetComponent = panel.component
        }

        val wrapper = SimpleToolWindowPanel(true, true).apply {
            setToolbar(actionToolbar.component)
            setContent(panel.component)
        }

        val content = ContentFactory.getInstance().createContent(wrapper, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
