package io.tolgee.intellij.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.tolgee.intellij.api.TolgeeKey
import io.tolgee.intellij.api.TolgeeTranslation
import io.tolgee.intellij.project.TolgeeKeyCache

class XmlTolgeeKeyCompletionContributorTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            TolgeeKeyCache.getInstance(project).setIndexForTests(TolgeeKeyCache.Index())
        } finally {
            super.tearDown()
        }
    }

    fun testCompletesKeysInsideStringName() {
        // Two keys sharing the prefix so completion doesn't auto-insert.
        seedCache("greeting", "grocery", "farewell")
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
        seedCache("greeting", "farewell")
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
        seedCache("greeting", "farewell")
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
        // Cache stays empty (default).
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
        seedCache("greeting", "farewell")
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

    private fun seedCache(vararg names: String) {
        val keys = names.mapIndexed { idx, name ->
            TolgeeKey(
                keyId = (idx + 1).toLong(),
                keyName = name,
                translations = mapOf("en" to TolgeeTranslation(text = name)),
            )
        }
        TolgeeKeyCache.getInstance(project).setIndexForTests(TolgeeKeyCache.Index(keys))
    }
}
