package io.tolgee.intellij.project

import io.tolgee.intellij.api.TolgeeKey
import io.tolgee.intellij.api.TolgeeTranslation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TolgeeKeyCacheTest {

    @Test
    fun `sample prefers the requested base language when available`() {
        val cached = TolgeeKeyCache.CachedKey.of(
            keyWithTranslations("en" to "Hello", "cs" to "Ahoj", "de" to "Hallo"),
            preferredLanguage = "cs",
        )
        assertEquals("Ahoj", cached.sample)
    }

    @Test
    fun `sample falls back to English when the base language is missing`() {
        val cached = TolgeeKeyCache.CachedKey.of(
            keyWithTranslations("en" to "Hello", "de" to "Hallo"),
            preferredLanguage = "cs",
        )
        assertEquals("Hello", cached.sample)
    }

    @Test
    fun `sample falls back to an English variant when 'en' itself is absent`() {
        val cached = TolgeeKeyCache.CachedKey.of(
            keyWithTranslations("en-GB" to "Colour", "de" to "Farbe"),
            preferredLanguage = null,
        )
        assertEquals("Colour", cached.sample)
    }

    @Test
    fun `sample falls back to any translation when no preferred nor English is present`() {
        val cached = TolgeeKeyCache.CachedKey.of(
            keyWithTranslations("de" to "Hallo", "fr" to "Bonjour"),
            preferredLanguage = null,
        )
        assertEquals("Hallo", cached.sample)
    }

    @Test
    fun `blank preferred language is ignored`() {
        val cached = TolgeeKeyCache.CachedKey.of(
            keyWithTranslations("en" to "Hello", "de" to "Hallo"),
            preferredLanguage = "",
        )
        assertEquals("Hello", cached.sample)
    }

    @Test
    fun `sample is null when there are no translations at all`() {
        val cached = TolgeeKeyCache.CachedKey.of(
            keyWithTranslations(),
            preferredLanguage = "en",
        )
        assertNull(cached.sample)
    }

    @Test
    fun `displayEntries drops namespaced siblings when the default namespace has the same key`() {
        val index = TolgeeKeyCache.Index(
            listOf(
                cachedKey(id = 1, name = "edit-button", namespace = null),
                cachedKey(id = 2, name = "edit-button", namespace = "emails"),
                cachedKey(id = 3, name = "edit-button", namespace = "invoices"),
                cachedKey(id = 4, name = "other-key", namespace = "emails"),
            ),
        )
        val display = index.displayEntries
        assertEquals(2, display.size)
        assertEquals(listOf(1L, 4L), display.map { it.key.keyId })
    }

    @Test
    fun `displayEntries collapses accidental default-namespace duplicates to one entry`() {
        val index = TolgeeKeyCache.Index(
            listOf(
                cachedKey(id = 1, name = "edit-button", namespace = null),
                cachedKey(id = 2, name = "edit-button", namespace = null),
            ),
        )
        val display = index.displayEntries
        assertEquals(1, display.size)
        assertEquals(1L, display.single().key.keyId)
    }

    @Test
    fun `displayEntries keeps every namespaced sibling when no default is present`() {
        val index = TolgeeKeyCache.Index(
            listOf(
                cachedKey(id = 1, name = "edit-button", namespace = "emails"),
                cachedKey(id = 2, name = "edit-button", namespace = "invoices"),
            ),
        )
        val display = index.displayEntries
        assertEquals(2, display.size)
        assertEquals(setOf("emails", "invoices"), display.mapNotNull { it.key.keyNamespace }.toSet())
    }

    private fun cachedKey(id: Long, name: String, namespace: String?): TolgeeKeyCache.CachedKey =
        TolgeeKeyCache.CachedKey.of(
            TolgeeKey(
                keyId = id,
                keyName = name,
                keyNamespace = namespace,
                translations = mapOf("en" to TolgeeTranslation(text = "sample")),
            ),
        )

    private fun keyWithTranslations(vararg entries: Pair<String, String>): TolgeeKey =
        TolgeeKey(
            keyId = 1,
            keyName = "greeting",
            translations = linkedMapOf(*entries.map { (k, v) -> k to TolgeeTranslation(text = v) }.toTypedArray()),
        )
}
