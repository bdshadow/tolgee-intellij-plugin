package io.tolgee.intellij.util

import com.intellij.openapi.application.WriteAction
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

    private val jsonPretty = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }
    private val jsonStrict = Json { isLenient = true; ignoreUnknownKeys = true }

    fun resolveDir(project: Project, translationsPath: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        return LocalFileSystem.getInstance().refreshAndFindFileByPath("$basePath/${translationsPath.trim('/')}")
    }

    fun ensureDir(project: Project, translationsPath: String): VirtualFile {
        val basePath = project.basePath ?: error("No project base path")
        val abs = "$basePath/${translationsPath.trim('/')}"
        val existing = LocalFileSystem.getInstance().refreshAndFindFileByPath(abs)
        if (existing != null) return existing
        return WriteAction.computeAndWait<VirtualFile, RuntimeException> {
            VfsUtil.createDirectories(abs) ?: error("Failed to create $abs")
        }
    }

    fun readFlatJson(file: VirtualFile): Map<String, JsonElement> {
        val bytes = file.contentsToByteArray()
        if (bytes.isEmpty()) return emptyMap()
        val text = String(bytes, StandardCharsets.UTF_8)
        val obj = jsonStrict.parseToJsonElement(text) as? JsonObject ?: return emptyMap()
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
