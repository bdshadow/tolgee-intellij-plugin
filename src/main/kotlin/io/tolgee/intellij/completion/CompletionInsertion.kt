package io.tolgee.intellij.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import io.tolgee.intellij.api.TolgeeKey

/** Strategies for inserting parameters after the key string. */
enum class ParamInsertionStyle {
    /** `t('greeting', { name: <CARET> })` — JS/TS object literal. */
    JS_OBJECT_LITERAL,

    /** `t("greeting", mapOf("name" to <CARET>))` — Kotlin map. */
    KOTLIN_MAP_OF,

    /** `tolgee.translate("greeting", Map.of("name", <CARET>))` — Java Map.of. */
    JAVA_MAP_OF,

    /** No params — just the key. */
    NONE,
}

/** Builds a completion [LookupElement] and, if the key has ICU params, appends an argument map after the key string. */
object CompletionInsertion {

    fun lookupFor(
        key: TolgeeKey,
        style: ParamInsertionStyle,
    ): LookupElement {
        val params = IcuParams.extractParamNames(key)
        val sample = key.translations.values.firstOrNull()?.text?.take(60)?.replace('\n', ' ')

        val full = if (key.keyNamespace.isNullOrBlank()) key.keyName
        else "${key.keyNamespace}:${key.keyName}"

        var builder = LookupElementBuilder.create(key, key.keyName)
            .withPresentableText(key.keyName)
            .withTypeText(if (params.isEmpty()) "Tolgee" else "Tolgee · {${params.joinToString(", ")}}", true)
            .withTailText(sample?.let { " — $it" }, true)
            .withLookupString(full)

        if (params.isNotEmpty() && style != ParamInsertionStyle.NONE) {
            builder = builder.withInsertHandler(ParamInsertHandler(params, style))
        }

        return builder
    }

    private class ParamInsertHandler(
        private val params: List<String>,
        private val style: ParamInsertionStyle,
    ) : InsertHandler<LookupElement> {

        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            val editor = context.editor
            val document = editor.document
            context.commitDocument()

            val (rawTail, caretInTail) = renderTail(params, style) ?: return
            val insertOffset = findInsertOffset(context, context.tailOffset)
            document.insertString(insertOffset, rawTail)
            context.commitDocument()

            if (caretInTail >= 0) {
                editor.caretModel.moveToOffset(insertOffset + caretInTail)
            }
        }

        /** Insert after the closing quote of the key, before any `,` / `)`. */
        private fun findInsertOffset(context: InsertionContext, tailOffset: Int): Int {
            val text = context.document.charsSequence
            var i = tailOffset
            while (i < text.length && text[i].isWhitespace()) i++
            return if (i < text.length && (text[i] == ',' || text[i] == ')')) i else tailOffset
        }

        /** Returns `(text, caretOffsetWithinText)` or null if there's nothing to insert. */
        private fun renderTail(params: List<String>, style: ParamInsertionStyle): Pair<String, Int>? {
            if (params.isEmpty()) return null
            val sb = StringBuilder()
            var caretAt = -1
            when (style) {
                ParamInsertionStyle.JS_OBJECT_LITERAL -> {
                    sb.append(", { ")
                    params.forEachIndexed { idx, p ->
                        if (idx > 0) sb.append(", ")
                        sb.append(p).append(": ")
                        if (idx == 0) caretAt = sb.length
                        sb.append(p)
                    }
                    sb.append(" }")
                }
                ParamInsertionStyle.KOTLIN_MAP_OF -> {
                    sb.append(", mapOf(")
                    params.forEachIndexed { idx, p ->
                        if (idx > 0) sb.append(", ")
                        sb.append('"').append(p).append("\" to ")
                        if (idx == 0) caretAt = sb.length
                        sb.append(p)
                    }
                    sb.append(')')
                }
                ParamInsertionStyle.JAVA_MAP_OF -> {
                    sb.append(", java.util.Map.of(")
                    params.forEachIndexed { idx, p ->
                        if (idx > 0) sb.append(", ")
                        sb.append('"').append(p).append("\", ")
                        if (idx == 0) caretAt = sb.length
                        sb.append(p)
                    }
                    sb.append(')')
                }
                ParamInsertionStyle.NONE -> return null
            }
            return sb.toString() to caretAt
        }
    }
}
