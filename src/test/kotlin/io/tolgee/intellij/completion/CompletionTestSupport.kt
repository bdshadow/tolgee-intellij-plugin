package io.tolgee.intellij.completion

import com.intellij.openapi.project.Project
import io.tolgee.intellij.api.TolgeeKey
import io.tolgee.intellij.api.TolgeeTranslation
import io.tolgee.intellij.project.TolgeeKeyCache

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
