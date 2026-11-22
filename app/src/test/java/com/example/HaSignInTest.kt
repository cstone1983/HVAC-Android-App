package com.example

import com.example.api.FIELD_MFA_MODULE
import com.example.api.LoginFlowResult
import com.example.api.SIGN_IN_NOT_HOME_ASSISTANT
import com.example.api.SignInError
import com.example.api.SignInMove
import com.example.api.SignInStage
import com.example.api.SignInWsMessage
import com.example.api.TokenPair
import com.example.api.defaultDeviceName
import com.example.api.flowAbortMessage
import com.example.api.flowErrorMessage
import com.example.api.httpErrorMessage
import com.example.api.loginFlowStartBody
import com.example.api.loginFlowStepBody
import com.example.api.longLivedTokenNames
import com.example.api.networkErrorMessage
import com.example.api.nextSignInMove
import com.example.api.parseLoginFlowResponse
import com.example.api.parseServerMessage
import com.example.api.parseSignInWsMessage
import com.example.api.parseTokenResponse
import com.example.api.signInClientId
import com.example.api.signInErrorText
import com.example.api.signInWebSocketUrl
import com.example.api.tokenStepMessage
import com.example.api.uniqueTokenName
import com.example.api.wsAuthMessage
import com.example.api.wsCreateTokenMessage
import com.example.api.wsListTokensMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Username and password sign-in (83.0): the requests it builds and how it reads Home Assistant's
 * answers. The JSON samples are the shapes Home Assistant 2026.11 sends, taken from its source
 * (`components/auth/login_flow.py`, `components/auth/__init__.py`).
 *
 * The tokens and passwords in here are made-up strings.
 */
class HaSignInTest {

    private val home = "http://10.10.11.103:8123/"

    // --- addresses ---

    @Test
    fun `client id is the server address with a slash`() {
        assertEquals("http://10.10.11.103:8123/", signInClientId("http://10.10.11.103:8123"))
        assertEquals("http://10.10.11.103:8123/", signInClientId("http://10.10.11.103:8123/"))
        assertEquals("https://ha.example.com/", signInClientId(" https://HA.Example.com "))
    }

    @Test
    fun `client id drops a path, as the login page does`() {
        assertEquals("https://ha.example.com:8443/", signInClientId("https://ha.example.com:8443/lovelace/0"))
    }

    @Test
    fun `websocket address follows the scheme`() {
        assertEquals("ws://10.10.11.103:8123/api/websocket", signInWebSocketUrl("http://10.10.11.103:8123"))
        assertEquals("wss://ha.example.com/api/websocket", signInWebSocketUrl("https://ha.example.com/"))
    }

    // --- request bodies ---

    @Test
    fun `starting a flow names the homeassistant provider with a null id`() {
        // The old, never-used code sent ["homeassistant", "default"], which Home Assistant
        // answers with 404 "Invalid handler specified".
        assertEquals(
            """{"client_id":"http://10.10.11.103:8123/","handler":["homeassistant",null],"redirect_uri":"http://10.10.11.103:8123/"}""",
            loginFlowStartBody(home)
        )
    }

    @Test
    fun `a step carries client_id with its own fields`() {
        // Home Assistant answers a step without client_id with a 400.
        assertEquals(
            """{"client_id":"http://10.10.11.103:8123/","username":"chris","password":"p\"a\\ss"}""",
            loginFlowStepBody(home, linkedMapOf("username" to "chris", "password" to "p\"a\\ss"))
        )
        assertEquals(
            """{"client_id":"http://10.10.11.103:8123/","code":"123456"}""",
            loginFlowStepBody(home, mapOf("code" to "123456"))
        )
    }

    @Test
    fun `a field cannot replace client_id`() {
        assertEquals(
            """{"client_id":"http://10.10.11.103:8123/"}""",
            loginFlowStepBody(home, mapOf("client_id" to "http://evil.example/"))
        )
    }

