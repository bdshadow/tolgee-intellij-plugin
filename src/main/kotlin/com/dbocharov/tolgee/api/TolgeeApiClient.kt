package com.dbocharov.tolgee.api

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
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
    baseUrl: String,
    private val apiKey: String,
) {
    private val baseUrl: String = validateAndNormaliseBaseUrl(baseUrl)

    private val http: OkHttpClient = sharedHttp

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Project id this API key is bound to, or null for an unbound key (PAT — Personal
     * Access Token). Propagates network / API errors so callers can distinguish
     * "server confirmed this key spans all projects" (`null`) from "the request
     * failed" (thrown exception).
     */
    fun currentApiKeyProjectId(): Long? =
        execute(request("/v2/api-keys/current").get().build()).use { resp ->
            json.decodeFromString<ApiKeyInfo>(resp.body!!.string()).projectId
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

    /** Paged fetch of every key with all translations. */
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
     * Push one language file via `single-step-import`. `OVERRIDE` clobbers existing
     * translations and `createNewKeys=true` accepts brand-new keys.
     *
     * @return `true` if the server response looked like an actual import summary
     *   (non-empty body). `false` when the server accepted with a 200 but returned
     *   nothing — Tolgee sometimes silently drops an import (e.g. unknown language,
     *   missing scope), so callers should surface `false` as a per-file warning
     *   rather than count the file as pushed.
     */
    fun importFlatJson(
        projectId: Long,
        languageTag: String,
        flatJsonBytes: ByteArray,
        namespace: String? = null,
        overrideExisting: Boolean = true,
    ): Boolean {
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
                            put("format", JsonPrimitive("JSON_ICU"))
                            if (!namespace.isNullOrBlank()) put("namespace", JsonPrimitive(namespace))
                        },
                    )
                },
            )
        }

        val paramsJson = json.encodeToString(JsonObject.serializer(), params)
        if (log.isDebugEnabled) {
            log.debug("Import params for project=$projectId, lang=$languageTag, ns=$namespace: $paramsJson")
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
                paramsJson.toRequestBody("application/json".toMediaType()),
            )
            .build()

        val req = request("/v2/projects/$projectId/single-step-import")
            .post(multipart)
            .build()
        return execute(req).use { resp ->
            val body = resp.body?.string().orEmpty().trim()
            if (body.isNotEmpty()) {
                log.info("Import response for project=$projectId, lang=$languageTag: ${body.take(500)}")
                true
            } else {
                log.warn("Import response for project=$projectId, lang=$languageTag: <empty body>")
                false
            }
        }
    }

    private inline fun <reified T> get(path: String, query: Map<String, String> = emptyMap()): T {
        execute(request(path, query).get().build()).use { resp ->
            return json.decodeFromString(resp.body!!.string())
        }
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
            resp.use { r ->
                val body = runCatching { r.body?.string().orEmpty() }.getOrDefault("")
                log.warn("Tolgee ${req.method} ${req.url.encodedPath} -> ${r.code} in ${ms}ms: ${body.take(200)}")
                throw TolgeeApiException(r.code, humanErrorMessage(r.code, body))
            }
        }
        if (log.isDebugEnabled) {
            log.debug("Tolgee ${req.method} ${req.url.encodedPath} -> ${resp.code} in ${ms}ms")
        }
        return resp
    }

    private fun request(path: String, query: Map<String, String> = emptyMap()): Request.Builder {
        val urlBuilder = "${baseUrl.trimEnd('/')}$path".toHttpUrl().newBuilder()
        for ((k, v) in query) urlBuilder.addQueryParameter(k, v)
        return Request.Builder()
            .url(urlBuilder.build())
            .header("X-Api-Key", apiKey)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .header("X-Tolgee-Client", "intellij-plugin")
            .header("X-Tolgee-Client-Version", pluginVersion)
    }

    companion object {
        /**
         * Returns a normalised base URL. Also blocks the API key from being sent over
         * cleartext http:// to a public host — the `.idea/tolgee.xml` that carries the
         * URL is frequently committed to VCS, so a hostile URL there would otherwise
         * leak the key on the next Pull.
         *
         * @throws IllegalArgumentException with a message suitable for surfacing in
         *   the UI when the URL is not usable.
         */
        fun validateAndNormaliseBaseUrl(input: String): String {
            val raw = input.trim()
            require(raw.isNotEmpty()) { "Tolgee instance URL is required." }
            // Detect the scheme up front — okhttp only understands http/https and
            // would throw an opaque IAE for anything else. We want a clear message
            // that names the offending scheme.
            val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://")
                .find(raw)?.groupValues?.get(1)?.lowercase()
            if (scheme != null && scheme != "http" && scheme != "https") {
                throw IllegalArgumentException(
                    "Tolgee instance URL must use http or https (got '$scheme://').",
                )
            }
            val withScheme = if (scheme != null) raw else "https://$raw"
            val url = try {
                withScheme.toHttpUrl()
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Not a valid Tolgee instance URL: '$input'")
            }
            if (url.scheme == "http" && !isLoopbackHost(url.host)) {
                throw IllegalArgumentException(
                    "Refusing to send the API key over cleartext http:// to a non-loopback host " +
                        "('${url.host}'). Use https:// for public Tolgee instances.",
                )
            }
            return withScheme.trimEnd('/')
        }

        /**
         * Turns a raw server response into a message safe to show in a Messages dialog.
         * Proxies and WAFs happily return HTML for a Tolgee JSON API — don't paste an
         * HTML login page verbatim into a modal. Falls back to a status-code-keyed hint
         * plus a compact plain-text tail.
         */
        fun humanErrorMessage(code: Int, body: String): String {
            val hint = when (code) {
                401 -> "Invalid or expired Tolgee API key."
                403 -> "The Tolgee API key lacks the required scope for this operation."
                404 -> "The Tolgee endpoint or project was not found."
                429 -> "Rate-limited by Tolgee. Wait a moment and retry."
                in 500..599 -> "Tolgee server error ($code). See idea.log for details."
                else -> "Tolgee API $code."
            }
            val trimmed = body.trim()
            val looksLikeMarkup = trimmed.startsWith("<") || trimmed.startsWith("<!DOCTYPE", ignoreCase = true)
            if (trimmed.isEmpty() || looksLikeMarkup) return hint
            val compact = trimmed.replace(Regex("\\s+"), " ").take(200)
            return "$hint\n$compact"
        }

        // Cryptographically bound to the local machine — the kernel routes these
        // regardless of DNS, so plain http is safe. mDNS `.local` is deliberately
        // excluded: any device on the same LAN can advertise a `.local` name, so
        // a rogue mDNS responder could catch the API key over cleartext.
        private fun isLoopbackHost(host: String): Boolean {
            val h = host.lowercase()
            return h == "localhost" ||
                h == "::1" ||
                h.matches(Regex("""^127(?:\.\d{1,3}){3}$"""))
        }

        val log = Logger.getInstance(TolgeeApiClient::class.java)

        val sharedHttp: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        // Both `PluginManagerCore.getPlugin` and `ApplicationInfo.getInstance` need the IntelliJ
        // Application to be initialised — pure JUnit tests call the API client without it, so fall
        // back to a minimal identifier when the platform isn't around.
        val pluginVersion: String by lazy {
            runCatching { PluginManagerCore.getPlugin(PluginId.getId("com.dbocharov.tolgee"))?.version }
                .getOrNull().orEmpty().ifEmpty { "unknown" }
        }

        val userAgent: String by lazy {
            val ide = runCatching { ApplicationInfo.getInstance().build.asString() }.getOrNull()
            val os = "${SystemInfo.OS_NAME} ${SystemInfo.OS_VERSION}"
            val arch = SystemInfo.OS_ARCH
            if (ide != null) "Tolgee-IntelliJ/$pluginVersion ($ide; $os; $arch)"
            else "Tolgee-IntelliJ/$pluginVersion ($os; $arch)"
        }
    }
}
