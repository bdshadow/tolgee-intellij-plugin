package io.tolgee.intellij.util

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class TranslationFilesTest : BasePlatformTestCase() {

    fun testWriteAndReadFlatJsonRoundTrip() {
        val dir = tempDir()
        TranslationFiles.writeFlatJson(
            dir,
            "en",
            mapOf(
                "b.key" to JsonPrimitive("beta"),
                "a.key" to JsonPrimitive("alpha"),
            ),
        )
        val file = dir.findChild("en.json") ?: error("en.json not created")
        val read = TranslationFiles.readFlatJson(file)
        assertEquals("alpha", (read["a.key"] as JsonPrimitive).content)
        assertEquals("beta", (read["b.key"] as JsonPrimitive).content)
    }

    fun testWriteFlatJsonSortsKeysDeterministically() {
        val dir = tempDir()
        TranslationFiles.writeFlatJson(
            dir,
            "en",
            mapOf("zeta" to JsonPrimitive("z"), "alpha" to JsonPrimitive("a")),
        )
        val text = String(dir.findChild("en.json")!!.contentsToByteArray())
        assertTrue(
            "keys should be sorted (alpha before zeta) — got:\n$text",
            text.indexOf("alpha") < text.indexOf("zeta"),
        )
    }

    fun testReadFlatJsonReturnsEmptyForBlankFile() {
        val dir = tempDir()
        val file = createFile(dir, "en.json", "")
        assertEquals(emptyMap<String, Any>(), TranslationFiles.readFlatJson(file))
    }

    fun testReadFlatJsonReturnsEmptyForNonObjectRoot() {
        val dir = tempDir()
        val file = createFile(dir, "en.json", "[1, 2, 3]")
        assertEquals(emptyMap<String, Any>(), TranslationFiles.readFlatJson(file))
    }

    fun testListAllLanguageFilesWalksRootAndOneLevelOfNamespaces() {
        val dir = tempDir()
        createFile(dir, "en.json", """{"root":"1"}""")
        createFile(dir, "de.json", """{"root":"1"}""")
        val emailsDir = WriteAction.computeAndWait<VirtualFile, RuntimeException> {
            dir.createChildDirectory(this, "emails")
        }
        createFile(emailsDir, "en.json", """{"welcome":"Hi"}""")
        createFile(emailsDir, "fr.json", """{"welcome":"Salut"}""")

        // Files deeper than one level should be ignored.
        val deep = WriteAction.computeAndWait<VirtualFile, RuntimeException> {
            emailsDir.createChildDirectory(this, "nope")
        }
        createFile(deep, "en.json", "{}")
        // Non-JSON siblings should be ignored too.
        createFile(dir, "README.md", "not a translation file")

        val listed = TranslationFiles.listAllLanguageFiles(dir).map { it.namespace to it.language }.toSet()
        assertEquals(
            setOf(
                null to "en",
                null to "de",
                "emails" to "en",
                "emails" to "fr",
            ),
            listed,
        )
    }

    fun testWriteFlatJsonOverwritesExistingFile() {
        val dir = tempDir()
        TranslationFiles.writeFlatJson(dir, "en", mapOf("k" to JsonPrimitive("v1")))
        TranslationFiles.writeFlatJson(dir, "en", mapOf("k" to JsonPrimitive("v2")))
        val obj = Json.parseToJsonElement(
            String(dir.findChild("en.json")!!.contentsToByteArray()),
        ) as JsonObject
        assertEquals("v2", obj["k"]!!.jsonPrimitive.content)
    }

    private fun tempDir(): VirtualFile =
        myFixture.tempDirFixture.findOrCreateDir("t-${System.identityHashCode(this)}-${counter++}")

    private fun createFile(parent: VirtualFile, name: String, text: String): VirtualFile =
        WriteAction.computeAndWait<VirtualFile, RuntimeException> {
            val f = parent.findChild(name) ?: parent.createChildData(this, name)
            f.setBinaryContent(text.toByteArray())
            f
        }

    companion object {
        private var counter = 0
    }
}
