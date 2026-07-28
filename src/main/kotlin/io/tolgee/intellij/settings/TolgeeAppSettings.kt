package io.tolgee.intellij.settings

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

    /** API key is stored in the secure password safe, not in XML. */
    var apiKey: String
        get() = PasswordSafe.instance.get(credentialAttributes())?.getPasswordAsString().orEmpty()
        set(value) {
            val attrs = credentialAttributes()
            if (value.isEmpty()) {
                PasswordSafe.instance.set(attrs, null)
            } else {
                PasswordSafe.instance.set(attrs, Credentials("tolgee", value))
            }
        }

    private fun credentialAttributes(): CredentialAttributes =
        CredentialAttributes("Tolgee — API key")

    val isConfigured: Boolean
        get() = instanceUrl.isNotBlank() && apiKey.isNotBlank()

    companion object {
        fun getInstance(): TolgeeAppSettings =
            ApplicationManager.getApplication().getService(TolgeeAppSettings::class.java)
    }
}
