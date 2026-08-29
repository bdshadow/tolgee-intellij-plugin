package io.tolgee.intellij.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import io.tolgee.intellij.project.TolgeeKeyCache

class JavaTolgeeKeyCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return
        val project = parameters.position.project
        val cache = TolgeeKeyCache.getInstance(project).current
        if (cache.entries.isEmpty()) return
        if (!isInsideTolgeeCallStringArg(parameters.position)) return

        for (entry in cache.entries) {
            result.addElement(CompletionInsertion.lookupFor(entry, ParamInsertionStyle.JAVA_MAP_OF))
        }
    }

    private fun isInsideTolgeeCallStringArg(position: PsiElement): Boolean {
        val literal = PsiTreeUtil.getParentOfType(position, PsiLiteralExpression::class.java) ?: return false
        if (literal.value !is String) return false
        val call = PsiTreeUtil.getParentOfType(literal, PsiMethodCallExpression::class.java) ?: return false
        // Only treat the *first* argument as the key.
        val firstArg = call.argumentList.expressions.firstOrNull() ?: return false
        if (firstArg !== literal) return false
        val methodName = call.methodExpression.referenceName ?: return false
        // Common Tolgee Java method shapes
        return methodName in TOLGEE_METHOD_NAMES
    }

    companion object {
        private val TOLGEE_METHOD_NAMES = setOf("translate", "t", "getTranslation", "getMessage")
    }
}
