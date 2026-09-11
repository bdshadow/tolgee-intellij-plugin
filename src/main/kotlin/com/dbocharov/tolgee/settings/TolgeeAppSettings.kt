package com.dbocharov.tolgee.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "TolgeeAppSettings",
    storages = [Storage("tolgee.xml")],
)
@Service(Service.Level.APP)
class TolgeeAppSettings : PersistentStateComponent<TolgeeAppSettings.State> {

    data class State(
        var instanceUrl: String = "https://app.tolgee.io",
        /**
         * Mirrors PasswordSafe presence so [isConfigured] can answer without touching
         * the credential store — PasswordSafe.get is a documented slow op and would
         * otherwise be called from every AnAction.update on the EDT.
         */
        var hasApiKey: Boolean = false,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    var instanceUrl: String
        get() = state.instanceUrl
        set(value) {
            state.instanceUrl = value
        }

    /**
     * API key is stored in the secure password safe, not in XML. Both accessors touch
     * PasswordSafe and may block — call from a background thread only.
     */
    var apiKey: String
        get() = PasswordSafe.instance.get(credentialAttributes())?.getPasswordAsString().orEmpty()
        set(value) {
            val attrs = credentialAttributes()
            if (value.isEmpty()) {
                PasswordSafe.instance.set(attrs, null)
                state.hasApiKey = false
            } else {
                PasswordSafe.instance.set(attrs, Credentials("tolgee", value))
                state.hasApiKey = true
            }
        }

    private fun credentialAttributes(): CredentialAttributes =
        CredentialAttributes("Tolgee — API key")

    /** EDT-safe — reads only the in-memory persisted flag, never PasswordSafe. */
    val isConfigured: Boolean
        get() = instanceUrl.isNotBlank() && state.hasApiKey

    companion object {
        fun getInstance(): TolgeeAppSettings =
            ApplicationManager.getApplication().getService(TolgeeAppSettings::class.java)
    }
}
