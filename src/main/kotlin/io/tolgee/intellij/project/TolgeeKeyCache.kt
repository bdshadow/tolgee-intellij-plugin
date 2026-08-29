package io.tolgee.intellij.project

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.tolgee.intellij.api.TolgeeKey
import io.tolgee.intellij.api.TolgeeTranslation
import io.tolgee.intellij.completion.IcuParams
import io.tolgee.intellij.util.TranslationFiles
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
            fun of(key: TolgeeKey): CachedKey = CachedKey(
                key = key,
                params = IcuParams.extractParamNames(key),
                sample = key.translations.values.firstOrNull()?.text?.take(60)?.replace('\n', ' '),
                fullName = if (key.keyNamespace.isNullOrBlank()) key.keyName
                else "${key.keyNamespace}:${key.keyName}",
            )
        }
    }

    data class Index(val entries: List<CachedKey> = emptyList())

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

        val task = object : Task.Backgroundable(project, "Refreshing Tolgee keys from files", true) {
            private var loaded: List<CachedKey> = emptyList()
            private var err: Throwable? = null
            private var startedAt = 0L

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                startedAt = System.nanoTime()
                try {
                    val dir = ReadAction.compute<VirtualFile?, RuntimeException> {
                        TranslationFiles.resolveDir(project, translationsPath)
                    }
                    loaded = if (dir == null || !dir.isDirectory) emptyList()
                    else ReadAction.compute<List<CachedKey>, RuntimeException> { buildKeys(dir) }
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
    private fun buildKeys(dir: VirtualFile): List<CachedKey> {
        val builders = linkedMapOf<Pair<String, String>, KeyBuilder>()
        var nextId = 1L
        for (lf in TranslationFiles.listAllLanguageFiles(dir)) {
            val flat = try {
                TranslationFiles.readFlatJson(lf.file)
            } catch (_: Exception) {
                continue
            }
            for ((keyName, valueEl) in flat) {
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
