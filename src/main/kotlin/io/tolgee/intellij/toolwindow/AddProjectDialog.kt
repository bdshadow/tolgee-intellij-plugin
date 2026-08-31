package io.tolgee.intellij.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.CollapsibleRow
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBDimension
import io.tolgee.intellij.api.TolgeeApiClient
import io.tolgee.intellij.api.TolgeeProject
import io.tolgee.intellij.project.TolgeeKeyCache
import io.tolgee.intellij.project.TolgeeProjectLink
import io.tolgee.intellij.pull.PullAction
import io.tolgee.intellij.settings.TolgeeAppSettings
import io.tolgee.intellij.util.TranslationFiles
import java.awt.event.ItemEvent
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent

class AddProjectDialog(private val ideProject: Project) : DialogWrapper(ideProject) {

    private val settings = TolgeeAppSettings.getInstance()
    private val link = TolgeeProjectLink.getInstance(ideProject)

    private val urlField = JBTextField(settings.instanceUrl.ifBlank { "https://app.tolgee.io" })
    private val apiKeyField = JBPasswordField().apply { text = settings.apiKey }
    private val projectCombo = ComboBox<TolgeeProject>().apply { renderer = TolgeeProjectListRenderer() }
    private val refreshProjectsButton = JButton("Load projects")
    private val pathField = JBTextField(link.translationsPath.ifBlank { ".tolgee" })
    private val autoPullCheckbox = JBCheckBox("Pull translations after creating the connection", true)
    private val isCreation = !link.isLinked

    private val namespacesList = CheckBoxList<String>().apply {
        isEnabled = false
        setStringItems(linkedMapOf(ALL_NAMESPACES to true))
    }
    private val languagesList = CheckBoxList<String>().apply {
        isEnabled = false
        setStringItems(linkedMapOf(ALL_LANGUAGES to true))
    }

    // Guards CheckBoxList listeners from re-entering during programmatic updates.
    private var suppressListListeners = false

    // Drop metadata responses from superseded loads (user clicked between projects).
    private var metaEpoch = 0

    private lateinit var advancedGroup: CollapsibleRow

