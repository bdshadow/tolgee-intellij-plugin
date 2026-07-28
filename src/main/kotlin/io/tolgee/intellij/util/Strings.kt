package io.tolgee.intellij.util

/** Split a comma-separated field into trimmed non-empty tokens. */
fun String.splitCsv(): MutableList<String> =
    split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