    @Test
    fun `websocket messages`() {
        assertEquals("""{"type":"auth","access_token":"short-access"}""", wsAuthMessage("short-access"))
        assertEquals("""{"id":1,"type":"auth/refresh_tokens"}""", wsListTokensMessage(1))
        assertEquals(
            """{"id":2,"type":"auth/long_lived_access_token","client_name":"HVAC tablet - kitchen","lifespan":3650}""",
            wsCreateTokenMessage(2, "HVAC tablet - kitchen", 3650)
        )
    }

    // --- login flow answers ---

    private val initForm = """{"type":"form","flow_id":"8f7c2d","handler":["homeassistant",null],
        "data_schema":[{"name":"username","required":true,"type":"string"},{"name":"password","required":true,"type":"string"}],
        "errors":{},"description_placeholders":null,"last_step":null,"preview":null,"step_id":"init"}"""

    @Test
    fun `the first answer is the username and password form`() {
        val form = parseLoginFlowResponse(initForm) as LoginFlowResult.Form
        assertEquals("8f7c2d", form.flowId)
        assertEquals("init", form.stepId)
        assertEquals(listOf("username", "password"), form.fields.map { it.name })
        assertNull(form.error)
    }

    @Test
    fun `a wrong password comes back as the same form with invalid_auth`() {
        val form = parseLoginFlowResponse(initForm.replace("\"errors\":{}", "\"errors\":{\"base\":\"invalid_auth\"}"))
            as LoginFlowResult.Form
        assertEquals("init", form.stepId)
        assertEquals("invalid_auth", form.error)
    }

    @Test
    fun `a finished login has a code and no step_id`() {
        // The old response model required step_id, so it could not have parsed this.
        val done = parseLoginFlowResponse(
            """{"version":1,"type":"create_entry","flow_id":"8f7c2d","handler":["homeassistant",null],
               "description":null,"description_placeholders":null,"result":"0d5a9c1e"}"""
        )
        assertEquals(LoginFlowResult.Authorized("0d5a9c1e"), done)
    }

    private val mfaForm = """{"type":"form","flow_id":"8f7c2d","handler":["homeassistant",null],"step_id":"mfa",
        "data_schema":[{"name":"code","required":true,"type":"string"}],
        "description_placeholders":{"mfa_module_name":"Authenticator app","mfa_module_id":"totp"},"errors":{}}"""

    @Test
    fun `the two-step code form names the method`() {
        val form = parseLoginFlowResponse(mfaForm) as LoginFlowResult.Form
        assertEquals("mfa", form.stepId)
        assertEquals("Authenticator app", form.mfaModuleName)
        assertEquals("code", form.fields.single().name)
    }

    private val chooseForm = """{"type":"form","flow_id":"8f7c2d","step_id":"select_mfa_module",
        "data_schema":[{"name":"multi_factor_auth_module","type":"select","options":[["totp","Authenticator app"],["notify","Notify one-time password"]]}],
        "errors":{}}"""

    @Test
    fun `an account with two methods is asked which one`() {
        val form = parseLoginFlowResponse(chooseForm) as LoginFlowResult.Form
        val field = form.fields.single()
        assertEquals(FIELD_MFA_MODULE, field.name)
        assertEquals(listOf("totp" to "Authenticator app", "notify" to "Notify one-time password"), field.options)
    }

    @Test
    fun `an abort has a reason and nothing else`() {
        assertEquals(
            LoginFlowResult.Aborted("too_many_retry"),
            parseLoginFlowResponse("""{"type":"abort","flow_id":"8f7c2d","handler":["homeassistant",null],"reason":"too_many_retry","description_placeholders":null}""")
        )
    }

    @Test
    fun `anything else is not a login flow answer`() {
        assertEquals(LoginFlowResult.Unreadable, parseLoginFlowResponse("<html>router login</html>"))
        assertEquals(LoginFlowResult.Unreadable, parseLoginFlowResponse(""))
        assertEquals(LoginFlowResult.Unreadable, parseLoginFlowResponse(null))
        assertEquals(LoginFlowResult.Unreadable, parseLoginFlowResponse("""{"type":"create_entry"}"""))
        assertEquals(LoginFlowResult.Unreadable, parseLoginFlowResponse("""{"type":"form","step_id":"init"}"""))
        assertEquals(LoginFlowResult.Unreadable, parseLoginFlowResponse("[1,2]"))
    }

