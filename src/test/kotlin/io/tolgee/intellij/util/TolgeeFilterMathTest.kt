package io.tolgee.intellij.util

import com.intellij.testFramework.LightVirtualFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TolgeeFilterMathTest {

    @Test fun `filterWasWidened - empty stays empty is not widening`() {
        assertFalse(TolgeeFilterMath.filterWasWidened(emptyList(), emptyList()))
    }

    @Test fun `filterWasWidened - empty to non-empty is narrowing, not widening`() {
        // [] means "all", so narrowing to a subset is not widening.
        assertFalse(TolgeeFilterMath.filterWasWidened(emptyList(), listOf("en")))
    }

    @Test fun `filterWasWidened - non-empty to empty is widening (goes to all)`() {
        assertTrue(TolgeeFilterMath.filterWasWidened(listOf("en"), emptyList()))
    }

    @Test fun `filterWasWidened - subset unchanged is not widening`() {
        assertFalse(TolgeeFilterMath.filterWasWidened(listOf("en", "de"), listOf("en")))
    }

    @Test fun `filterWasWidened - adding a language IS widening`() {
        assertTrue(TolgeeFilterMath.filterWasWidened(listOf("en"), listOf("en", "de")))
    }

    @Test fun `filterWasWidened - swapping for a disjoint language IS widening`() {
        assertTrue(TolgeeFilterMath.filterWasWidened(listOf("en"), listOf("de")))
    }

    @Test fun `filesNoLongerCovered - empty filters leave every file in place`() {
        val files = listOf(file(null, "en"), file("emails", "de"))
        assertEquals(emptyList<TranslationFiles.LanguageFile>(),
            TolgeeFilterMath.filesNoLongerCovered(files, emptyList(), emptyList()))
    }

    @Test fun `filesNoLongerCovered - narrows by language`() {
        val en = file(null, "en")
        val de = file(null, "de")
        assertEquals(listOf(de),
            TolgeeFilterMath.filesNoLongerCovered(listOf(en, de), listOf("en"), emptyList()))
    }

    @Test fun `filesNoLongerCovered - narrows by namespace with default (unnamed) as empty string`() {
        val root = file(null, "en")
        val emails = file("emails", "en")
        assertEquals(listOf(root),
            TolgeeFilterMath.filesNoLongerCovered(listOf(root, emails), emptyList(), listOf("emails")))
    }

    @Test fun `filesNoLongerCovered - both axes combine with OR`() {
        val enRoot = file(null, "en")
        val deEmails = file("emails", "de")
        assertEquals(listOf(deEmails),
            TolgeeFilterMath.filesNoLongerCovered(listOf(enRoot, deEmails), listOf("en"), listOf("")))
    }

    @Test fun `labelFor - default namespace file`() {
        assertEquals("en.json", TolgeeFilterMath.labelFor(file(null, "en")))
    }

    @Test fun `labelFor - namespaced file`() {
        assertEquals("emails/en.json", TolgeeFilterMath.labelFor(file("emails", "en")))
    }

    private fun file(namespace: String?, language: String): TranslationFiles.LanguageFile =
        TranslationFiles.LanguageFile(namespace, language, LightVirtualFile("$language.json"))
}
