package io.tolgee.intellij.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import io.tolgee.intellij.settings.TolgeeAppSettings

/**
 * Shared Push/Pull precondition. Shows a warning and returns null when
 * misconfigured; otherwise returns the ready-to-use link.
 */
fun requireConfiguredLink(project: Project): TolgeeProjectLink? {
    val link = TolgeeProjectLink.getInstance(project)
    if (!link.isLinked || !TolgeeAppSettings.getInstance().isConfigured) {
        Messages.showWarningDialog(
            project,
            "Open the Tolgee tool window and click + to add a Tolgee project.",
            "Tolgee",
        )
        return null
    }
    return link
}

/** EDT-safe predicate for AnAction.update — both link and settings must be present. */
fun isTolgeeReady(project: Project?): Boolean {
    if (project == null) return false
    return TolgeeProjectLink.getInstance(project).isLinked && TolgeeAppSettings.getInstance().isConfigured
}