    // --- what to do next ---

    @Test
    fun `a finished login moves on to the tokens`() {
        assertEquals(SignInMove.Finish("0d5a9c1e"), nextSignInMove(LoginFlowResult.Authorized("0d5a9c1e")))
    }

    @Test
    fun `a wrong password stops, empties the password and gives the flow up`() {
        val move = nextSignInMove(LoginFlowResult.Form("8f7c2d", "init", error = "invalid_auth")) as SignInMove.Stop
        assertEquals("Invalid username or password", move.error.text)
        assertTrue(move.passwordRejected)
        assertEquals("8f7c2d", move.openFlowId)
    }

    @Test
    fun `the code step asks for the code and keeps the flow`() {
        val move = nextSignInMove(parseLoginFlowResponse(mfaForm)) as SignInMove.Ask
        assertEquals("8f7c2d", move.flowId)
        assertEquals(SignInStage.Code("Authenticator app"), move.stage)
        assertEquals("code", move.inputField)
        assertNull(move.error)
    }

    @Test
    fun `a wrong code asks again with Home Assistant's words`() {
        val wrong = mfaForm.replace("\"errors\":{}", "\"errors\":{\"base\":\"invalid_code\"}")
        val move = nextSignInMove(parseLoginFlowResponse(wrong)) as SignInMove.Ask
        assertEquals(SignInStage.Code("Authenticator app"), move.stage)
        assertEquals("Invalid authentication code", move.error?.text)
    }

    @Test
    fun `two methods ask which one`() {
        val move = nextSignInMove(parseLoginFlowResponse(chooseForm)) as SignInMove.Ask
        assertEquals(FIELD_MFA_MODULE, move.inputField)
        assertEquals(
            SignInStage.ChooseMethod(listOf("totp" to "Authenticator app", "notify" to "Notify one-time password")),
            move.stage
        )
    }

    @Test
    fun `too many wrong codes stops with nothing left open`() {
        val move = nextSignInMove(LoginFlowResult.Aborted("too_many_retry")) as SignInMove.Stop
        assertEquals("too_many_retry", move.error.key)
        assertNull(move.openFlowId)
        assertFalse(move.passwordRejected)
    }

    @Test
    fun `a step the app does not know stops and points at the token`() {
        val move = nextSignInMove(LoginFlowResult.Form("8f7c2d", "captcha")) as SignInMove.Stop
        assertEquals("unknown_step", move.error.key)
        assertTrue(move.error.text.contains("captcha"))
        assertEquals("8f7c2d", move.openFlowId)
    }

    @Test
    fun `an unreadable answer stops`() {
        assertEquals(SignInMove.Stop(SIGN_IN_NOT_HOME_ASSISTANT), nextSignInMove(LoginFlowResult.Unreadable))
    }

    // --- tokens ---

    @Test
    fun `the token answer gives the short-lived pair`() {
        val pair = parseTokenResponse(
            """{"access_token":"short-access","token_type":"Bearer","refresh_token":"short-refresh","expires_in":1800,"ha_auth_provider":"homeassistant"}"""
        )!!
        assertEquals("short-access", pair.accessToken)
        assertEquals("short-refresh", pair.refreshToken)
    }

    @Test
    fun `a token answer without both tokens is no answer`() {
        assertNull(parseTokenResponse("""{"access_token":"short-access"}"""))
        assertNull(parseTokenResponse("""{"error":"invalid_request","error_description":"Invalid code"}"""))
        assertNull(parseTokenResponse("not json"))
    }

    @Test
    fun `the token pair does not print its tokens`() {
        val printed = TokenPair("short-access", "short-refresh").toString()
        assertFalse(printed.contains("short-access"))
        assertFalse(printed.contains("short-refresh"))
    }

