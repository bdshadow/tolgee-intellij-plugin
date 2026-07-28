package io.tolgee.intellij.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.tolgee.intellij.api.TolgeeApiClient
import io.tolgee.intellij.api.TolgeeProject
import io.tolgee.intellij.project.TolgeeKeyCache
import io.tolgee.intellij.project.TolgeeProjectLink
import io.tolgee.intellij.pull.PullAction
import io.tolgee.intellij.settings.TolgeeAppSettings
import io.tolgee.intellij.util.TranslationFiles
import io.tolgee.intellij.util.splitCsv
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
    private val namespacesField = JBTextField(link.namespaces.joinToString(","))
    private val pathField = JBTextField(link.translationsPath.ifBlank { ".tolgee" })
    private val languagesField = JBTextField(link.languages.joinToString(","))
    private val autoPullCheckbox = JBCheckBox("Pull translations after creating the connection", true)
    private val isCreation = !link.isLinked

    init {
        title = if (link.isLinked) "Edit Tolgee Project" else "Add Tolgee Project"
        setOKButtonText(if (link.isLinked) "Save" else "Add")
        refreshProjectsButton.addActionListener { refreshProjects() }
        init()
        if (link.isLinked) {
            // Show current selection as a stub so OK is enabled without reloading.
            val stub = TolgeeProject(id = link.tolgeeProjectId, name = link.tolgeeProjectName)
            val model = DefaultComboBoxModel<TolgeeProject>()
            model.addElement(stub)
            projectCombo.model = model
            projectCombo.selectedIndex = 0
        }
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Instance URL:") { cell(urlField).resizableColumn().align(AlignX.FILL) }
        row("API key:") { cell(apiKeyField).resizableColumn().align(AlignX.FILL) }
        row("Tolgee project:") {
            cell(projectCombo).resizableColumn().align(AlignX.FILL)
            cell(refreshProjectsButton)
        }
        row("Namespaces:") {
            cell(namespacesField).resizableColumn().align(AlignX.FILL)
                .comment("Comma-separated. Leave blank to include all namespaces.")
        }
        row("Translations path:") { cell(pathField).resizableColumn().align(AlignX.FILL) }
        row("Languages:") {
            cell(languagesField).resizableColumn().align(AlignX.FILL)
                .comment("Comma-separated. Leave blank to include all project languages.")
        }
        if (isCreation) {
            row { cell(autoPullCheckbox) }
        }
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
        link.namespaces = namespacesField.text.splitCsv()
        link.translationsPath = pathField.text.trim().ifEmpty { ".tolgee" }
        link.languages = languagesField.text.splitCsv()

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
                }
            }
        }
        task.queue()
    }
}
