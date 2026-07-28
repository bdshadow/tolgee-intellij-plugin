package io.tolgee.intellij.project

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Project-level binding to a Tolgee project. Stored in `.idea/tolgee.xml`.
 */
@State(
    name = "TolgeeProjectLink",
    storages = [Storage("tolgee.xml")],
)
@Service(Service.Level.PROJECT)
class TolgeeProjectLink : PersistentStateComponent<TolgeeProjectLink.State> {

    data class State(
        var tolgeeProjectId: Long = 0,
        var tolgeeProjectName: String = "",
        /** Namespaces to include. Empty = all namespaces in the Tolgee project. */
        var namespaces: MutableList<String> = mutableListOf(),
        /** Path relative to project root for translation files (default: .tolgee). */
        var translationsPath: String = ".tolgee",
        /** Languages to push/pull. Empty = use Tolgee project languages. */
        var languages: MutableList<String> = mutableListOf(),
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

    fun unlink() {
        state.tolgeeProjectId = 0
        state.tolgeeProjectName = ""
    }

    private val changeListeners = mutableListOf<() -> Unit>()

    fun addChangeListener(listener: () -> Unit) {
        changeListeners.add(listener)
    }

    fun fireChanged() {
        changeListeners.forEach { it() }
    }

    companion object {
        fun getInstance(project: Project): TolgeeProjectLink =
            project.getService(TolgeeProjectLink::class.java)
    }
}