    @Test
    fun `websocket answers`() {
        assertEquals(SignInWsMessage.AuthRequired, parseSignInWsMessage("""{"type":"auth_required","ha_version":"2026.11.2"}"""))
        assertEquals(SignInWsMessage.AuthOk, parseSignInWsMessage("""{"type":"auth_ok","ha_version":"2026.11.2"}"""))
        assertEquals(
            SignInWsMessage.AuthInvalid("Invalid access token or password"),
            parseSignInWsMessage("""{"type":"auth_invalid","message":"Invalid access token or password"}""")
        )
        assertEquals(SignInWsMessage.Other, parseSignInWsMessage("""{"type":"event","id":3}"""))
        assertEquals(SignInWsMessage.Other, parseSignInWsMessage("garbage"))
    }

    @Test
    fun `the new token arrives as a result`() {
        val ok = parseSignInWsMessage("""{"id":2,"type":"result","success":true,"result":"long-lived-token"}""")
            as SignInWsMessage.Result
        assertEquals(2, ok.id)
        assertTrue(ok.success)
        assertEquals("long-lived-token", ok.result)
        assertFalse("a result does not print what it carries", ok.toString().contains("long-lived-token"))
    }

    @Test
    fun `a refused token request says why`() {
        val failed = parseSignInWsMessage(
            """{"id":2,"type":"result","success":false,"error":{"code":"unknown_error","message":"Unknown error"}}"""
        ) as SignInWsMessage.Result
        assertFalse(failed.success)
        assertEquals("unknown_error", failed.errorCode)
        assertEquals("Unknown error", failed.errorMessage)
    }

    @Test
    fun `only long-lived tokens count as taken names`() {
        val list = (parseSignInWsMessage(
            """{"id":1,"type":"result","success":true,"result":[
                 {"id":"a","type":"normal","client_id":"http://10.10.11.103:8123/","client_name":null,"is_current":true},
                 {"id":"b","type":"long_lived_access_token","client_id":null,"client_name":"HVAC tablet - kitchen"},
                 {"id":"c","type":"long_lived_access_token","client_id":null,"client_name":"n8n"},
                 {"id":"d","type":"normal","client_name":"HVAC tablet - upstairs"}]}"""
        ) as SignInWsMessage.Result).result
        assertEquals(listOf("HVAC tablet - kitchen", "n8n"), longLivedTokenNames(list))
        assertEquals(emptyList<String>(), longLivedTokenNames(null))
        assertEquals(emptyList<String>(), longLivedTokenNames("nope"))
    }

    @Test
    fun `a taken name gets a number`() {
        // Home Assistant answers a duplicate name with a bare "Unknown error", and a reinstalled
        // tablet always signs in under a name that is still listed.
        assertEquals("HVAC tablet - kitchen", uniqueTokenName(" HVAC tablet - kitchen ", listOf("n8n")))
        assertEquals("HVAC tablet - kitchen (2)", uniqueTokenName("HVAC tablet - kitchen", listOf("HVAC tablet - kitchen")))
        assertEquals(
            "HVAC tablet - kitchen (3)",
            uniqueTokenName("HVAC tablet - kitchen", listOf("HVAC tablet - kitchen", "HVAC tablet - kitchen (2)"))
        )
    }

    // --- device name ---

    @Test
    fun `the device name comes from the model`() {
        val names = mapOf("SM-X110" to "HVAC tablet - kitchen", "SM-X200" to "HVAC tablet - upstairs")
        assertEquals("HVAC tablet - kitchen", defaultDeviceName("SM-X110", names, "HVAC {model}"))
        assertEquals("HVAC tablet - upstairs", defaultDeviceName(" sm-x200 ", names, "HVAC {model}"))
    }

    @Test
    fun `an unlisted model falls back to its model number`() {
        assertEquals("HVAC Pixel 9", defaultDeviceName("Pixel 9", mapOf("SM-X110" to "x"), "HVAC {model}"))
        assertEquals("HVAC Pixel 9", defaultDeviceName("Pixel 9", null, null))
        assertEquals("HVAC device", defaultDeviceName(null, null, " "))
        assertEquals("Wall panel", defaultDeviceName("Pixel 9", emptyMap(), "Wall panel"))
    }

    // --- errors ---

