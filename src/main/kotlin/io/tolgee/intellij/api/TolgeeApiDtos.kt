package io.tolgee.intellij.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TolgeeProject(
    val id: Long,
    val name: String,
    val slug: String? = null,
    val baseLanguage: TolgeeLanguage? = null,
)

@Serializable
data class TolgeeLanguage(
    val id: Long,
    val tag: String,
    val name: String? = null,
    val originalName: String? = null,
    val flagEmoji: String? = null,
    val base: Boolean = false,
)

@Serializable
data class TolgeeKey(
    val keyId: Long,
    val keyName: String,
    val keyNamespace: String? = null,
    val keyIsPlural: Boolean = false,
    val translations: Map<String, TolgeeTranslation> = emptyMap(),
)

@Serializable
data class TolgeeTranslation(
    val id: Long? = null,
    val text: String? = null,
    val state: String? = null,
)

@Serializable
data class PagedKeys(
    @SerialName("_embedded") val embedded: KeysEmbedded? = null,
    val page: PageInfo? = null,
)

@Serializable
data class KeysEmbedded(
    val keys: List<TolgeeKey> = emptyList(),
)

@Serializable
data class PageInfo(
    val size: Int = 0,
    val number: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

@Serializable
data class PagedProjects(
    @SerialName("_embedded") val embedded: ProjectsEmbedded? = null,
    val page: PageInfo? = null,
)

@Serializable
data class ProjectsEmbedded(
    val projects: List<TolgeeProject> = emptyList(),
)

@Serializable
data class PagedLanguages(
    @SerialName("_embedded") val embedded: LanguagesEmbedded? = null,
    val page: PageInfo? = null,
)

@Serializable
data class LanguagesEmbedded(
    val languages: List<TolgeeLanguage> = emptyList(),
)

@Serializable
data class TolgeeNamespace(
    val id: Long? = null,
    val name: String? = null,
)

@Serializable
data class PagedNamespaces(
    @SerialName("_embedded") val embedded: NamespacesEmbedded? = null,
    val page: PageInfo? = null,
)

@Serializable
data class NamespacesEmbedded(
    val namespaces: List<TolgeeNamespace> = emptyList(),
)

@Serializable
data class WhoamiResponse(
    val id: Long? = null,
    val username: String? = null,
    val name: String? = null,
)

@Serializable
data class ApiKeyInfo(
    val id: Long? = null,
    val projectId: Long? = null,
    val scopes: List<String> = emptyList(),
)

/**
 * A flat translations map exported from Tolgee. Tolgee returns nested JSON when
 * `structureDelimiter=.` (default). We pass `structureDelimiter=` (empty) to get
 * a flat map of fully-qualified key -> translation string.
 */
typealias FlatTranslations = Map<String, JsonElement>
