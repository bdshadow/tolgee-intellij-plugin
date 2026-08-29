package io.tolgee.intellij.api

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin Tolgee REST client. All calls are blocking — wrap in a background task.
 * PAKs (`tgpak_*`) are bound to one project; PATs (`tgpat_*`) span every visible
 * project. Both use the `X-Api-Key` header.
 */
class TolgeeApiClient(
    private val baseUrl: String,
    private val apiKey: String,
) {
    private val http: OkHttpClient = sharedHttp

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun request(path: String, query: Map<String, String> = emptyMap()): Request.Builder {
        val urlBuilder = "${baseUrl.trimEnd('/')}$path".toHttpUrl().newBuilder()
        for ((k, v) in query) urlBuilder.addQueryParameter(k, v)
        return Request.Builder()
            .url(urlBuilder.build())
            .header("X-Api-Key", apiKey)
            .header("Accept", "application/json")
    }

    private fun execute(req: Request): Response {
        // The API key sits in the `X-Api-Key` header of `req` — never let it into a log line.
        val started = System.nanoTime()
        val resp = try {
            http.newCall(req).execute()
        } catch (e: IOException) {
            val ms = (System.nanoTime() - started) / 1_000_000
            log.warn("Tolgee ${req.method} ${req.url.encodedPath} failed after ${ms}ms: ${e.message}")
            throw e
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        if (!resp.isSuccessful) {
            val body = resp.body?.string().orEmpty()
            resp.close()
            log.warn("Tolgee ${req.method} ${req.url.encodedPath} -> ${resp.code} in ${ms}ms: ${body.take(200)}")
            throw TolgeeApiException(resp.code, "Tolgee API ${resp.code}: ${body.take(500)}")
        }
        if (log.isDebugEnabled) {
            log.debug("Tolgee ${req.method} ${req.url.encodedPath} -> ${resp.code} in ${ms}ms")
        }
        return resp
    }

    private inline fun <reified T> get(path: String, query: Map<String, String> = emptyMap()): T {
        execute(request(path, query).get().build()).use { resp ->
            return json.decodeFromString(resp.body!!.string())
        }
    }

    /** Project id this API key is bound to, or null for unbound keys (PAT). */
    fun currentApiKeyProjectId(): Long? {
        return try {
            execute(request("/v2/api-keys/current").get().build()).use { resp ->
                json.decodeFromString<ApiKeyInfo>(resp.body!!.string()).projectId
            }
        } catch (_: TolgeeApiException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    fun listProjects(): List<TolgeeProject> {
        val collected = mutableListOf<TolgeeProject>()
        var page = 0
        while (true) {
            val resp = get<PagedProjects>(
                "/v2/projects",
                mapOf("page" to page.toString(), "size" to "100"),
            )
            collected += resp.embedded?.projects.orEmpty()
            val info = resp.page ?: break
            if (page + 1 >= info.totalPages) break
            page++
        }
        return collected
    }

    fun getProject(projectId: Long): TolgeeProject =
        get("/v2/projects/$projectId")

    fun listProjectLanguages(projectId: Long): List<TolgeeLanguage> {
        val collected = mutableListOf<TolgeeLanguage>()
        var page = 0
        while (true) {
            val resp = get<PagedLanguages>(
                "/v2/projects/$projectId/languages",
                mapOf("page" to page.toString(), "size" to "100"),
            )
            collected += resp.embedded?.languages.orEmpty()
            val info = resp.page ?: break
            if (page + 1 >= info.totalPages) break
            page++
        }
        return collected
    }

    /** Namespaces referenced by keys. The default (unnamed) namespace is returned as an empty string. */
    fun listProjectNamespaces(projectId: Long): List<String> {
        val collected = mutableListOf<String>()
        var page = 0
        while (true) {
            val resp = get<PagedNamespaces>(
                "/v2/projects/$projectId/used-namespaces",
                mapOf("page" to page.toString(), "size" to "100"),
            )
            collected += resp.embedded?.namespaces.orEmpty().map { it.name.orEmpty() }
            val info = resp.page ?: break
            if (page + 1 >= info.totalPages) break
            page++
        }
        return collected
    }

    /** Paged fetch of every key with all translations. Used by Pull. */
    fun listAllKeys(projectId: Long, languages: List<String> = emptyList()): List<TolgeeKey> {
        val collected = mutableListOf<TolgeeKey>()
        var page = 0
        while (true) {
            val q = mutableMapOf("page" to page.toString(), "size" to "200")
            if (languages.isNotEmpty()) q["languages"] = languages.joinToString(",")
            val resp = get<PagedKeys>("/v2/projects/$projectId/translations", q)
            collected += resp.embedded?.keys.orEmpty()
            val info = resp.page ?: break
            if (page + 1 >= info.totalPages) break
            page++
        }
        return collected
    }

    /**
     * Push one language file via `single-step-import`. `OVERRIDE` clobbers
     * existing translations and `createNewKeys=true` accepts brand-new keys.
     */
    fun importFlatJson(
        projectId: Long,
        languageTag: String,
        flatJsonBytes: ByteArray,
        namespace: String? = null,
        overrideExisting: Boolean = true,
    ) {
        val params = buildJsonObject {
            put("forceMode", JsonPrimitive(if (overrideExisting) "OVERRIDE" else "KEEP"))
            put("createNewKeys", JsonPrimitive(true))
            put(
                "fileMappings",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("fileName", JsonPrimitive("$languageTag.json"))
                            put("languageTag", JsonPrimitive(languageTag))
                            put("format", JsonPrimitive("JSON_TOLGEE"))
                            if (!namespace.isNullOrBlank()) put("namespace", JsonPrimitive(namespace))
                        },
                    )
                },
            )
        }

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "files",
                "$languageTag.json",
                flatJsonBytes.toRequestBody("application/json".toMediaType()),
            )
            .addFormDataPart(
                "params",
                null,
                json.encodeToString(JsonObject.serializer(), params)
                    .toRequestBody("application/json".toMediaType()),
            )
            .build()

        val req = request("/v2/projects/$projectId/single-step-import")
            .post(multipart as RequestBody)
            .build()
        execute(req).close()
    }

    private companion object {
        val log = Logger.getInstance(TolgeeApiClient::class.java)

        // Shared across TolgeeApiClient instances so we reuse connection/thread pools instead of
        // spinning up a fresh OkHttpClient per dialog interaction.
        val sharedHttp: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
