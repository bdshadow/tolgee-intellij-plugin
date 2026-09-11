package com.dbocharov.tolgee.completion

import com.dbocharov.tolgee.api.TolgeeKey

/**
 * ICU MessageFormat parameter extraction.
 *
 * Walks all translation strings of the given key (or just the base/source) and
 * collects every distinct top-level placeholder. We support:
 *
 *   - Simple placeholders: `{name}`
 *   - Typed placeholders: `{count, number}`, `{when, date, short}`
 *   - Plural / select / selectordinal: `{count, plural, one {…} other {…}}`
 *
 * Nested placeholders inside sub-messages are also harvested. We balance braces
 * so we don't get tripped up by the structure inside plural/select.
 */
object IcuParams {

    /** Returns sorted, unique parameter names referenced by any translation of [key]. */
    fun extractParamNames(key: TolgeeKey): List<String> {
        val names = sortedSetOf<String>()
        for (t in key.translations.values) {
            val text = t.text ?: continue
            collect(text, names)
        }
        // Plural keys always have a `count`-like parameter; if no translations yet,
        // synthesize one so completion is still useful.
        if (names.isEmpty() && key.keyIsPlural) names += "count"
        return names.toList()
    }

    private fun collect(message: String, out: MutableSet<String>) {
        var i = 0
        val len = message.length
        while (i < len) {
            val c = message[i]
            if (c == '\'' && startsIcuQuote(message, i)) {
                i = skipQuoted(message, i)
                continue
            }
            if (c == '{') {
                val end = matchClose(message, i)
                if (end < 0) return
                parsePlaceholder(message.substring(i + 1, end), out)
                i = end + 1
            } else {
                i++
            }
        }
    }

    private fun parsePlaceholder(inner: String, out: MutableSet<String>) {
        val name = takeNameUntilComma(inner)
        if (name.isNotBlank() && isValidName(name)) out += name

        var depth = 0
        var i = 0
        var subStart = -1
        while (i < inner.length) {
            val c = inner[i]
            if (c == '\'' && startsIcuQuote(inner, i)) {
                i = skipQuoted(inner, i); continue
            }
            when (c) {
                '{' -> {
                    if (depth == 0) subStart = i + 1
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && subStart >= 0) {
                        collect(inner.substring(subStart, i), out)
                        subStart = -1
                    }
                }
            }
            i++
        }
    }

    private fun takeNameUntilComma(s: String): String {
        var i = 0
        var depth = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\'' && startsIcuQuote(s, i)) { i = skipQuoted(s, i); continue }
            when (c) {
                '{' -> depth++
                '}' -> depth--
                ',' -> if (depth == 0) return s.substring(0, i).trim()
            }
            i++
        }
        return s.trim()
    }

    private fun matchClose(s: String, openIdx: Int): Int {
        var depth = 0
        var i = openIdx
        while (i < s.length) {
            val c = s[i]
            if (c == '\'' && startsIcuQuote(s, i)) { i = skipQuoted(s, i); continue }
            when (c) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return -1
    }

    /**
     * ICU MessageFormat rule: a single `'` starts a quoted section only if it's
     * immediately followed by an ICU syntax character (`{`, `}`, `|`, `#`) or by
     * another `'` (which encodes a literal apostrophe). Everywhere else a lone
     * `'` is just a literal apostrophe — vital for English text like "Don't
     * forget {name}", where the apostrophe must not swallow the placeholder.
     */
    private fun startsIcuQuote(s: String, at: Int): Boolean {
        val next = s.getOrNull(at + 1) ?: return false
        return next == '\'' || next == '{' || next == '}' || next == '|' || next == '#'
    }

    private fun skipQuoted(s: String, startQuote: Int): Int {
        if (startQuote + 1 < s.length && s[startQuote + 1] == '\'') return startQuote + 2
        var i = startQuote + 1
        while (i < s.length) {
            if (s[i] == '\'') return i + 1
            i++
        }
        return s.length
    }

    private fun isValidName(name: String): Boolean {
        if (name.isEmpty()) return false
        if (!name[0].isLetter() && name[0] != '_') return false
        return name.all { it.isLetterOrDigit() || it == '_' }
    }

}
