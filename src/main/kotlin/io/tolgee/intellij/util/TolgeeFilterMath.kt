package io.tolgee.intellij.util

/**
 * Pure helpers for reasoning about the "empty list means all" filter shape shared by
 * TolgeeProjectLink.namespaces and TolgeeProjectLink.languages.
 */
object TolgeeFilterMath {

    /**
     * True when the new selection covers something the old one didn't — i.e. Push/Pull
     * would now operate on a superset. Empty list means "all", so
     *   [] -> [a, b]  is NOT widening (still a subset of "all"),
     *   [a] -> []     IS widening (was one, becomes all),
     *   [a] -> [a, b] IS widening.
     */
    fun filterWasWidened(old: List<String>, new: List<String>): Boolean = when {
        old.isEmpty() -> false
        new.isEmpty() -> true
        else -> !old.toSet().containsAll(new)
    }

    /**
     * Returns files whose language or namespace falls outside the new filter — the
     * caller can then offer to delete them from disk. `newLanguages` and
     * `newNamespaces` follow the "empty list means all" shape so an empty list on
     * that axis matches every file.
     */
    fun filesNoLongerCovered(
        files: List<TranslationFiles.LanguageFile>,
        newLanguages: List<String>,
        newNamespaces: List<String>,
    ): List<TranslationFiles.LanguageFile> {
        val langSet = newLanguages.toSet()
        val nsSet = newNamespaces.toSet()
        return files.filter { lf ->
            val langOut = langSet.isNotEmpty() && lf.language !in langSet
            val nsOut = nsSet.isNotEmpty() && (lf.namespace ?: "") !in nsSet
            langOut || nsOut
        }
    }

    fun labelFor(lf: TranslationFiles.LanguageFile): String =
        if (lf.namespace == null) "${lf.language}.json" else "${lf.namespace}/${lf.language}.json"
}
