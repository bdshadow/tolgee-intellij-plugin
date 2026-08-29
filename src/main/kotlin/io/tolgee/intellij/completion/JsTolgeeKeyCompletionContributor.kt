package io.tolgee.intellij.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiElement
import io.tolgee.intellij.project.TolgeeKeyCache

/**
 * Completion at recognized Tolgee call sites in JS/TS/JSX/TSX:
 *
 *   - `t('foo.bar')` / `tolgee.t('foo.bar')` / `tolgee.translate('foo.bar')`
 *   - `<T keyName="foo.bar" />` JSX/TSX attribute
 *
 * We walk PSI ancestors and match by element-type name (compared as
 * `String.uppercase()` to handle camelCase vs underscore variants of element
 * type names across JS PSI versions).
 */
class JsTolgeeKeyCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return
        val project = parameters.position.project
        val cache = TolgeeKeyCache.getInstance(project).current
        if (cache.entries.isEmpty()) return

        if (LOG_DIAGNOSTICS) logDiagnostics(parameters)

        if (!isInsideTolgeeContext(parameters.position)) return

        for (entry in cache.entries) {
            result.addElement(CompletionInsertion.lookupFor(entry, ParamInsertionStyle.JS_OBJECT_LITERAL))
        }
    }

    private fun logDiagnostics(parameters: CompletionParameters) {
        val pos = parameters.position
        val ancestors = generateSequence(pos as PsiElement?) { it.parent }
            .take(10)
            .map { e ->
                val type = e.node?.elementType?.toString().orEmpty()
                val text = e.text.take(40).replace('\n', ' ')
                "$type[$text]"
            }
            .toList()
        thisLogger().warn(
            "[Tolgee] completion fired: lang=${pos.language.id} " +
                "matched=${isInsideTolgeeContext(pos)}\n  " +
                ancestors.joinToString("\n  "),
        )
    }

    private fun isInsideTolgeeContext(position: PsiElement): Boolean {
        var p: PsiElement? = position
        repeat(15) {
            val cur = p ?: return false
            val rawType = cur.node?.elementType?.toString().orEmpty()
            val type = rawType.uppercase()

            // 1. Tolgee call expression (`t(...)`, `*.t(...)`, `*.translate(...)`)
            //    The first *direct* child is the method reference — PsiTreeUtil.firstChild
            //    recurses to the leftmost leaf, which loses the qualifier (`tolgee.t` → `tolgee`).
            if (type.contains("CALL") && type.contains("EXPRESSION")) {
                val ref = cur.firstChild
                if (ref != null && isTolgeeCall(ref.text)) return true
            }

            // 2. JSX/TSX attribute named `keyName` on a `<T>` tag.
            //    Match the attribute *element* (containing name + `=` + value).
            //    Exclude VALUE / NAME / LIST sub-elements.
            if (type.contains("ATTRIBUTE") &&
                !type.contains("VALUE") &&
                !type.contains("NAME") &&
                !type.contains("LIST")
            ) {
                val attrText = cur.text.trimStart()
                val matches = attrText.startsWith("keyName") &&
                    attrText.getOrNull("keyName".length).let {
                        it == null || it == '=' || it.isWhitespace()
                    }
                if (matches) {
                    val tag = enclosingJsxTag(cur)
                    if (tag != null && jsxTagName(tag) == "T") return true
                }
            }

            p = cur.parent
        }
        return false
    }

    private fun enclosingJsxTag(start: PsiElement): PsiElement? {
        var p: PsiElement? = start.parent
        repeat(8) {
            val cur = p ?: return null
            val type = cur.node?.elementType?.toString().orEmpty().uppercase()
            // Current IntelliJ JS PSI uses `JSX_XML_LITERAL_EXPRESSION` as the
            // wrapping element for `<T ...>` (no separate XML_TAG element).
            // Older PSI variants used `XML_TAG` / `JSX_TAG`.
            val looksLikeTag = type.contains("JSX_XML_LITERAL") ||
                type.contains("JSXML") ||
                (type.contains("TAG") && !type.contains("ARGUMENT") && !type.contains("COMMENT"))
            if (looksLikeTag) return cur
            p = cur.parent
        }
        return null
    }

    private fun jsxTagName(tag: PsiElement): String? =
        Regex("""<\s*([A-Za-z_][A-Za-z0-9_.]*)""").find(tag.text)?.groupValues?.get(1)

    private fun isTolgeeCall(refText: String?): Boolean {
        val cleaned = refText?.trim().orEmpty()
        if (cleaned.isEmpty()) return false
        return cleaned == "t" ||
            cleaned == "translate" ||
            cleaned.endsWith(".t") ||
            cleaned.endsWith(".translate") ||
            cleaned.matches(Regex(""".*\.t\b.*"""))
    }

    /**
     * Auto-pop the completion popup as the user types inside a Tolgee context.
     * Strings normally don't auto-trigger completion in JetBrains IDEs.
     */
    override fun invokeAutoPopup(position: PsiElement, typeChar: Char): Boolean {
        return isInsideTolgeeContext(position)
    }

    companion object {
        // Flip to true when diagnosing why completion doesn't fire — writes a
        // single WARN line per completion invocation to idea.log with the PSI
        // ancestor chain at the cursor.
        private const val LOG_DIAGNOSTICS = false
    }
}