    @Test
    fun `Home Assistant's own words for a wrong password and a wrong code`() {
        assertEquals("Invalid username or password", flowErrorMessage("invalid_auth").text)
        assertEquals("Invalid authentication code", flowErrorMessage("invalid_code").text)
        assertEquals("Session expired, please log in again.", flowAbortMessage("login_expired").text)
    }

    @Test
    fun `an error the app has no words for is shown as Home Assistant named it`() {
        assertTrue(flowErrorMessage("something_new").text.contains("something_new"))
        assertTrue(flowAbortMessage("unknown_error").text.contains("unknown_error"))
    }

    @Test
    fun `a refused request shows what Home Assistant said`() {
        val e = httpErrorMessage(400, """{"message":"Invalid client id"}""")
        assertEquals("server_refused", e.key)
        assertEquals("Home Assistant said: Invalid client id (400)", e.text)
        assertEquals(
            "Home Assistant said: Invalid code (400)",
            httpErrorMessage(400, """{"error":"invalid_request","error_description":"Invalid code"}""").text
        )
        assertEquals(
            "Home Assistant said: Login blocked: User is local only (403)",
            httpErrorMessage(403, """{"message":"Login blocked: User is local only"}""").text
        )
    }

    @Test
    fun `a 403 with no message is the ban list`() {
        val e = httpErrorMessage(403, "403: Forbidden")
        assertEquals("blocked", e.key)
        assertTrue(e.text.contains("ip_bans.yaml"))
    }

    @Test
    fun `a 404 with no message is not Home Assistant`() {
        assertEquals("not_home_assistant", httpErrorMessage(404, "<html>Not Found</html>").key)
        // Home Assistant's own 404 for a bad handler or a finished flow carries a message.
        assertEquals("server_refused", httpErrorMessage(404, """{"message":"Invalid flow specified"}""").key)
    }

    @Test
    fun `a redirect names where it points`() {
        val e = httpErrorMessage(301, "", "https://ha.example.com/auth/login_flow")
        assertEquals("redirected", e.key)
        assertTrue(e.text.contains("https://ha.example.com/auth/login_flow"))
    }

    @Test
    fun `an error with nothing readable still shows its number`() {
        assertEquals("Home Assistant answered with error 502", httpErrorMessage(502, "Bad Gateway").text)
        assertNull(parseServerMessage("Bad Gateway"))
    }

    @Test
    fun `no answer at all is can't reach the server`() {
        val refused = networkErrorMessage(java.net.ConnectException("Connection refused"), home)
        assertEquals("unreachable", refused.key)
        assertEquals("Can't reach the server at http://10.10.11.103:8123/ (nothing is answering there)", refused.text)
        assertTrue(networkErrorMessage(java.net.UnknownHostException("ha.example.com"), home).text.contains("the name was not found"))
        assertTrue(networkErrorMessage(java.net.SocketTimeoutException("timeout"), home).text.contains("no answer in time"))
        assertTrue(networkErrorMessage(javax.net.ssl.SSLHandshakeException("bad cert"), home).text.contains("secure connection"))
    }

    @Test
    fun `the token step failing says the login itself worked`() {
        val e = tokenStepMessage("Unknown error")
        assertEquals("token_failed", e.key)
        assertTrue(e.text.startsWith("Signed in, but"))
        assertTrue(e.text.contains("Unknown error"))
        assertFalse(tokenStepMessage(null).text.contains("()"))
    }

    @Test
    fun `wording can be replaced from the layout config`() {
        val error = SignInError("server_refused", "Home Assistant said: Invalid client id (400)", "Invalid client id")
        assertEquals(error.text, signInErrorText(error, null))
        assertEquals(error.text, signInErrorText(error, mapOf("invalid_auth" to "Nope")))
        assertEquals(error.text, signInErrorText(error, mapOf("server_refused" to "  ")))
        assertEquals("HA: Invalid client id", signInErrorText(error, mapOf("server_refused" to "HA: {detail}")))
        assertEquals(
            "Wrong username or password",
            signInErrorText(flowErrorMessage("invalid_auth"), mapOf("invalid_auth" to "Wrong username or password"))
        )
    }
}
