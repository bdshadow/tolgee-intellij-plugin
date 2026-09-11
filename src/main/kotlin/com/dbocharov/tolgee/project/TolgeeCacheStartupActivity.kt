package com.dbocharov.tolgee.project

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Rebuilds the Tolgee key cache from local translation files right after the
 * project opens, so JS/Kotlin/Java/XML completion works without the user having
 * to invoke "Refresh Tolgee Keys" first.
 */
class TolgeeCacheStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!isTolgeeReady(project)) return
        thisLogger().debug("Tolgee: rebuilding key cache on project open")
        TolgeeKeyCache.getInstance(project).refreshAsync()
    }
}
