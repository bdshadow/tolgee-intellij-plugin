package com.dbocharov.tolgee.completion

import com.intellij.openapi.project.Project
import com.dbocharov.tolgee.api.TolgeeKey
import com.dbocharov.tolgee.api.TolgeeTranslation
import com.dbocharov.tolgee.project.TolgeeKeyCache

/** Seeds the project cache with keys carrying a single English translation equal to the key name. */
internal fun seedCache(project: Project, vararg names: String) {
    val entries = names.mapIndexed { idx, name ->
        TolgeeKeyCache.CachedKey.of(
            TolgeeKey(
                keyId = (idx + 1).toLong(),
                keyName = name,
                translations = mapOf("en" to TolgeeTranslation(text = name)),
            ),
        )
    }
    TolgeeKeyCache.getInstance(project).setIndexForTests(TolgeeKeyCache.Index(entries))
}

internal fun clearCache(project: Project) {
    TolgeeKeyCache.getInstance(project).setIndexForTests(TolgeeKeyCache.Index())
}
