package io.tolgee.intellij.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TolgeeApiClientHumanErrorMessageTest {

    @Test fun `401 mentions API key`() {
        val msg = TolgeeApiClient.humanErrorMessage(401, "")
        assertTrue(msg, msg.contains("API key", ignoreCase = true))
    }

    @Test fun `403 mentions scope`() {
        val msg = TolgeeApiClient.humanErrorMessage(403, "")
        assertTrue(msg, msg.contains("scope", ignoreCase = true))
    }

    @Test fun `404 mentions not found`() {
        val msg = TolgeeApiClient.humanErrorMessage(404, "")
        assertTrue(msg, msg.contains("not found", ignoreCase = true))
    }

    @Test fun `429 mentions rate limit`() {
        val msg = TolgeeApiClient.humanErrorMessage(429, "")
        assertTrue(msg, msg.contains("Rate-limited", ignoreCase = true))
    }

    @Test fun `5xx mentions server error and points at the log`() {
        val msg = TolgeeApiClient.humanErrorMessage(502, "")
        assertTrue(msg, msg.contains("server error", ignoreCase = true))
        assertTrue(msg, msg.contains("idea.log"))
    }

    @Test fun `HTML body is suppressed - only the hint is returned`() {
        val msg = TolgeeApiClient.humanErrorMessage(401, "<!DOCTYPE html><html><body>Sign in</body></html>")
        assertFalse("HTML body must not appear in the dialog: $msg", msg.contains("<html"))
        assertFalse("HTML body must not appear in the dialog: $msg", msg.contains("<body"))
        assertTrue(msg.contains("API key", ignoreCase = true))
    }

    @Test fun `bare-tag HTML body is suppressed too`() {
        val msg = TolgeeApiClient.humanErrorMessage(500, "<html><body>500 Internal Server Error</body></html>")
        assertFalse(msg, msg.contains("<html"))
    }

    @Test fun `empty body returns only the hint`() {
        val msg = TolgeeApiClient.humanErrorMessage(401, "")
        assertEquals("Invalid or expired Tolgee API key.", msg)
    }

    @Test fun `plain-text body is appended after the hint on a new line`() {
        val msg = TolgeeApiClient.humanErrorMessage(400, "Bad request — missing 'name' field")
        val lines = msg.lines()
        assertEquals(2, lines.size)
        assertEquals("Tolgee API 400.", lines[0])
        assertTrue(lines[1], lines[1].contains("Bad request"))
    }

    @Test fun `plain-text body is truncated to 200 chars and newlines collapsed`() {
        val body = "line1\nline2\nline3" + "x".repeat(400)
        val msg = TolgeeApiClient.humanErrorMessage(400, body)
        val tail = msg.lines().last()
        assertFalse("newlines should be collapsed: $tail", "\n" in tail)
        assertTrue("tail should be at most 200 chars, was ${tail.length}", tail.length <= 200)
    }
}
