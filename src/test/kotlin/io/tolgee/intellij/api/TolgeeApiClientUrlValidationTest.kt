package io.tolgee.intellij.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TolgeeApiClientUrlValidationTest {

    @Test fun `passes an https URL through unchanged`() {
        assertEquals(
            "https://app.tolgee.io",
            TolgeeApiClient.validateAndNormaliseBaseUrl("https://app.tolgee.io"),
        )
    }

    @Test fun `trims whitespace and trailing slash`() {
        assertEquals(
            "https://app.tolgee.io",
            TolgeeApiClient.validateAndNormaliseBaseUrl("  https://app.tolgee.io/  "),
        )
    }

    @Test fun `prepends https for schemeless input`() {
        assertEquals(
            "https://app.tolgee.io",
            TolgeeApiClient.validateAndNormaliseBaseUrl("app.tolgee.io"),
        )
    }

    @Test fun `accepts http for localhost`() {
        assertEquals(
            "http://localhost:8080",
            TolgeeApiClient.validateAndNormaliseBaseUrl("http://localhost:8080"),
        )
    }

    @Test fun `accepts http for 127-0-0-1 loopback`() {
        assertEquals(
            "http://127.0.0.1:8080",
            TolgeeApiClient.validateAndNormaliseBaseUrl("http://127.0.0.1:8080"),
        )
    }

    @Test fun `rejects http for a mDNS local host - anyone on the LAN can claim it`() {
        assertMessageContains("cleartext http") {
            TolgeeApiClient.validateAndNormaliseBaseUrl("http://tolgee.local")
        }
    }

    @Test fun `rejects http for a public host`() {
        assertMessageContains("cleartext http") {
            TolgeeApiClient.validateAndNormaliseBaseUrl("http://app.tolgee.io")
        }
    }

    @Test fun `rejects unknown scheme`() {
        assertMessageContains("must use http or https") {
            TolgeeApiClient.validateAndNormaliseBaseUrl("ftp://tolgee.example")
        }
    }

    @Test fun `rejects empty input with a friendly message`() {
        assertMessageContains("required") {
            TolgeeApiClient.validateAndNormaliseBaseUrl("   ")
        }
    }

    @Test fun `rejects a totally malformed URL with a friendly message`() {
        assertMessageContains("Not a valid") {
            TolgeeApiClient.validateAndNormaliseBaseUrl("http://")
        }
    }

    private inline fun assertMessageContains(needle: String, block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException containing '$needle'")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "expected message to contain '$needle', got: ${e.message}",
                e.message.orEmpty().contains(needle, ignoreCase = true),
            )
        }
    }
}
