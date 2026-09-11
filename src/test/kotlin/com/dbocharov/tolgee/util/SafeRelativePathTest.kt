package com.dbocharov.tolgee.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeRelativePathTest {

    @Test fun `accepts a simple relative path`() {
        assertEquals(".tolgee", TranslationFiles.safeRelativePath(".tolgee"))
    }

    @Test fun `accepts a nested relative path and normalises trailing separators`() {
        assertEquals("i18n/messages", TranslationFiles.safeRelativePath("i18n/messages/"))
    }

    @Test fun `accepts a Windows-style separator by normalising to forward slashes`() {
        assertEquals("i18n/messages", TranslationFiles.safeRelativePath("i18n\\messages"))
    }

    @Test fun `rejects absolute Unix path`() {
        assertNull(TranslationFiles.safeRelativePath("/etc/passwd"))
    }

    @Test fun `rejects absolute Windows drive path`() {
        assertNull(TranslationFiles.safeRelativePath("C:\\Windows\\System32"))
    }

    @Test fun `rejects UNC path`() {
        assertNull(TranslationFiles.safeRelativePath("\\\\host\\share\\evil"))
    }

    @Test fun `rejects path-traversal segment`() {
        assertNull(TranslationFiles.safeRelativePath("../../.."))
    }

    @Test fun `rejects path-traversal buried mid-path`() {
        assertNull(TranslationFiles.safeRelativePath("i18n/../../etc"))
    }

    @Test fun `rejects single-dot segment`() {
        assertNull(TranslationFiles.safeRelativePath("./i18n"))
    }

    @Test fun `rejects NUL byte`() {
        assertNull(TranslationFiles.safeRelativePath("i18n\u0000/x"))
    }

    @Test fun `rejects Windows reserved device name`() {
        assertNull(TranslationFiles.safeRelativePath("CON"))
    }

    @Test fun `rejects Windows reserved device name with extension`() {
        assertNull(TranslationFiles.safeRelativePath("NUL.json"))
    }

    @Test fun `rejects empty and whitespace input`() {
        assertNull(TranslationFiles.safeRelativePath(""))
        assertNull(TranslationFiles.safeRelativePath("   "))
        assertNull(TranslationFiles.safeRelativePath("/"))
    }
}
