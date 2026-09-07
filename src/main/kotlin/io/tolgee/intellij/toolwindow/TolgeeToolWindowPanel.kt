package io.tolgee.intellij.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBLabel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import io.tolgee.intellij.project.TolgeeKeyCache
import io.tolgee.intellij.project.TolgeeProjectLink
import io.tolgee.intellij.pull.PullAction
import io.tolgee.intellij.push.PushAction
import io.tolgee.intellij.settings.TolgeeAppSettings
import io.tolgee.intellij.util.TolgeeIcons
import io.tolgee.intellij.util.TranslationFiles
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class TolgeeToolWindowPanel(private val project: Project) {

    private val root = DefaultMutableTreeNode(NodeData("Tolgee", TolgeeIcons.TOOL_WINDOW))
    private val treeModel = DefaultTreeModel(root)
    private val tree = Tree(treeModel).apply {
        isRootVisible = true
        showsRootHandles = true
        cellRenderer = NodeRenderer()
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                val node = lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
                val data = node.userObject as? NodeData ?: return
                val file = data.file ?: return
                if (!file.isDirectory) FileEditorManager.getInstance(project).openFile(file, true)
            }
        })
    }
    private val emptyLabel = JBLabel("No Tolgee project linked. Click + to add one.").apply {
        border = JBUI.Borders.empty(12)
    }

    // The toolbar's actions read e.project via CommonDataKeys.PROJECT from the
    // DataContext derived from targetComponent. Without an explicit provider on
    // our panel, IntelliJ (and Android Studio, in particular) can end up with
    // e.project == null in AnAction.update — the "+" button ends up permanently
    // disabled and the whole toolbar looks dead.
    private val container = object : JPanel(BorderLayout()), DataProvider {
        override fun getData(dataId: String): Any? =
            if (CommonDataKeys.PROJECT.`is`(dataId)) project else null
    }

    val component: JComponent get() = container

    init {
        installContextMenu()
        rebuild()
        project.messageBus.connect(project).subscribe(
            TolgeeProjectLink.TOPIC,
            TolgeeProjectLink.Listener { rebuild() },
        )
    }

    private fun installContextMenu() {
        tree.addMouseListener(object : PopupHandler() {
            override fun invokePopup(comp: java.awt.Component, x: Int, y: Int) {
                val path = tree.getPathForLocation(x, y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val group = buildContextMenu(node) ?: return
                tree.selectionPath = path
                val menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, group)
                menu.component.show(comp, x, y)
            }
        })
    }

    private fun buildContextMenu(node: DefaultMutableTreeNode): DefaultActionGroup? {
        val group = DefaultActionGroup()
        val am = ActionManager.getInstance()
        if (node === root) {
            am.getAction("Tolgee.EditProject")?.let { group.add(it) }
            group.addSeparator()
            group.add(scopedPush("Push to Tolgee", null, null))
            group.add(scopedPull("Pull from Tolgee", null, null))
            group.add(refreshAction())
            return group
        }
        val data = node.userObject as? NodeData ?: return null
        val vf = data.file ?: return null
        val parent = node.parent as? DefaultMutableTreeNode ?: return null
        return when {
            vf.isDirectory && parent === root -> {
                val ns = vf.name
                group.add(scopedPush("Push namespace '$ns' to Tolgee", setOf(ns), null))
                group.add(scopedPull("Pull namespace '$ns' from Tolgee", setOf(ns), null))
                group
            }
            !vf.isDirectory && vf.name.endsWith(".json") -> {
                val lang = vf.name.removeSuffix(".json")
                val parentData = parent.userObject as? NodeData
                val ns = if (parentData?.file?.isDirectory == true && parent.parent === root) parentData.file.name else ""
                val label = if (ns.isEmpty()) "$lang.json" else "$ns/$lang.json"
                group.add(scopedPush("Push $label to Tolgee", setOf(ns), setOf(lang)))
                group.add(scopedPull("Pull $label from Tolgee", setOf(ns), setOf(lang)))
                group
            }
            else -> null
        }
    }

    private fun scopedPush(title: String, ns: Set<String>?, lang: Set<String>?): AnAction =
        object : AnAction(title, null, AllIcons.Actions.Upload) {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
            override fun actionPerformed(e: AnActionEvent) = PushAction.runFor(project, ns, lang)
        }

    private fun scopedPull(title: String, ns: Set<String>?, lang: Set<String>?): AnAction =
        object : AnAction(title, null, AllIcons.Actions.Download) {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
            override fun actionPerformed(e: AnActionEvent) = PullAction.runFor(project, ns, lang)
        }

    private fun refreshAction(): AnAction =
        object : AnAction("Refresh", null, AllIcons.Actions.Refresh) {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
            override fun actionPerformed(e: AnActionEvent) {
                TolgeeKeyCache.getInstance(project).refreshAsync()
            }
        }

    fun rebuild() {
        container.removeAll()
        val link = TolgeeProjectLink.getInstance(project)
        val configured = TolgeeAppSettings.getInstance().isConfigured
        if (!link.isLinked) {
            container.add(emptyLabel, BorderLayout.NORTH)
        } else if (!configured) {
            container.add(buildReconnectPanel(), BorderLayout.NORTH)
        } else {
            root.userObject = NodeData(link.tolgeeProjectName, TolgeeIcons.TOOL_WINDOW)
            root.removeAllChildren()
            val dir = TranslationFiles.resolveDir(project, link.translationsPath)
            if (dir != null && dir.isDirectory) {
                addChildren(root, dir)
            } else {
                root.add(DefaultMutableTreeNode(NodeData("(${link.translationsPath} — not found)", AllIcons.General.Warning)))
            }
            treeModel.reload()
            expandAll()
            container.add(JScrollPane(tree), BorderLayout.CENTER)
        }
        container.revalidate()
        container.repaint()
    }

    // Shown by rebuild() when the link is present but the API key isn't (fresh
    // install after upgrade, OS keychain cleared, keychain access denied). Toolbar
    // actions all depend on isConfigured, so without this fallback the user has no
    // reachable way to reopen the Add/Edit dialog.
    private fun buildReconnectPanel(): JComponent {
        val label = JBLabel("Tolgee API key not available. Reopen the connection dialog to re-enter it.")
        val button = JButton("Edit Connection…").apply {
            addActionListener { AddProjectDialog(project).show() }
        }
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(label, BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 8)).apply { add(button) }, BorderLayout.CENTER)
        }
    }

    private fun addChildren(node: DefaultMutableTreeNode, vf: VirtualFile) {
        val sorted = vf.children.orEmpty().sortedWith(compareBy({ !it.isDirectory }, { it.name }))
        for (child in sorted) {
            val icon = if (child.isDirectory) AllIcons.Nodes.Folder else AllIcons.FileTypes.Json
            val n = DefaultMutableTreeNode(NodeData(child.name, icon, child))
            node.add(n)
            if (child.isDirectory) addChildren(n, child)
        }
    }

    private fun expandAll() {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    private data class NodeData(val label: String, val icon: Icon, val file: VirtualFile? = null)

    private class NodeRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            val data = (value as? DefaultMutableTreeNode)?.userObject as? NodeData ?: return
            icon = data.icon
            append(data.label)
        }
    }
}
