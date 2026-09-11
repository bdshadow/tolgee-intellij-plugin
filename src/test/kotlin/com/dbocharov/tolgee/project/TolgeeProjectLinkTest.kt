package com.dbocharov.tolgee.project

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TolgeeProjectLinkTest : BasePlatformTestCase() {

    fun testUnlinkClearsIdNameAndFilterStateButKeepsTranslationsPath() {
        val link = TolgeeProjectLink.getInstance(project)
        link.tolgeeProjectId = 42
        link.tolgeeProjectName = "Demo"
        link.namespaces = mutableListOf("emails", "invoices")
        link.languages = mutableListOf("en", "de")
        link.baseLanguage = "en"
        link.translationsPath = ".tolgee-custom"

        link.unlink()

        assertEquals(0, link.tolgeeProjectId)
        assertEquals("", link.tolgeeProjectName)
        assertTrue("namespaces should be cleared, got ${link.namespaces}", link.namespaces.isEmpty())
        assertTrue("languages should be cleared, got ${link.languages}", link.languages.isEmpty())
        assertEquals("", link.baseLanguage)
        // translationsPath is user-chosen infra, not a per-project filter; keep it so
        // the next Add doesn't lose the '.tolgee-custom' folder the user picked.
        assertEquals(".tolgee-custom", link.translationsPath)
    }
}
