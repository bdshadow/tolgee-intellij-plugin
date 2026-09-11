package com.dbocharov.tolgee.api

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class TolgeeApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: TolgeeApiClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = TolgeeApiClient(server.url("/").toString().trimEnd('/'), "tgpat_test")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `currentApiKeyProjectId returns bound project id`() {
        server.enqueue(jsonResponse("""{"id":1,"projectId":42,"scopes":["translations.view"]}"""))
        assertEquals(42L, client.currentApiKeyProjectId())
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/v2/api-keys/current", req.path)
        assertEquals("tgpat_test", req.getHeader("X-Api-Key"))
    }

    @Test
    fun `every request carries plugin client-identification headers`() {
        server.enqueue(jsonResponse("""{"id":1,"name":"demo"}"""))
        client.getProject(1)
        val req = server.takeRequest()
        assertEquals("intellij-plugin", req.getHeader("X-Tolgee-Client"))
        val version = req.getHeader("X-Tolgee-Client-Version")
        assertNotNull("X-Tolgee-Client-Version should be present", version)
        val ua = req.getHeader("User-Agent") ?: ""
        assertTrue("User-Agent should announce the plugin, got: $ua", ua.startsWith("Tolgee-IntelliJ/"))
    }

    @Test
    fun `currentApiKeyProjectId surfaces non-2xx as TolgeeApiException`() {
        server.enqueue(MockResponse().setResponseCode(403))
        try {
            client.currentApiKeyProjectId()
            fail("expected TolgeeApiException")
        } catch (e: TolgeeApiException) {
            assertEquals(403, e.statusCode)
        }
    }

    @Test
    fun `listProjects walks all pages`() {
        server.enqueue(
            jsonResponse(
                """{"_embedded":{"projects":[{"id":1,"name":"a"},{"id":2,"name":"b"}]},
                    "page":{"size":2,"number":0,"totalElements":3,"totalPages":2}}""",
            ),
        )
        server.enqueue(
            jsonResponse(
                """{"_embedded":{"projects":[{"id":3,"name":"c"}]},
                    "page":{"size":2,"number":1,"totalElements":3,"totalPages":2}}""",
            ),
        )
        val projects = client.listProjects()
        assertEquals(listOf(1L, 2L, 3L), projects.map { it.id })

        val first = server.takeRequest()
        assertTrue(first.path!!.startsWith("/v2/projects?"))
        assertTrue(first.path!!.contains("page=0"))
        val second = server.takeRequest()
        assertTrue(second.path!!.contains("page=1"))
    }

    @Test
    fun `getProject decodes single response`() {
        server.enqueue(jsonResponse("""{"id":7,"name":"demo","slug":"demo"}"""))
        val p = client.getProject(7)
        assertEquals(7L, p.id)
        assertEquals("demo", p.name)
        assertEquals("/v2/projects/7", server.takeRequest().path)
    }

    @Test
    fun `listAllKeys aggregates pages and threads translations`() {
        server.enqueue(
            jsonResponse(
                """{"_embedded":{"keys":[
                       {"keyId":1,"keyName":"a","translations":{"en":{"text":"A"}}}
                    ]},
                    "page":{"size":1,"number":0,"totalElements":2,"totalPages":2}}""",
            ),
        )
        server.enqueue(
            jsonResponse(
                """{"_embedded":{"keys":[
                       {"keyId":2,"keyName":"b","keyIsPlural":true,"translations":{"en":{"text":"B"}}}
                    ]},
                    "page":{"size":1,"number":1,"totalElements":2,"totalPages":2}}""",
            ),
        )
        val keys = client.listAllKeys(99)
        assertEquals(listOf("a", "b"), keys.map { it.keyName })
        assertTrue(keys[1].keyIsPlural)
    }

    @Test
    fun `listAllKeys sends explicit languages so pull does not fall back to server-default subset`() {
        server.enqueue(
            jsonResponse(
                """{"_embedded":{"keys":[]},
                    "page":{"size":200,"number":0,"totalElements":0,"totalPages":0}}""",
            ),
        )
        client.listAllKeys(99, listOf("en", "de", "fr"))
        val path = server.takeRequest().path!!
        assertTrue("expected languages query param, got path: $path", path.contains("languages=en%2Cde%2Cfr"))
    }

    @Test
    fun `listAllKeys omits languages query param when list is empty`() {
        server.enqueue(
            jsonResponse(
                """{"_embedded":{"keys":[]},
                    "page":{"size":200,"number":0,"totalElements":0,"totalPages":0}}""",
            ),
        )
        client.listAllKeys(99)
        val path = server.takeRequest().path!!
        assertFalse("did not expect languages query param, got path: $path", path.contains("languages="))
    }

    @Test
    fun `listProjectLanguages walks pages and decodes tags`() {
        server.enqueue(
            jsonResponse(
                """{"_embedded":{"languages":[
                       {"id":1,"tag":"en","name":"English","base":true},
                       {"id":2,"tag":"de","name":"German"}
                    ]},
                    "page":{"size":2,"number":0,"totalElements":3,"totalPages":2}}""",
            ),
        )
        server.enqueue(
            jsonResponse(
                """{"_embedded":{"languages":[{"id":3,"tag":"fr","name":"French"}]},
                    "page":{"size":2,"number":1,"totalElements":3,"totalPages":2}}""",
            ),
        )
        val langs = client.listProjectLanguages(5)
        assertEquals(listOf("en", "de", "fr"), langs.map { it.tag })
        assertTrue(server.takeRequest().path!!.startsWith("/v2/projects/5/languages"))
    }

    @Test
    fun `listProjectNamespaces returns names including the default (empty) namespace`() {
        server.enqueue(
            jsonResponse(
                """{"_embedded":{"namespaces":[
                       {"id":null,"name":""},
                       {"id":1,"name":"emails"},
                       {"id":2,"name":"invoices"}
                    ]},
                    "page":{"size":100,"number":0,"totalElements":3,"totalPages":1}}""",
            ),
        )
        val ns = client.listProjectNamespaces(9)
        assertEquals(listOf("", "emails", "invoices"), ns)
        assertTrue(server.takeRequest().path!!.startsWith("/v2/projects/9/used-namespaces"))
    }

    @Test
    fun `listProjectNamespaces treats null name as default namespace`() {
        server.enqueue(
            jsonResponse(
                """{"_embedded":{"namespaces":[{"id":null,"name":null}]},
                    "page":{"size":100,"number":0,"totalElements":1,"totalPages":1}}""",
            ),
        )
        assertEquals(listOf(""), client.listProjectNamespaces(1))
    }

    @Test
    fun `listProjectNamespaces tolerates empty embedded`() {
        server.enqueue(jsonResponse("""{"page":{"size":100,"number":0,"totalElements":0,"totalPages":0}}"""))
        assertEquals(emptyList<String>(), client.listProjectNamespaces(1))
    }

    @Test
    fun `importFlatJson posts multipart with expected fields`() {
        server.enqueue(MockResponse().setResponseCode(200))
        client.importFlatJson(
            projectId = 12,
            languageTag = "de",
            flatJsonBytes = """{"hello":"Hallo"}""".toByteArray(),
            namespace = "emails",
            overrideExisting = true,
        )
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v2/projects/12/single-step-import", req.path)
        val ct = req.getHeader("Content-Type") ?: ""
        assertTrue("expected multipart, got: $ct", ct.startsWith("multipart/form-data"))
        val body = req.body.readUtf8()
        assertTrue("body should contain uploaded file", body.contains("de.json"))
        assertTrue("body should contain namespace", body.contains("\"namespace\":\"emails\""))
        assertTrue("body should contain OVERRIDE", body.contains("\"forceMode\":\"OVERRIDE\""))
        assertTrue("body should declare JSON_ICU format", body.contains("\"format\":\"JSON_ICU\""))
        assertTrue("body should contain the JSON payload", body.contains("Hallo"))
    }

    @Test
    fun `importFlatJson omits namespace field when null`() {
        server.enqueue(MockResponse().setResponseCode(200))
        client.importFlatJson(12, "en", "{}".toByteArray(), namespace = null)
        val body = server.takeRequest().body.readUtf8()
        assertTrue("namespace key should not appear", !body.contains("\"namespace\""))
    }

    @Test
    fun `importFlatJson returns true when the server responds with a body`() {
        server.enqueue(jsonResponse("""{"summary": "ok"}"""))
        val acknowledged = client.importFlatJson(12, "en", "{}".toByteArray())
        assertTrue(acknowledged)
    }

    @Test
    fun `importFlatJson returns false when the server responds with an empty body`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val acknowledged = client.importFlatJson(12, "en", "{}".toByteArray())
        assertFalse(acknowledged)
    }

    @Test
    fun `non-2xx surfaces TolgeeApiException with body`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("bad request"))
        try {
            client.getProject(1)
            fail("expected TolgeeApiException")
        } catch (e: TolgeeApiException) {
            assertEquals(400, e.statusCode)
            assertNotNull(e.message)
            assertTrue(e.message!!.contains("bad request"))
        }
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
