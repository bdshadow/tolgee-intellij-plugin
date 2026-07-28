package io.tolgee.intellij.completion

import io.tolgee.intellij.api.TolgeeKey
import io.tolgee.intellij.api.TolgeeTranslation
import org.junit.Assert.assertEquals
import org.junit.Test

class IcuParamsTest {

    @Test
    fun `extracts a single simple placeholder`() {
        assertEquals(listOf("name"), extract("Hello {name}!"))
    }

    @Test
    fun `dedupes placeholders across translations`() {
        val key = TolgeeKey(
            keyId = 1,
            keyName = "greeting",
            translations = mapOf(
                "en" to TolgeeTranslation(text = "Hi {name}, welcome to {app}."),
                "de" to TolgeeTranslation(text = "Hallo {name}!"),
            ),
        )
        assertEquals(listOf("app", "name"), IcuParams.extractParamNames(key))
    }

    @Test
    fun `extracts typed placeholder parameter`() {
        assertEquals(listOf("count"), extract("You have {count, number} messages."))
    }

    @Test
    fun `extracts plural param and any inner placeholders`() {
        val text = "{count, plural, one {# item for {user}} other {# items for {user}}}"
        assertEquals(listOf("count", "user"), extract(text))
    }

    @Test
    fun `synthesises count when key is plural but empty`() {
        val key = TolgeeKey(keyId = 1, keyName = "n", keyIsPlural = true, translations = emptyMap())
        assertEquals(listOf("count"), IcuParams.extractParamNames(key))
    }

    @Test
    fun `ignores ICU-quoted braces`() {
        assertEquals(emptyList<String>(), extract("A '{literal}' brace."))
    }

    @Test
    fun `doubled apostrophe is a literal — braces after it still count`() {
        assertEquals(listOf("x"), extract("It''s {x}"))
    }

    @Test
    fun `rejects invalid placeholder names`() {
        assertEquals(emptyList<String>(), extract("{ } and { 1abc }"))
    }

    @Test
    fun `unclosed brace does not throw`() {
        assertEquals(emptyList<String>(), extract("Broken {oops"))
    }

    @Test
    fun `nested selects and plurals`() {
        val text = "{gender, select, male {He liked {count, plural, one {# post} other {# posts}}} " +
            "female {She liked {count, plural, one {# post} other {# posts}}} " +
            "other {They liked {count, plural, one {# post} other {# posts}}}}"
        assertEquals(listOf("count", "gender"), extract(text))
    }

    private fun extract(vararg texts: String): List<String> {
        val key = TolgeeKey(
            keyId = 1,
            keyName = "k",
            translations = texts.mapIndexed { i, t -> "lang$i" to TolgeeTranslation(text = t) }.toMap(),
        )
        return IcuParams.extractParamNames(key)
    }
}
