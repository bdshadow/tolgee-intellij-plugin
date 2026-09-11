package com.dbocharov.tolgee.pull

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.dbocharov.tolgee.api.TolgeeLanguage
import com.dbocharov.tolgee.project.TolgeeProjectLink

class PullActionRefreshSavedBaseLanguageTest : BasePlatformTestCase() {

    fun testEmptyLanguageListLeavesTheSavedBaseUntouched() {
        val link = TolgeeProjectLink.getInstance(project).also { it.baseLanguage = "en" }
        PullAction.refreshSavedBaseLanguage(link, emptyList())
        assertEquals("en", link.baseLanguage)
    }

    fun testListWithoutABaseLanguageLeavesTheSavedBaseUntouched() {
        val link = TolgeeProjectLink.getInstance(project).also { it.baseLanguage = "en" }
        PullAction.refreshSavedBaseLanguage(link, listOf(lang("de"), lang("fr")))
        assertEquals("en", link.baseLanguage)
    }

    fun testMatchingBaseIsANoop() {
        val link = TolgeeProjectLink.getInstance(project).also { it.baseLanguage = "en" }
        PullAction.refreshSavedBaseLanguage(link, listOf(lang("en", base = true), lang("de")))
        assertEquals("en", link.baseLanguage)
    }

    fun testDifferentBaseUpdatesTheSavedValue() {
        val link = TolgeeProjectLink.getInstance(project).also { it.baseLanguage = "en" }
        PullAction.refreshSavedBaseLanguage(link, listOf(lang("de", base = true), lang("fr")))
        assertEquals("de", link.baseLanguage)
    }

    fun testBlankBaseTagIsIgnored() {
        val link = TolgeeProjectLink.getInstance(project).also { it.baseLanguage = "en" }
        PullAction.refreshSavedBaseLanguage(link, listOf(lang("", base = true)))
        assertEquals("en", link.baseLanguage)
    }

    private fun lang(tag: String, base: Boolean = false): TolgeeLanguage =
        TolgeeLanguage(id = 1, tag = tag, base = base)
}
