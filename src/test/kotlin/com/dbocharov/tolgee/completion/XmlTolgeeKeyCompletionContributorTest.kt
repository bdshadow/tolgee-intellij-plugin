package com.dbocharov.tolgee.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class XmlTolgeeKeyCompletionContributorTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            clearCache(project)
        } finally {
            super.tearDown()
        }
    }

    fun testCompletesKeysInsideStringName() {
        // Two keys sharing the prefix so completion doesn't auto-insert.
        seedCache(project, "greeting", "grocery", "farewell")
        myFixture.configureByText(
            "strings.xml",
            """
            <resources>
                <string name="gr<caret>">Hello</string>
            </resources>
            """.trimIndent(),
        )
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings ?: emptyList()
        assertTrue("expected `greeting` in lookups, got $lookups", "greeting" in lookups)
        assertTrue("expected `grocery` in lookups, got $lookups", "grocery" in lookups)
        assertFalse("`farewell` shouldn't match `gr` prefix, got $lookups", "farewell" in lookups)
    }

    fun testDoesNotCompleteInsideStringBody() {
        seedCache(project, "greeting", "farewell")
        myFixture.configureByText(
            "strings.xml",
            """
            <resources>
                <string name="hello">gr<caret></string>
            </resources>
            """.trimIndent(),
        )
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings ?: emptyList()
        assertFalse(
            "should not have offered translation keys inside <string> body, got $lookups",
            "greeting" in lookups,
        )
    }

    fun testDoesNotCompleteInOtherAttributes() {
        seedCache(project, "greeting", "farewell")
        myFixture.configureByText(
            "strings.xml",
            """
            <resources>
                <string name="hello" translatable="gr<caret>">Hello</string>
            </resources>
            """.trimIndent(),
        )
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings ?: emptyList()
        assertFalse(
            "should not fire on non-`name` attributes, got $lookups",
            "greeting" in lookups,
        )
    }

    fun testDoesNotCompleteWhenCacheEmpty() {
        myFixture.configureByText(
            "strings.xml",
            """
            <resources>
                <string name="gr<caret>">Hello</string>
            </resources>
            """.trimIndent(),
        )
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings ?: emptyList()
        assertFalse("greeting" in lookups)
    }

    fun testDoesNotCompleteOutsideResourcesRoot() {
        seedCache(project, "greeting", "farewell")
        myFixture.configureByText(
            "config.xml",
            """
            <config>
                <string name="gr<caret>">Hello</string>
            </config>
            """.trimIndent(),
        )
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings ?: emptyList()
        assertFalse(
            "should only fire under <resources>, got $lookups",
            "greeting" in lookups,
        )
    }
}
