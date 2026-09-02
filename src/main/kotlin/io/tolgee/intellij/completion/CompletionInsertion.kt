package io.tolgee.intellij.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import io.tolgee.intellij.project.TolgeeKeyCache

/** Strategies for inserting parameters after the key string. */
enum class ParamInsertionStyle {
    /** `t('greeting', { name: <SEL>name</SEL> })` — JS/TS function-call. */
    JS_OBJECT_LITERAL,

    /** `<T keyName="greeting" params={{ name: <SEL>name</SEL> }} />` — Tolgee React `<T>` component prop. */
    JSX_ATTRIBUTE,

    /** `t("greeting", mapOf("name" to <SEL>name</SEL>))` — Kotlin map. */
    KOTLIN_MAP_OF,

    /** `tolgee.translate("greeting", Map.of("name", <SEL>name</SEL>))` — Java Map.of. */
    JAVA_MAP_OF,

    /** No params — just the key. */
    NONE,
}

/** Builds a completion [LookupElement] and, if the key has ICU params, appends a tail after the key string. */
object CompletionInsertion {

    fun lookupFor(
        cached: TolgeeKeyCache.CachedKey,
        style: ParamInsertionStyle,
    ): LookupElement {
        val key = cached.key
        val params = cached.params

        val namespace = key.keyNamespace?.takeIf { it.isNotBlank() }
        val paramsText = if (params.isEmpty()) "" else " · {${params.joinToString(", ")}}"
        val typeText = "Tolgee" + (namespace?.let { " · $it" }.orEmpty()) + paramsText

        var builder = LookupElementBuilder.create(key, key.keyName)
            .withPresentableText(key.keyName)
            .withTypeText(typeText, true)
            .withTailText(cached.sample?.let { " — $it" }, true)
            .withLookupString(cached.fullName)

        if (params.isNotEmpty() && style != ParamInsertionStyle.NONE) {
            builder = builder.withInsertHandler(ParamInsertHandler(params, style))
        }

        return builder
    }

    private data class TailInsertion(val text: String, val selectionStart: Int, val selectionEnd: Int)

    private class ParamInsertHandler(
        private val params: List<String>,
        private val style: ParamInsertionStyle,
    ) : InsertHandler<LookupElement> {

        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            val editor = context.editor
            val document = editor.document
            context.commitDocument()

            val tail = renderTail(params, style) ?: return
            val insertOffset = findInsertOffset(context, context.tailOffset)
            document.insertString(insertOffset, tail.text)
            context.commitDocument()

            if (style == ParamInsertionStyle.JSX_ATTRIBUTE) {
                selfCloseJsxTagIfNeeded(context, insertOffset + tail.text.length)
            }

            val selStart = insertOffset + tail.selectionStart
            val selEnd = insertOffset + tail.selectionEnd
            editor.caretModel.moveToOffset(selEnd)
            editor.selectionModel.setSelection(selStart, selEnd)
        }

        private fun selfCloseJsxTagIfNeeded(context: InsertionContext, afterTail: Int) {
            val document = context.document
            if (!tagNeedsSelfClose(document.charsSequence, afterTail)) return
            document.insertString(afterTail, " />")
            context.commitDocument()
        }

        private fun tagNeedsSelfClose(text: CharSequence, at: Int): Boolean {
            var i = at
            while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
            if (i >= text.length) return true
            val c = text[i]
            return c != '/' && c != '>'
        }

        /**
         * `tailOffset` sits inside the string literal (between the inserted key text and the
         * closing quote). Step past the quote so we land immediately in the enclosing call/tag,
         * and let each tail carry its own leading whitespace — skipping through existing spaces
         * causes doubles (`"foo"  params`) or lands on the wrong line for unclosed tags.
         */
        private fun findInsertOffset(context: InsertionContext, tailOffset: Int): Int {
            val text = context.document.charsSequence
            val i = tailOffset
            return if (i < text.length && (text[i] == '\'' || text[i] == '"' || text[i] == '`')) i + 1 else i
        }

        private fun renderTail(params: List<String>, style: ParamInsertionStyle): TailInsertion? {
            if (params.isEmpty()) return null
            val sb = StringBuilder()
            var selStart = -1
            var selEnd = -1
            fun markSelection(range: IntRange) {
                if (selStart < 0) {
                    selStart = range.first
                    selEnd = range.last
                }
            }
            when (style) {
                ParamInsertionStyle.JS_OBJECT_LITERAL -> {
                    sb.append(", { ")
                    params.forEachIndexed { idx, p ->
                        if (idx > 0) sb.append(", ")
                        sb.append(p).append(": ")
                        val start = sb.length
                        sb.append(p)
                        if (idx == 0) markSelection(start..sb.length)
                    }
                    sb.append(" }")
                }
                ParamInsertionStyle.JSX_ATTRIBUTE -> {
                    sb.append(" params={{ ")
                    params.forEachIndexed { idx, p ->
                        if (idx > 0) sb.append(", ")
                        sb.append(p).append(": ")
                        val start = sb.length
                        sb.append(p)
                        if (idx == 0) markSelection(start..sb.length)
                    }
                    sb.append(" }}")
                }
                ParamInsertionStyle.KOTLIN_MAP_OF -> {
                    sb.append(", mapOf(")
                    params.forEachIndexed { idx, p ->
                        if (idx > 0) sb.append(", ")
                        sb.append('"').append(p).append("\" to ")
                        val start = sb.length
                        sb.append(p)
                        if (idx == 0) markSelection(start..sb.length)
                    }
                    sb.append(')')
                }
                ParamInsertionStyle.JAVA_MAP_OF -> {
                    sb.append(", java.util.Map.of(")
                    params.forEachIndexed { idx, p ->
                        if (idx > 0) sb.append(", ")
                        sb.append('"').append(p).append("\", ")
                        val start = sb.length
                        sb.append(p)
                        if (idx == 0) markSelection(start..sb.length)
                    }
                    sb.append(')')
                }
                ParamInsertionStyle.NONE -> return null
            }
            return TailInsertion(sb.toString(), selStart, selEnd)
        }
    }
}
