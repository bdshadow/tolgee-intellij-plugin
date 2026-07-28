package io.tolgee.intellij.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import io.tolgee.intellij.settings.TolgeeAppSettings

/**
 * Shared Push/Pull precondition. Shows a warning and returns null when
 * misconfigured; otherwise returns the ready-to-use link.
 */
fun requireConfiguredLink(project: Project): TolgeeProjectLink? {
    if (!TolgeeAppSettings.getInstance().isConfigured) {
        Messages.showWarningDialog(project, "Configure Tolgee in settings first.", "Tolgee")
        return null
    }
    val link = TolgeeProjectLink.getInstance(project)
    if (!link.isLinked) {
        Messages.showWarningDialog(project, "Link a Tolgee project first.", "Tolgee")
        return null
    }
    return link
}
