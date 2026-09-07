package io.tolgee.intellij.project

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Project-level binding to a Tolgee project. Stored in `.idea/tolgee.xml`.
 */
@State(
    name = "TolgeeProjectLink",
    storages = [Storage("tolgee.xml")],
)
@Service(Service.Level.PROJECT)
class TolgeeProjectLink(private val project: Project) : PersistentStateComponent<TolgeeProjectLink.State> {

    data class State(
        var tolgeeProjectId: Long = 0,
        var tolgeeProjectName: String = "",
        /** Namespaces to include. Empty = all namespaces in the Tolgee project. */
        var namespaces: MutableList<String> = mutableListOf(),
        /** Path relative to project root for translation files (default: .tolgee). */
        var translationsPath: String = ".tolgee",
        /** Languages to push/pull. Empty = use Tolgee project languages. */
        var languages: MutableList<String> = mutableListOf(),
        /**
         * Tag of the Tolgee project's base language (`en`, `de`, …). Used to pick the
         * sample translation shown in the completion popup. Empty = unknown; the
         * cache falls back to English then to any available translation.
         */
        var baseLanguage: String = "",
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    val isLinked: Boolean
        get() = state.tolgeeProjectId > 0

    var tolgeeProjectId: Long
        get() = state.tolgeeProjectId
        set(value) { state.tolgeeProjectId = value }

    var tolgeeProjectName: String
        get() = state.tolgeeProjectName
        set(value) { state.tolgeeProjectName = value }

    var namespaces: MutableList<String>
        get() = state.namespaces
        set(value) { state.namespaces = value }

    var translationsPath: String
        get() = state.translationsPath
        set(value) { state.translationsPath = value }

    var languages: MutableList<String>
        get() = state.languages
        set(value) { state.languages = value }

    var baseLanguage: String
        get() = state.baseLanguage
        set(value) { state.baseLanguage = value }

    /**
     * Clears every piece of link state — not just the project id — so an Edit
     * dialog opened after Unlink doesn't pre-populate namespace/language filters
     * or a base language from the previously-linked project (they wouldn't apply
     * to a different Tolgee project anyway).
     */
    fun unlink() {
        state.tolgeeProjectId = 0
        state.tolgeeProjectName = ""
        state.namespaces = mutableListOf()
        state.languages = mutableListOf()
        state.baseLanguage = ""
    }

    fun fireChanged() {
        project.messageBus.syncPublisher(TOPIC).linkChanged()
    }

    fun interface Listener {
        fun linkChanged()
    }

    companion object {
        @Topic.ProjectLevel
        val TOPIC: Topic<Listener> = Topic.create("Tolgee project link changed", Listener::class.java)

        fun getInstance(project: Project): TolgeeProjectLink =
            project.getService(TolgeeProjectLink::class.java)
    }
}
