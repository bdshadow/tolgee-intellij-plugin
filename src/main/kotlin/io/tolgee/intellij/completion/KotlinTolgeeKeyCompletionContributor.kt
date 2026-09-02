package io.tolgee.intellij.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.tolgee.intellij.project.TolgeeKeyCache
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class KotlinTolgeeKeyCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return
        val project = parameters.position.project
        val cache = TolgeeKeyCache.getInstance(project).current
        if (cache.entries.isEmpty()) return
        if (!isInsideTolgeeCallStringArg(parameters.position)) return

        for (entry in cache.displayEntries) {
            result.addElement(CompletionInsertion.lookupFor(entry, ParamInsertionStyle.KOTLIN_MAP_OF))
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun invokeAutoPopup(position: PsiElement, typeChar: Char): Boolean =
        isInsideTolgeeCallStringArg(position)

    private fun isInsideTolgeeCallStringArg(position: PsiElement): Boolean {
        val literal = PsiTreeUtil.getParentOfType(position, KtStringTemplateExpression::class.java) ?: return false
        val call = PsiTreeUtil.getParentOfType(literal, KtCallExpression::class.java) ?: return false
        val firstArg = call.valueArguments.firstOrNull()?.getArgumentExpression() ?: return false
        if (firstArg !== literal) return false
        val callee = call.calleeExpression?.text ?: return false
        return callee in TOLGEE_METHOD_NAMES
    }

    companion object {
        private val TOLGEE_METHOD_NAMES = setOf("translate", "t", "getTranslation", "getMessage")
    }
}
