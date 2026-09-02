package io.tolgee.intellij.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import io.tolgee.intellij.project.TolgeeKeyCache

/**
 * Completion of Tolgee keys inside Android `<string name="…">` attributes.
 * Matches the file shape (a `<string>` under a `<resources>` root) rather than
 * a specific filename, so it works for both `strings.xml` and split resource
 * files that follow the same convention.
 */
class XmlTolgeeKeyCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return
        val project = parameters.position.project
        val cache = TolgeeKeyCache.getInstance(project).current
        if (cache.entries.isEmpty()) return
        if (!isInsideStringNameAttribute(parameters.position)) return

        for (entry in cache.displayEntries) {
            result.addElement(CompletionInsertion.lookupFor(entry, ParamInsertionStyle.NONE))
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun invokeAutoPopup(position: PsiElement, typeChar: Char): Boolean =
        isInsideStringNameAttribute(position)

    private fun isInsideStringNameAttribute(position: PsiElement): Boolean {
        val value = PsiTreeUtil.getParentOfType(position, XmlAttributeValue::class.java) ?: return false
        val attr = value.parent as? XmlAttribute ?: return false
        if (attr.name != "name") return false
        val tag = attr.parent as? XmlTag ?: return false
        if (tag.name != "string") return false
        val root = PsiTreeUtil.getParentOfType(tag, XmlTag::class.java) ?: return false
        return root.name == "resources"
    }
}
