package com.dbocharov.tolgee.project

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.dbocharov.tolgee.api.TolgeeKey
import com.dbocharov.tolgee.api.TolgeeTranslation
import com.dbocharov.tolgee.completion.IcuParams
import com.dbocharov.tolgee.util.TranslationFiles
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference

/**
 * In-memory key cache for code completion, built from local translation files.
 * Push and Pull both trigger a rebuild so the cache stays in sync with disk.
 */
@Service(Service.Level.PROJECT)
class TolgeeKeyCache(private val project: Project) {

    /**
     * Snapshot of everything a completion contributor needs to render a lookup — precomputed
     * once at cache-build time so the completion hot path is O(1) per key.
     */
    data class CachedKey(
        val key: TolgeeKey,
        val params: List<String>,
        val sample: String?,
        val fullName: String,
    ) {
        companion object {
            fun of(key: TolgeeKey, preferredLanguage: String? = null): CachedKey = CachedKey(
                key = key,
                params = IcuParams.extractParamNames(key),
                sample = pickSample(key.translations, preferredLanguage),
                fullName = if (key.keyNamespace.isNullOrBlank()) key.keyName
                else "${key.keyNamespace}:${key.keyName}",
            )

            /**
             * Preference order for the completion popup's sample text:
             *   1. The project's base language (as saved on the link)
             *   2. Any English variant, so freshly-added or filter-narrowed links still
             *      show something readable to most users
             *   3. Whatever `firstOrNull()` returns — better than nothing
             */
            private fun pickSample(
                translations: Map<String, TolgeeTranslation>,
                preferred: String?,
            ): String? {
                val text = preferred?.takeIf { it.isNotBlank() }?.let { translations[it]?.text }
                    ?: ENGLISH_FALLBACKS.firstNotNullOfOrNull { translations[it]?.text }
                    ?: translations.values.firstOrNull()?.text
                return text?.take(60)?.replace('\n', ' ')
            }

            private val ENGLISH_FALLBACKS = listOf("en", "en-US", "en-GB")
        }
    }

    data class Index(val entries: List<CachedKey> = emptyList()) {
        /**
         * The subset of [entries] that completion popups should show. Same-name keys are
         * deduplicated per this rule:
         *   - if the key exists in the default (unnamed) namespace, it wins and every
         *     namespaced sibling with the same [TolgeeKey.keyName] is dropped;
         *   - otherwise every namespaced entry is kept so the user can pick which
         *     namespace they meant — the namespace itself is surfaced in the popup
         *     by [CompletionInsertion.lookupFor].
         */
        val displayEntries: List<CachedKey> by lazy {
            entries.groupBy { it.key.keyName }.flatMap { (_, siblings) ->
                // First hit in the default namespace wins outright — if the cache ever
                // holds two "default" entries for the same name (shouldn't happen from
                // buildKeys, but keeps the invariant that user-visible duplicates cannot
                // sneak in), only one shows up.
                val defaultWinner = siblings.firstOrNull { it.key.keyNamespace.isNullOrBlank() }
                if (defaultWinner != null) listOf(defaultWinner)
                else siblings
            }
        }
    }

    private val ref = AtomicReference(Index())

    val current: Index get() = ref.get()

    @TestOnly
    fun setIndexForTests(index: Index) {
        ref.set(index)
    }

    fun refreshAsync(onDone: ((Result<Int>) -> Unit)? = null) {
        val link = TolgeeProjectLink.getInstance(project)
        if (!link.isLinked) {
            onDone?.invoke(Result.failure(IllegalStateException("Tolgee project not linked")))
            return
        }
        val translationsPath = link.translationsPath
        val baseLanguage = link.baseLanguage.ifBlank { null }

        val task = object : Task.Backgroundable(project, "Refreshing Tolgee keys from files", true) {
            private var loaded: List<CachedKey> = emptyList()
            private var err: Throwable? = null
            private var startedAt = 0L

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                startedAt = System.nanoTime()
                try {
                    val dir = runReadAction { TranslationFiles.resolveDir(project, translationsPath) }
                    loaded = if (dir == null || !dir.isDirectory) emptyList()
                    else runReadAction { buildKeys(dir, baseLanguage) }
                } catch (e: Exception) {
                    err = e
                }
            }

            override fun onFinished() {
                val ms = if (startedAt != 0L) (System.nanoTime() - startedAt) / 1_000_000 else -1
                if (err != null) {
                    thisLogger().warn("Tolgee key refresh from files failed after ${ms}ms", err)
                    onDone?.invoke(Result.failure(err!!))
                    return
                }
                ref.set(Index(loaded))
                thisLogger().info("Tolgee key cache rebuilt: ${loaded.size} key(s) in ${ms}ms")
                onDone?.invoke(Result.success(loaded.size))
            }
        }
        task.queue()
    }

    /**
     * Merges every `<lang>.json` / `<ns>/<lang>.json` under [dir] into TolgeeKey
     * objects, wrapping each in a [CachedKey] with completion metadata already computed.
     * A key is flagged plural if any translation uses `{X, plural, …}`.
     */
    private fun buildKeys(dir: VirtualFile, preferredLanguage: String?): List<CachedKey> {
        val builders = linkedMapOf<Pair<String, String>, KeyBuilder>()
        val warnedNoisyNames = mutableSetOf<String>()
        var nextId = 1L
        for (lf in TranslationFiles.listAllLanguageFiles(dir)) {
            val flat = try {
                TranslationFiles.readFlatJson(lf.file)
            } catch (_: Exception) {
                continue
            }
            for ((rawKeyName, valueEl) in flat) {
                // Server-side data can end up with stray whitespace / newlines in the key
                // itself (e.g. "edit-button\n   "). That would otherwise appear as a second
                // row in the completion popup that looks identical to the clean one. Match
                // on the trimmed form so users pick the key that matches their source code.
                val keyName = rawKeyName.trim()
                if (keyName.isEmpty()) continue
                if (keyName != rawKeyName && warnedNoisyNames.add(rawKeyName)) {
                    thisLogger().warn(
                        "Tolgee key has surrounding whitespace in ${lf.file.path}: " +
                            "${rawKeyName.take(60).replace("\n", "\\n")} — trimmed for completion.",
                    )
                }
                val text = (valueEl as? JsonPrimitive)?.let { if (it.isString) it.content else null } ?: continue
                val b = builders.getOrPut(lf.namespace.orEmpty() to keyName) {
                    KeyBuilder(nextId++, keyName, lf.namespace)
                }
                b.translations[lf.language] = TolgeeTranslation(text = text)
                if (!b.isPlural && PLURAL_PATTERN.containsMatchIn(text)) b.isPlural = true
            }
        }
        return builders.values.map {
            CachedKey.of(
                TolgeeKey(
                    keyId = it.id,
                    keyName = it.keyName,
                    keyNamespace = it.keyNamespace,
                    keyIsPlural = it.isPlural,
                    translations = it.translations.toMap(),
                ),
                preferredLanguage = preferredLanguage,
            )
        }
    }

    private data class KeyBuilder(
        val id: Long,
        val keyName: String,
        val keyNamespace: String?,
        val translations: MutableMap<String, TolgeeTranslation> = linkedMapOf(),
        var isPlural: Boolean = false,
    )

    companion object {
        private val PLURAL_PATTERN =
            Regex("\\{\\s*[A-Za-z_][A-Za-z0-9_]*\\s*,\\s*(?:plural|selectordinal)\\s*,")

        fun getInstance(project: Project): TolgeeKeyCache =
            project.getService(TolgeeKeyCache::class.java)
    }
}
