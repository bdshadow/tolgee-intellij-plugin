package com.dbocharov.tolgee.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TolgeeAppSettingsTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            TolgeeAppSettings.getInstance().apiKey = ""
        } finally {
            super.tearDown()
        }
    }

    fun testHasApiKeyMirrorsSetter() {
        val settings = TolgeeAppSettings.getInstance()
        settings.instanceUrl = "https://app.tolgee.io"
        settings.apiKey = ""
        assertFalse("empty key should not count as configured", settings.isConfigured)
        assertFalse(settings.state.hasApiKey)

        settings.apiKey = "tgpat_test"
        assertTrue("non-empty key should count as configured", settings.isConfigured)
        assertTrue(settings.state.hasApiKey)

        settings.apiKey = ""
        assertFalse(settings.isConfigured)
        assertFalse(settings.state.hasApiKey)
    }

    fun testIsConfiguredNeedsBothUrlAndKey() {
        val settings = TolgeeAppSettings.getInstance()
        settings.apiKey = "tgpat_test"
        settings.instanceUrl = ""
        assertFalse("blank URL disqualifies the connection", settings.isConfigured)
        settings.instanceUrl = "https://app.tolgee.io"
        assertTrue(settings.isConfigured)
    }
}
