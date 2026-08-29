package io.tolgee.intellij.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.tolgee.intellij.api.TolgeeKey
import io.tolgee.intellij.api.TolgeeTranslation
import io.tolgee.intellij.project.TolgeeKeyCache

class JsTolgeeKeyCompletionContributorTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            clearCache(project)
        } finally {
            super.tearDown()
        }
    }

    fun testCompletesInsideBareTCall() {
        seedCache(project, "greeting", "grocery", "farewell")
        myFixture.configureByText("m.ts", """const s = t('gr<caret>');""")
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings ?: emptyList()
        assertTrue("expected `greeting` in $lookups", "greeting" in lookups)
        assertTrue("expected `grocery` in $lookups", "grocery" in lookups)
        assertFalse("`farewell` shouldn't match `gr` prefix, got $lookups", "farewell" in lookups)
    }

    fun testCompletesInsideNamespacedTCall() {
        seedCache(project, "greeting", "grocery", "farewell")
        myFixture.configureByText("m.ts", """const s = tolgee.t('gr<caret>');""")
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings ?: emptyList()
        assertTrue("expected `greeting` in $lookups", "greeting" in lookups)
    }

    fun testCompletesInsideTranslateCall() {
        seedCache(project, "greeting", "grocery", "farewell")
        myFixture.configureByText("m.ts", """const s = tolgee.translate('gr<caret>');""")
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings ?: emptyList()
        assertTrue("expected `greeting` in $lookups", "greeting" in lookups)
    }

    fun testDoesNotCompleteInUnrelatedCall() {
        seedCache(project, "greeting", "grocery", "farewell")
        myFixture.configureByText("m.ts", """console.log('gr<caret>');""")
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings ?: emptyList()
        assertFalse("shouldn't offer Tolgee keys in unrelated calls, got $lookups", "greeting" in lookups)
    }

    fun testCompletesInsideJsxKeyNameAttribute() {
        seedCache(project, "greeting", "grocery", "farewell")
        myFixture.configureByText(
            "M.tsx",
            """
            const el = <T keyName="gr<caret>" />;
            """.trimIndent(),
        )
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings ?: emptyList()
        assertTrue("expected `greeting` in $lookups", "greeting" in lookups)
        assertTrue("expected `grocery` in $lookups", "grocery" in lookups)
    }

    fun testDoesNotCompleteInOtherJsxAttribute() {
        seedCache(project, "greeting", "grocery", "farewell")
        myFixture.configureByText(
            "M.tsx",
            """
            const el = <T className="gr<caret>" />;
            """.trimIndent(),
        )
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings ?: emptyList()
        assertFalse("should only fire on keyName attr, got $lookups", "greeting" in lookups)
    }

    fun testDoesNotCompleteOnKeyNameOfOtherTag() {
        seedCache(project, "greeting", "grocery", "farewell")
        myFixture.configureByText(
            "M.tsx",
            """
            const el = <Input keyName="gr<caret>" />;
            """.trimIndent(),
        )
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings ?: emptyList()
        assertFalse("should only fire on <T> tag, got $lookups", "greeting" in lookups)
    }

    fun testDoesNotCompleteWhenCacheEmpty() {
        // Cache stays at Index() from tearDown.
        myFixture.configureByText("m.ts", """const s = t('gr<caret>');""")
        myFixture.completeBasic()
        val lookups = myFixture.lookupElementStrings ?: emptyList()
        assertFalse("greeting" in lookups)
    }

    fun testInsertHandlerAppendsJsObjectLiteralForParams() {
        // Two keys sharing prefix, one with an ICU param and one without.
        val entries = listOf(
            TolgeeKeyCache.CachedKey.of(
                TolgeeKey(
                    keyId = 1,
                    keyName = "greeting_named",
                    translations = mapOf("en" to TolgeeTranslation(text = "Hello {name}!")),
                ),
            ),
            TolgeeKeyCache.CachedKey.of(
                TolgeeKey(
                    keyId = 2,
                    keyName = "greeting_plain",
                    translations = mapOf("en" to TolgeeTranslation(text = "Hello there.")),
                ),
            ),
        )
        TolgeeKeyCache.getInstance(project).setIndexForTests(TolgeeKeyCache.Index(entries))

        myFixture.configureByText("m.ts", """const s = t('greeting_n<caret>');""")
        myFixture.completeBasic()
        // Only one match remains → framework auto-inserts and fires the insert handler.
        val text = myFixture.editor.document.text
        assertTrue(
            "expected JS object-literal tail after key, got:\n$text",
            text.contains("t('greeting_named', { name: name })"),
        )
    }
}