    init {
        title = if (link.isLinked) "Edit Tolgee Project" else "Add Tolgee Project"
        setOKButtonText(if (link.isLinked) "Save" else "Add")
        refreshProjectsButton.addActionListener { refreshProjects() }
        projectCombo.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                (e.item as? TolgeeProject)?.let { loadProjectMeta(it.id) }
            }
            // DialogWrapper only auto-tracks text/checkbox fields; poke the OK state on combo changes.
            refreshOkState()
        }
        installExclusiveAllListener(namespacesList, ALL_NAMESPACES)
        installExclusiveAllListener(languagesList, ALL_LANGUAGES)
        init()
        if (link.isLinked) {
            // Show current selection as a stub so OK is enabled without reloading;
            // the selection change also kicks off a metadata load.
            val stub = TolgeeProject(id = link.tolgeeProjectId, name = link.tolgeeProjectName)
            val model = DefaultComboBoxModel<TolgeeProject>()
            model.addElement(stub)
            projectCombo.model = model
            projectCombo.selectedIndex = 0
        }
        refreshOkState()
    }

    private fun refreshOkState() {
        isOKActionEnabled = doValidate() == null
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Instance URL:") { cell(urlField).resizableColumn().align(AlignX.FILL) }
        row("API key:") { cell(apiKeyField).resizableColumn().align(AlignX.FILL) }
        row("Tolgee project:") {
            cell(projectCombo).resizableColumn().align(AlignX.FILL)
            cell(refreshProjectsButton)
        }
        row("Translations path:") { cell(pathField).resizableColumn().align(AlignX.FILL) }
        if (isCreation) {
            row { cell(autoPullCheckbox) }
        }
        advancedGroup = collapsibleGroup("Advanced") {
            row("Namespaces:") {
                cell(JBScrollPane(namespacesList).apply { preferredSize = JBDimension(320, 120) })
                    .resizableColumn().align(AlignX.FILL)
            }
            row("Languages:") {
                cell(JBScrollPane(languagesList).apply { preferredSize = JBDimension(320, 120) })
                    .resizableColumn().align(AlignX.FILL)
            }
        }
    }.also {
        advancedGroup.expanded = link.isLinked
    }

    override fun doValidate(): ValidationInfo? {
        if (urlField.text.isBlank()) return ValidationInfo("Instance URL is required", urlField)
        if (apiKeyField.password.isEmpty()) return ValidationInfo("API key is required", apiKeyField)
        if (projectCombo.selectedItem == null) return ValidationInfo("Load and select a Tolgee project", projectCombo)
        if (pathField.text.isBlank()) return ValidationInfo("Translations path is required", pathField)
        return null
    }

    override fun doOKAction() {
        settings.instanceUrl = urlField.text.trim()
        settings.apiKey = String(apiKeyField.password)

        val selected = projectCombo.selectedItem as? TolgeeProject ?: return
        link.tolgeeProjectId = selected.id
        link.tolgeeProjectName = selected.name
        link.namespaces = collectNamespaces().toMutableList()
        link.translationsPath = pathField.text.trim().ifEmpty { ".tolgee" }
        link.languages = collectSelection(languagesList, ALL_LANGUAGES).toMutableList()

        // Materialise the directory now so the tree renders as "empty" not "missing".
        try {
            TranslationFiles.ensureDir(ideProject, link.translationsPath)
        } catch (_: Exception) {
            // Pull/push will surface the error later if it still matters.
        }

        super.doOKAction()
        link.fireChanged()
        if (isCreation && autoPullCheckbox.isSelected) {
            // Pull refreshes the cache itself, so no extra call here.
            PullAction.runFor(ideProject)
        } else {
            TolgeeKeyCache.getInstance(ideProject).refreshAsync()
        }
    }

    private fun refreshProjects() {
        val url = urlField.text.trim()
        val apiKey = String(apiKeyField.password)
        if (url.isBlank() || apiKey.isBlank()) {
            Messages.showWarningDialog(ideProject, "Enter URL and API key first.", "Tolgee")
            return
        }
        val task = object : Task.Modal(ideProject, "Loading Tolgee projects", true) {
            private var result: List<TolgeeProject> = emptyList()
            private var err: Throwable? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                try {
                    val client = TolgeeApiClient(url, apiKey)
                    val boundProjectId = client.currentApiKeyProjectId()
                    result = if (boundProjectId != null) {
                        listOf(client.getProject(boundProjectId))
                    } else {
                        client.listProjects()
                    }
                } catch (e: Exception) {
                    err = e
                }
            }

            override fun onFinished() {
                ApplicationManager.getApplication().invokeLater {
                    if (err != null) {
                        Messages.showErrorDialog(ideProject, err!!.message ?: "Unknown error", "Tolgee")
                        return@invokeLater
                    }
                    val model = DefaultComboBoxModel<TolgeeProject>()
                    result.forEach { model.addElement(it) }
                    projectCombo.model = model
                    if (result.size == 1) projectCombo.selectedIndex = 0
                    refreshOkState()
                }
            }
        }
        task.queue()
    }

    private fun loadProjectMeta(projectId: Long) {
        val url = urlField.text.trim()
        val apiKey = String(apiKeyField.password)
        if (url.isBlank() || apiKey.isBlank()) return

        val myEpoch = ++metaEpoch
        val task = object : Task.Backgroundable(ideProject, "Loading Tolgee project metadata", false) {
            private var languagesResult: Result<List<String>> = Result.success(emptyList())
            private var namespacesResult: Result<List<String>> = Result.success(emptyList())

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val client = TolgeeApiClient(url, apiKey)
                // Run both fetches in parallel: languages on a pooled thread, namespaces here.
                val langFuture = ApplicationManager.getApplication().executeOnPooledThread(
                    Callable {
                        runCatching {
                            client.listProjectLanguages(projectId).map { it.tag }.filter { it.isNotBlank() }
                        }
                    },
                )
                namespacesResult = runCatching { client.listProjectNamespaces(projectId) }
                languagesResult = try {
                    langFuture.get()
                } catch (e: ExecutionException) {
                    Result.failure(e.cause ?: e)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    Result.failure(e)
                }
            }

            override fun onFinished() {
                if (myEpoch != metaEpoch) return
                ApplicationManager.getApplication().invokeLater {
                    val errors = listOfNotNull(namespacesResult.exceptionOrNull(), languagesResult.exceptionOrNull())
                    if (errors.isNotEmpty()) {
                        Messages.showWarningDialog(
                            ideProject,
                            "Failed to load project metadata:\n" +
                                errors.joinToString("\n") { it.message ?: it.javaClass.simpleName },
                            "Tolgee",
                        )
                    }
                    populateNamespaces(namespacesResult.getOrDefault(emptyList()), link.namespaces.toSet())
                    populateLanguages(languagesResult.getOrDefault(emptyList()), link.languages.toSet())
                }
            }
        }
        task.queue()
    }

    private fun populateNamespaces(available: List<String>, previouslySelected: Set<String>) {
        // Present the default (unnamed) namespace as a labelled row; store it as "" on save.
        val labels = available.map { if (it.isEmpty()) DEFAULT_NAMESPACE else it }
        if (renderCollapsedIfTrivial(namespacesList, labels)) return
        val previousLabels = previouslySelected.mapTo(mutableSetOf()) {
            if (it.isEmpty()) DEFAULT_NAMESPACE else it
        }
        populate(namespacesList, ALL_NAMESPACES, labels, previousLabels)
    }

    private fun populateLanguages(available: List<String>, previouslySelected: Set<String>) {
        if (renderCollapsedIfTrivial(languagesList, available)) return
        populate(languagesList, ALL_LANGUAGES, available, previouslySelected)
    }

    /**
     * When the project offers 0 or 1 real choice, showing "<All …>" alongside a single specific
     * row is redundant. Collapse to just that single row, disabled — the filter has no effect.
     * Returns true if it handled the render; false if the caller should fall through to [populate].
     */
    private fun renderCollapsedIfTrivial(list: CheckBoxList<String>, labels: List<String>): Boolean {
        if (labels.size > 1) return false
        suppressListListeners = true
        try {
            val items = linkedMapOf<String, Boolean>()
            labels.firstOrNull()?.let { items[it] = true }
            list.setStringItems(items)
            list.isEnabled = false
        } finally {
            suppressListListeners = false
        }
        return true
    }

    private fun collectNamespaces(): List<String> =
        collectSelection(namespacesList, ALL_NAMESPACES).map {
            if (it == DEFAULT_NAMESPACE) "" else it
        }

    private fun populate(
        list: CheckBoxList<String>,
        allMarker: String,
        available: List<String>,
        previouslySelected: Set<String>,
    ) {
        suppressListListeners = true
        try {
            val useAll = previouslySelected.isEmpty()
            val items = linkedMapOf<String, Boolean>()
            items[allMarker] = useAll
            for (item in available.sorted()) {
                items[item] = !useAll && item in previouslySelected
            }
            list.setStringItems(items)
            list.isEnabled = true
        } finally {
            suppressListListeners = false
        }
    }

    // Enforces two invariants:
    //  - Checking "all" unchecks specific rows; checking a specific row unchecks "all".
    //  - At least one row is always checked — unchecking the last one re-checks "all".
    private fun installExclusiveAllListener(list: CheckBoxList<String>, allMarker: String) {
        list.setCheckBoxListListener { index, value ->
            if (suppressListListeners) return@setCheckBoxListListener
            suppressListListeners = true
            try {
                val item = list.getItemAt(index)
                if (value) {
                    if (item == allMarker) {
                        for (i in 0 until list.itemsCount) {
                            val other = list.getItemAt(i) ?: continue
                            if (other != allMarker) list.setItemSelected(other, false)
                        }
                    } else {
                        list.setItemSelected(allMarker, false)
                    }
                } else if ((0 until list.itemsCount).none { list.isItemSelected(it) }) {
                    list.setItemSelected(allMarker, true)
                }
            } finally {
                suppressListListeners = false
            }
        }
    }

    private fun collectSelection(list: CheckBoxList<String>, allMarker: String): List<String> {
        if (list.isItemSelected(allMarker)) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until list.itemsCount) {
            val item = list.getItemAt(i) ?: continue
            if (item != allMarker && list.isItemSelected(i)) out += item
        }
        return out
    }

    private companion object {
        const val ALL_NAMESPACES = "<All namespaces>"
        const val ALL_LANGUAGES = "<All languages>"
        const val DEFAULT_NAMESPACE = "<Default namespace>"
    }
}
