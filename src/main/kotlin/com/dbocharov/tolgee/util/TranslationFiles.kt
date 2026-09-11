package com.dbocharov.tolgee.util

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.nio.charset.StandardCharsets

/**
 * Read/write flat-JSON translation files at `<project>/<translationsPath>/[<ns>/]<lang>.json`.
 * Nested JSON is not parsed — flat map only.
 */
object TranslationFiles {

    private val jsonPretty = Json { prettyPrint = true }
    private val jsonStrict = Json { isLenient = true; ignoreUnknownKeys = true }
    private val log = thisLogger()

    fun resolveDir(project: Project, translationsPath: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        val rel = safeRelativePath(translationsPath) ?: return null
        return LocalFileSystem.getInstance().refreshAndFindFileByPath("$basePath/$rel")
    }

    fun ensureDir(project: Project, translationsPath: String): VirtualFile {
        val basePath = project.basePath ?: error("No project base path")
        val rel = safeRelativePath(translationsPath)
            ?: error("Refusing unsafe translations path: '$translationsPath'")
        val abs = "$basePath/$rel"
        val existing = LocalFileSystem.getInstance().refreshAndFindFileByPath(abs)
        if (existing != null) return existing
        return WriteAction.computeAndWait<VirtualFile, RuntimeException> {
            VfsUtil.createDirectories(abs) ?: error("Failed to create $abs")
        }
    }

    /**
     * Validates a user-supplied or server-supplied path fragment before it's spliced
     * onto the project root. Returns a normalised `foo/bar` form when the fragment is
     * safe, or `null` when it's not — the caller should treat null as a hard refusal.
     *
     * Rejects: absolute paths, `..` / `.` segments (path traversal out of the
     * project), NUL bytes (path smuggling), and empty inputs. Accepts both `/` and
     * `\\` as segment separators so a value entered on Windows still normalises.
     *
     * We use this both for the persisted `translationsPath` (which lives in
     * `.idea/tolgee.xml` and can be committed to VCS) and for individual namespace
     * names coming from the Tolgee server, since both are used verbatim as
     * filesystem path segments.
     */
    fun safeRelativePath(input: String): String? {
        val trimmed = input.trim().trim('/', '\\')
        if (trimmed.isEmpty() || trimmed.contains('\u0000')) return null
        val leading = input.trimStart()
        if (leading.startsWith('/') || leading.startsWith('\\')) return null
        if (Regex("^[A-Za-z]:[/\\\\]").containsMatchIn(leading)) return null
        val segments = trimmed.split('/', '\\').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        if (segments.any { it == ".." || it == "." }) return null
        // Windows reserved device names — legal on Unix but writing them on Windows
        // fails in ways that are hard to attribute. Reject up front on every OS.
        val reserved = setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
        )
        if (segments.any { it.substringBefore('.').uppercase() in reserved }) return null
        return segments.joinToString("/")
    }

    fun readFlatJson(file: VirtualFile): Map<String, JsonElement> {
        val bytes = file.contentsToByteArray()
        if (bytes.isEmpty()) return emptyMap()
        val text = String(bytes, StandardCharsets.UTF_8)
        val obj = jsonStrict.parseToJsonElement(text) as? JsonObject ?: return emptyMap()
        // Tolgee's default export shape is nested JSON ({"emails": {"welcome": "Hi"}}) when the
        // user does not disable structure delimiters. We only understand flat maps — surface the
        // situation once per file so users know why completion appears empty.
        if (obj.values.any { it is JsonObject }) {
            log.warn(
                "Tolgee: '${file.path}' looks nested (values that are JSON objects). " +
                    "Only flat maps are read — re-run Pull, or export with structureDelimiter=''.",
            )
        }
        return obj.toMap()
    }

    fun writeFlatJson(dir: VirtualFile, languageTag: String, flat: Map<String, JsonElement>) {
        val obj = buildJsonObject { flat.toSortedMap().forEach { (k, v) -> put(k, v) } }
        val text = jsonPretty.encodeToString(JsonObject.serializer(), obj)
        WriteAction.runAndWait<RuntimeException> {
            val target = dir.findChild("$languageTag.json") ?: dir.createChildData(this, "$languageTag.json")
            target.setBinaryContent(text.toByteArray(StandardCharsets.UTF_8))
        }
    }

    /** `namespace = null` for files at the translations root; otherwise the immediate subdir name. */
    data class LanguageFile(val namespace: String?, val language: String, val file: VirtualFile)

    /** Walks one level deep. Deeper nesting is ignored. */
    fun listAllLanguageFiles(dir: VirtualFile): List<LanguageFile> {
        val out = mutableListOf<LanguageFile>()
        for (child in dir.children.orEmpty()) {
            if (child.isDirectory) {
                for (grandchild in child.children.orEmpty()) {
                    if (!grandchild.isDirectory && grandchild.name.endsWith(".json")) {
                        out += LanguageFile(child.name, grandchild.name.removeSuffix(".json"), grandchild)
                    }
                }
            } else if (child.name.endsWith(".json")) {
                out += LanguageFile(null, child.name.removeSuffix(".json"), child)
            }
        }
        return out
    }
}
