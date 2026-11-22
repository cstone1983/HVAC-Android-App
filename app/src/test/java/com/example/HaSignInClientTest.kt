package com.example

import com.example.api.HaSignInClient
import com.example.api.LoginFlowResult
import com.example.api.SignInException
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The network half of username and password sign-in, run against a stand-in for Home Assistant
 * that answers the way its source says it does (2026.11).
 *
 * What these pin down: each request's path, method, content type and exact body; that no request
 * is ever sent twice; and that the short-lived tokens only go where they should. The tokens and
 * passwords are made-up strings.
 */
class HaSignInClientTest {

    private class Seen(val method: String, val path: String, val contentType: String?, val auth: String?, val body: String)

    private val server = MockWebServer()
    private val seen = CopyOnWriteArrayList<Seen>()
    private val socketSeen = CopyOnWriteArrayList<String>()

    private var startAnswer = json(INIT_FORM)
    private val stepAnswers = ArrayDeque<MockResponse>()
    private var tokenAnswer = json(TOKEN_PAIR)
    private var listedTokens = "[]"
    private var createAnswer: (String) -> String = { """{"id":2,"type":"result","success":true,"result":"long-lived-token"}""" }
    private var socketAcceptsToken = true

    private lateinit var base: String
    private val client = HaSignInClient()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val body = request.body.readUtf8()
                seen += Seen(request.method.orEmpty(), path, request.getHeader("Content-Type"), request.getHeader("Authorization"), body)
                return when {
                    path == "/api/websocket" -> MockResponse().withWebSocketUpgrade(fakeSocket())
                    path == "/auth/login_flow" && request.method == "POST" -> startAnswer
                    path.startsWith("/auth/login_flow/") && request.method == "POST" -> stepAnswers.removeFirst()
                    path.startsWith("/auth/login_flow/") && request.method == "DELETE" -> json("""{"message":"Flow aborted"}""")
                    path == "/auth/token" && body.contains("action=revoke") -> MockResponse().setResponseCode(200)
                    path == "/auth/token" -> tokenAnswer
                    else -> MockResponse().setResponseCode(404).setBody("404: Not Found")
                }
            }
        }
        server.start()
        base = server.url("/").toString()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Home Assistant's side of the WebSocket: auth_required, then a result per command. */
    private fun fakeSocket() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send("""{"type":"auth_required","ha_version":"2026.11.2"}""")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            socketSeen += text
            when {
                text.contains("\"type\":\"auth\"") ->
                    if (socketAcceptsToken && text.contains("\"access_token\":\"short-access\"")) {
                        webSocket.send("""{"type":"auth_ok","ha_version":"2026.11.2"}""")
                    } else {
                        webSocket.send("""{"type":"auth_invalid","message":"Invalid access token or password"}""")
                        webSocket.close(1000, null)
                    }
                text.contains("auth/refresh_tokens") ->
                    webSocket.send("""{"id":1,"type":"result","success":true,"result":$listedTokens}""")
                text.contains("auth/long_lived_access_token") -> webSocket.send(createAnswer(text))
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }
    }

    private fun failure(block: suspend () -> Unit): SignInException = runBlocking {
        try {
            block()
            fail("expected sign-in to stop")
            throw IllegalStateException()
        } catch (e: SignInException) {
            e
        }
    }

    // --- the whole thing ---

    @Test
    fun `username and password end in a long-lived token and nothing else`() = runBlocking {
        stepAnswers += json(CREATE_ENTRY)

        val form = client.startFlow(base) as LoginFlowResult.Form
        val done = client.submitStep(base, form.flowId, linkedMapOf("username" to "chris", "password" to "correct horse"))
        val pair = client.exchangeCode(base, (done as LoginFlowResult.Authorized).code)
        val token = client.createLongLivedToken(base, pair.accessToken, "HVAC tablet - kitchen", 3650)
        client.revoke(base, pair.refreshToken)

        assertEquals("long-lived-token", token)

        // Four HTTP requests and one socket, each sent once.
        assertEquals(
            listOf(
                "POST /auth/login_flow",
                "POST /auth/login_flow/8f7c2d",
                "POST /auth/token",
                "GET /api/websocket",
                "POST /auth/token"
            ),
            seen.map { "${it.method} ${it.path}" }
        )

        // 1. start: same client id and redirect, the provider with a null id, JSON.
        assertEquals("""{"client_id":"$base","handler":["homeassistant",null],"redirect_uri":"$base"}""", seen[0].body)
        assertTrue(seen[0].contentType!!.startsWith("application/json"))

        // 2. credentials, with client_id.
        assertEquals("""{"client_id":"$base","username":"chris","password":"correct horse"}""", seen[1].body)
        assertTrue(seen[1].contentType!!.startsWith("application/json"))

        // 3. the code, form-encoded, with the same client id.
        assertEquals(
            mapOf("grant_type" to "authorization_code", "code" to "0d5a9c1e", "client_id" to base),
            formFields(seen[2].body)
        )
        assertEquals("application/x-www-form-urlencoded", seen[2].contentType)

        // 4. the socket: auth, list, create.
        assertEquals(
            listOf(
                """{"type":"auth","access_token":"short-access"}""",
                """{"id":1,"type":"auth/refresh_tokens"}""",
                """{"id":2,"type":"auth/long_lived_access_token","client_name":"HVAC tablet - kitchen","lifespan":3650}"""
            ),
            socketSeen.toList()
        )

        // 5. the short-lived refresh token is revoked.
        assertEquals(mapOf("token" to "short-refresh", "action" to "revoke"), formFields(seen[4].body))

        // No request carries an Authorization header: there is no token to send until the end,
        // and the short-lived one only ever goes down the socket.
        assertTrue(seen.all { it.auth == null })
    }

    @Test
    fun `a two-step code goes to the same flow`() = runBlocking {
        stepAnswers += json(MFA_FORM)
        stepAnswers += json(MFA_FORM.replace("\"errors\":{}", "\"errors\":{\"base\":\"invalid_code\"}"))
        stepAnswers += json(CREATE_ENTRY)

        val asked = client.submitStep(base, "8f7c2d", mapOf("username" to "chris", "password" to "pw")) as LoginFlowResult.Form
        assertEquals("mfa", asked.stepId)

        val wrong = client.submitStep(base, asked.flowId, mapOf("code" to "000000")) as LoginFlowResult.Form
        assertEquals("invalid_code", wrong.error)

        val done = client.submitStep(base, asked.flowId, mapOf("code" to "123456"))
        assertEquals(LoginFlowResult.Authorized("0d5a9c1e"), done)
        assertEquals("""{"client_id":"$base","code":"123456"}""", seen.last().body)
        assertEquals(3, server.requestCount)
    }

    // --- failures are reported once and never retried ---

    @Test
    fun `a wrong password is one request and Home Assistant's answer`() = runBlocking {
        stepAnswers += json(INIT_FORM.replace("\"errors\":{}", "\"errors\":{\"base\":\"invalid_auth\"}"))

        val result = client.submitStep(base, "8f7c2d", mapOf("username" to "chris", "password" to "wrong")) as LoginFlowResult.Form

        assertEquals("invalid_auth", result.error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a refused start shows what Home Assistant said and is not sent again`() {
        startAnswer = json("""{"message":"Invalid client id"}""", 400)

        val e = failure { client.startFlow(base) }

        assertEquals("Home Assistant said: Invalid client id (400)", e.error.text)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a banned address is told so`() {
        startAnswer = MockResponse().setResponseCode(403).setBody("403: Forbidden")

        val e = failure { client.startFlow(base) }

        assertEquals("blocked", e.error.key)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a server error is not retried`() {
        startAnswer = MockResponse().setResponseCode(503).setBody("Service Unavailable")

        val e = failure { client.startFlow(base) }

        assertEquals("Home Assistant answered with error 503", e.error.text)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `something that is not Home Assistant is said to be`() {
        startAnswer = MockResponse().setResponseCode(200).setBody("<html>router</html>")

        assertEquals("not_home_assistant", failure { client.startFlow(base) }.error.key)
    }

    @Test
    fun `a redirect is reported, not followed`() {
        startAnswer = MockResponse().setResponseCode(307).setHeader("Location", "https://elsewhere.example/auth/login_flow")

        val e = failure { client.startFlow(base) }

        assertEquals("redirected", e.error.key)
        assertTrue(e.error.text.contains("https://elsewhere.example/auth/login_flow"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `nothing listening is can't reach the server`() {
        server.shutdown()

        val e = failure { client.startFlow(base) }

        assertEquals("unreachable", e.error.key)
        assertTrue(e.error.text.startsWith("Can't reach the server at $base"))
    }

    @Test
    fun `an address that is not one is said to be`() {
        assertEquals("bad_address", failure { client.startFlow("kitchen tablet") }.error.key)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a used code is Home Assistant's words, once`() {
        tokenAnswer = json("""{"error":"invalid_request","error_description":"Invalid code"}""", 400)

        val e = failure { client.exchangeCode(base, "0d5a9c1e") }

        assertEquals("Home Assistant said: Invalid code (400)", e.error.text)
        assertEquals(1, server.requestCount)
    }

    // --- the token step ---

    @Test
    fun `a name that is taken gets a number`() = runBlocking {
        listedTokens = """[{"id":"b","type":"long_lived_access_token","client_name":"HVAC tablet - kitchen"},
                           {"id":"a","type":"normal","client_name":null,"is_current":true}]"""

        client.createLongLivedToken(base, "short-access", "HVAC tablet - kitchen", 3650)

        assertTrue(socketSeen.last().contains("\"client_name\":\"HVAC tablet - kitchen (2)\""))
    }

    @Test
    fun `Home Assistant refusing to make the token is reported as that`() {
        createAnswer = { """{"id":2,"type":"result","success":false,"error":{"code":"unknown_error","message":"Unknown error"}}""" }

        val e = failure { client.createLongLivedToken(base, "short-access", "HVAC tablet - kitchen", 3650) }

        assertEquals("token_failed", e.error.key)
        assertTrue(e.error.text.contains("Unknown error"))
        assertEquals(1, socketSeen.count { it.contains("auth/long_lived_access_token") })
    }

    @Test
    fun `a socket that rejects the short-lived token is reported as that`() {
        socketAcceptsToken = false

        val e = failure { client.createLongLivedToken(base, "short-access", "HVAC tablet - kitchen", 3650) }

        assertEquals("token_failed", e.error.key)
        assertFalse(socketSeen.any { it.contains("auth/long_lived_access_token") })
    }

    @Test
    fun `giving a flow up tells Home Assistant`() = runBlocking {
        client.abandonFlow(base, "8f7c2d")

        assertEquals("DELETE /auth/login_flow/8f7c2d", "${seen.single().method} ${seen.single().path}")
    }

    @Test
    fun `revoking never throws`() = runBlocking {
        server.shutdown()
        client.revoke(base, "short-refresh")
        client.abandonFlow(base, "8f7c2d")
        assertNull(null)
    }

    private fun formFields(body: String): Map<String, String> =
        body.split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            java.net.URLDecoder.decode(k, "UTF-8") to java.net.URLDecoder.decode(v, "UTF-8")
        }

    private companion object {
        fun json(body: String, status: Int = 200): MockResponse =
            MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

        const val INIT_FORM = """{"type":"form","flow_id":"8f7c2d","handler":["homeassistant",null],"data_schema":[{"name":"username","required":true,"type":"string"},{"name":"password","required":true,"type":"string"}],"errors":{},"description_placeholders":null,"last_step":null,"preview":null,"step_id":"init"}"""
        const val MFA_FORM = """{"type":"form","flow_id":"8f7c2d","handler":["homeassistant",null],"step_id":"mfa","data_schema":[{"name":"code","required":true,"type":"string"}],"description_placeholders":{"mfa_module_name":"Authenticator app","mfa_module_id":"totp"},"errors":{}}"""
        const val CREATE_ENTRY = """{"version":1,"type":"create_entry","flow_id":"8f7c2d","handler":["homeassistant",null],"description":null,"description_placeholders":null,"result":"0d5a9c1e"}"""
        const val TOKEN_PAIR = """{"access_token":"short-access","token_type":"Bearer","refresh_token":"short-refresh","expires_in":1800,"ha_auth_provider":"homeassistant"}"""
    }
}
