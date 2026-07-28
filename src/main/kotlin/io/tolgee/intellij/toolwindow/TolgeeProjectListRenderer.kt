package io.tolgee.intellij.toolwindow

import com.intellij.ui.SimpleListCellRenderer
import io.tolgee.intellij.api.TolgeeProject
import javax.swing.JList

class TolgeeProjectListRenderer : SimpleListCellRenderer<TolgeeProject>() {
    override fun customize(
        list: JList<out TolgeeProject>,
        value: TolgeeProject?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
        text = value?.let { "${it.name} (#${it.id})" } ?: ""
    }
}
