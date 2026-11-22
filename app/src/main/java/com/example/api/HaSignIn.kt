package com.example.api

import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import okio.Buffer

/**
 * Signing in to Home Assistant with a username and password (83.0).
 *
 * The app only ever runs on a long-lived access token. A release from cstone1983/app-builder has
 * none built in, and typing a 180-character token on a wall tablet is not practical, so the
 * sign-in screen does what Home Assistant's own login page does and then turns the result into
 * a long-lived token of its own:
 *
 * 1. `POST auth/login_flow`            start a flow with the `homeassistant` provider
 * 2. `POST auth/login_flow/{flow_id}`  username and password, then a two-step code if asked
 * 3. `POST auth/token`                 swap the flow's code for a short-lived token pair
 * 4. WebSocket `auth/long_lived_access_token`  mint the token the app keeps
 * 5. `POST auth/token action=revoke`   throw the short-lived pair away
 *
 * Everything in this file is plain parsing and message building with no Android and no network,
 * so it is unit tested. Each request was checked against Home Assistant's source
 * (`components/auth/login_flow.py`, `components/auth/__init__.py`) and `docs/auth_api.md` on
 * 2026-11-21, with Home Assistant 2026.11.2 on the house server.
 *
 * Three things matter more than they look:
 *
 * - **A 4xx from steps 1 to 3 counts as a failed login** in Home Assistant (`log_invalid_auth`),
 *   and so does a wrong password or wrong code. Failed logins feed `ip_ban`. So every field has
 *   to be right, and nothing here is ever retried on its own.
 * - **`client_id` is the server's own address plus "/"**, the same value on every request of one
 *   sign-in. That is what Home Assistant's login page sends, and it is the form Home Assistant
 *   accepts for a plain IP address.
 * - **Nothing here logs.** The password and the tokens only pass through.
 *
 * The code this replaces (`startLoginFlow` / `submitLoginStep` / `getAccessToken`, June and July
 * 2026) was never called. It would not have worked: it sent the handler `["homeassistant",
 * "default"]`, which Home Assistant answers with 404 "Invalid handler specified"; it left
 * `client_id` out of the second request, which is a 400; and its response model required
 * `step_id`, which a successful login does not have.
 */

/** The auth provider this screen signs in with: Home Assistant's own users, default instance. */
const val SIGN_IN_PROVIDER = "homeassistant"

/** The step that asks for the two-step code, and the one that asks which method to use. */
const val STEP_MFA = "mfa"
const val STEP_SELECT_MFA = "select_mfa_module"
const val FIELD_MFA_MODULE = "multi_factor_auth_module"

// ---------------------------------------------------------------------------------------------
// Addresses
// ---------------------------------------------------------------------------------------------

/**
 * The `client_id` (and `redirect_uri`) for a server: its scheme, host and port, then "/".
 * A path on the base address is dropped, as the login page does (`location.origin + "/"`).
 */
fun signInClientId(baseUrl: String): String {
    val base = HomeAssistantClient.formatBaseUrl(baseUrl)
    return try {
        val uri = java.net.URI(base)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme.isNullOrEmpty() || host.isNullOrEmpty()) return base
        if (uri.port >= 0) "$scheme://$host:${uri.port}/" else "$scheme://$host/"
    } catch (e: Exception) {
        base
    }
}

/** `http://host:8123/` becomes `ws://host:8123/api/websocket`. */
fun signInWebSocketUrl(baseUrl: String): String {
    val base = HomeAssistantClient.formatBaseUrl(baseUrl)
    val ws = when {
        base.startsWith("https://", ignoreCase = true) -> "wss://" + base.substring(8)
        base.startsWith("http://", ignoreCase = true) -> "ws://" + base.substring(7)
        else -> base
    }
    return ws + "api/websocket"
}

// ---------------------------------------------------------------------------------------------
// Request bodies
// ---------------------------------------------------------------------------------------------

private fun jsonObject(write: JsonWriter.() -> Unit): String {
    val buffer = Buffer()
    val writer = JsonWriter.of(buffer)
    writer.beginObject()
    writer.write()
    writer.endObject()
    writer.close()
    return buffer.readUtf8()
}

/**
 * Body of `POST auth/login_flow`. The handler is the pair `[provider type, provider id]`, and
 * the id of the default provider is JSON `null`, not a string.
 */
fun loginFlowStartBody(clientId: String): String = jsonObject {
    name("client_id").value(clientId)
    name("handler")
    beginArray()
    value(SIGN_IN_PROVIDER)
    nullValue()
    endArray()
    name("redirect_uri").value(clientId)
}

/**
 * Body of `POST auth/login_flow/{flow_id}`: the step's own fields plus `client_id`, which Home
 * Assistant requires on every step.
 */
fun loginFlowStepBody(clientId: String, fields: Map<String, String>): String = jsonObject {
    name("client_id").value(clientId)
    for ((key, value) in fields) {
        if (key != "client_id") name(key).value(value)
    }
}

fun wsAuthMessage(accessToken: String): String = jsonObject {
    name("type").value("auth")
    name("access_token").value(accessToken)
}

fun wsListTokensMessage(id: Int): String = jsonObject {
    name("id").value(id.toLong())
    name("type").value("auth/refresh_tokens")
}

/** `lifespan` is in days and has to be a whole number. */
fun wsCreateTokenMessage(id: Int, clientName: String, lifespanDays: Int): String = jsonObject {
    name("id").value(id.toLong())
    name("type").value("auth/long_lived_access_token")
    name("client_name").value(clientName)
    name("lifespan").value(lifespanDays.toLong())
}

// ---------------------------------------------------------------------------------------------
// Responses
// ---------------------------------------------------------------------------------------------

private fun parseJson(text: String?): Any? {
    if (text.isNullOrBlank()) return null
    return try {
        JsonReader.of(Buffer().writeUtf8(text)).readJsonValue()
    } catch (e: Exception) {
        null
    }
}

private fun Any?.asObject(): Map<*, *>? = this as? Map<*, *>
private fun Map<*, *>.string(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }

/** One input a step asks for. [options] is `value to label`, for a choice. */
data class FlowField(val name: String, val type: String, val options: List<Pair<String, String>> = emptyList())

/** What Home Assistant answered to a login-flow request that it accepted (HTTP 200). */
sealed interface LoginFlowResult {
    /**
     * Home Assistant wants input. [stepId] is `init` (username and password), [STEP_MFA] or
     * [STEP_SELECT_MFA]. [error] is set when the last input was wrong: `invalid_auth`,
     * `invalid_code`, `invalid_auth_module`. The flow stays open either way.
     */
    data class Form(
        val flowId: String,
        val stepId: String,
        val fields: List<FlowField> = emptyList(),
        val error: String? = null,
        val mfaModuleName: String? = null
    ) : LoginFlowResult

    /** Signed in. [code] is good for one `auth/token` request, for ten minutes. */
    data class Authorized(val code: String) : LoginFlowResult

    /** Home Assistant ended the flow: `too_many_retry`, `login_expired`, `unknown_error`. */
    data class Aborted(val reason: String) : LoginFlowResult

    /** Not a login-flow answer at all: something else is listening on that address. */
    data object Unreadable : LoginFlowResult
}

/**
 * Reads a login-flow answer. Only `type` is always there: a form has `step_id`, a finished login
 * has `result` and no `step_id`, an abort has `reason` and neither.
 */
fun parseLoginFlowResponse(body: String?): LoginFlowResult {
    val json = parseJson(body).asObject() ?: return LoginFlowResult.Unreadable
    return when (json.string("type")) {
        "create_entry" -> {
            val code = json.string("result") ?: return LoginFlowResult.Unreadable
            LoginFlowResult.Authorized(code)
        }
        "abort" -> LoginFlowResult.Aborted(json.string("reason") ?: "unknown_error")
        "form" -> {
            val flowId = json.string("flow_id") ?: return LoginFlowResult.Unreadable
            val stepId = json.string("step_id") ?: return LoginFlowResult.Unreadable
            val errors = json["errors"].asObject()
            val error = (errors?.get("base") as? String)
                ?: errors?.values?.firstOrNull { it is String } as? String
            LoginFlowResult.Form(
                flowId = flowId,
                stepId = stepId,
                fields = (json["data_schema"] as? List<*>).orEmpty().mapNotNull(::parseFlowField),
                error = error?.takeIf { it.isNotBlank() },
                mfaModuleName = json["description_placeholders"].asObject()?.string("mfa_module_name")
            )
        }
        else -> LoginFlowResult.Unreadable
    }
}

private fun parseFlowField(raw: Any?): FlowField? {
    val field = raw.asObject() ?: return null
    val name = field.string("name") ?: return null
    // A choice arrives as [[value, label], ...]; older servers sent a plain list of values or a
    // {value: label} object.
    val options = when (val o = field["options"]) {
        is List<*> -> o.mapNotNull { item ->
            when (item) {
                is List<*> -> (item.getOrNull(0) as? String)?.let { it to ((item.getOrNull(1) as? String) ?: it) }
                is String -> item to item
                else -> null
            }
        }
        is Map<*, *> -> o.entries.mapNotNull { (k, v) -> (k as? String)?.let { it to ((v as? String) ?: it) } }
        else -> emptyList()
    }
    return FlowField(name, field.string("type") ?: "string", options)
}

/** The short-lived pair from `auth/token`. Used for a few seconds, then revoked. */
class TokenPair(val accessToken: String, val refreshToken: String) {
    // Not a data class on purpose: no generated toString() to end up in a log line.
    override fun toString(): String = "TokenPair(***)"
}

fun parseTokenResponse(body: String?): TokenPair? {
    val json = parseJson(body).asObject() ?: return null
    val access = json.string("access_token") ?: return null
    val refresh = json.string("refresh_token") ?: return null
    return TokenPair(access, refresh)
}

/** `{"message": "..."}` from the login flow, or `{"error", "error_description"}` from the token endpoint. */
fun parseServerMessage(body: String?): String? {
    val json = parseJson(body).asObject() ?: return null
    return json.string("message") ?: json.string("error_description") ?: json.string("error")
}

/** What arrived on the WebSocket while minting the token. */
sealed interface SignInWsMessage {
    data object AuthRequired : SignInWsMessage
    data object AuthOk : SignInWsMessage
    data class AuthInvalid(val message: String?) : SignInWsMessage
    data class Result(
        val id: Int,
        val success: Boolean,
        val result: Any?,
        val errorCode: String?,
        val errorMessage: String?
    ) : SignInWsMessage {
        override fun toString(): String = "Result(id=$id, success=$success)"
    }
    data object Other : SignInWsMessage
}

fun parseSignInWsMessage(text: String?): SignInWsMessage {
    val json = parseJson(text).asObject() ?: return SignInWsMessage.Other
    return when (json.string("type")) {
        "auth_required" -> SignInWsMessage.AuthRequired
        "auth_ok" -> SignInWsMessage.AuthOk
        "auth_invalid" -> SignInWsMessage.AuthInvalid(json.string("message"))
        "result" -> {
            val id = (json["id"] as? Number)?.toInt() ?: return SignInWsMessage.Other
            val error = json["error"].asObject()
            SignInWsMessage.Result(
                id = id,
                success = json["success"] == true,
                result = json["result"],
                errorCode = error?.string("code"),
                errorMessage = error?.string("message")
            )
        }
        else -> SignInWsMessage.Other
    }
}

/** Names of the long-lived tokens the account already has, from an `auth/refresh_tokens` result. */
fun longLivedTokenNames(result: Any?): List<String> =
    (result as? List<*>).orEmpty().mapNotNull { entry ->
        val token = entry.asObject() ?: return@mapNotNull null
        if (token.string("type") == "long_lived_access_token") token.string("client_name") else null
    }

/**
 * Home Assistant allows one long-lived token per name per account, and answers a second one with
 * a bare "Unknown error". A tablet that is reinstalled signs in again under the same name while
 * the old token is still listed, so a taken name gets " (2)", " (3)" and so on. The old entry is
 * left alone: deleting it here could sign out another device that was given the same name.
 */
fun uniqueTokenName(wanted: String, existing: Collection<String>): String {
    val base = wanted.trim()
    if (base !in existing) return base
    var n = 2
    while ("$base ($n)" in existing) n++
    return "$base ($n)"
}

// ---------------------------------------------------------------------------------------------
// What to do with an answer
// ---------------------------------------------------------------------------------------------

/** What the sign-in screen is asking for. */
sealed interface SignInStage {
    data object Credentials : SignInStage

    /** A two-step code. [moduleName] is Home Assistant's name for the method ("Authenticator app"). */
    data class Code(val moduleName: String?) : SignInStage

    /** An account with more than one two-step method: `id to name`. */
    data class ChooseMethod(val options: List<Pair<String, String>>) : SignInStage
}

/** The next thing sign-in does after a login-flow answer. */
sealed interface SignInMove {
    /** Signed in: swap [code] for tokens. */
    data class Finish(val code: String) : SignInMove

    /**
     * Ask the person for more and keep the flow open. [inputField] is the name the answer is
     * sent back under. [error] says the last answer was wrong; they may type another.
     */
    data class Ask(
        val flowId: String,
        val stage: SignInStage,
        val inputField: String,
        val error: SignInError? = null
    ) : SignInMove

    /**
     * Back to the username and password with [error]. [openFlowId] is a flow Home Assistant
     * still holds and should be told to forget. Never followed by another request on its own.
     */
    data class Stop(
        val error: SignInError,
        val passwordRejected: Boolean = false,
        val openFlowId: String? = null
    ) : SignInMove
}

fun nextSignInMove(result: LoginFlowResult): SignInMove = when (result) {
    is LoginFlowResult.Authorized -> SignInMove.Finish(result.code)
    is LoginFlowResult.Aborted -> SignInMove.Stop(flowAbortMessage(result.reason))
    LoginFlowResult.Unreadable -> SignInMove.Stop(SIGN_IN_NOT_HOME_ASSISTANT)
    is LoginFlowResult.Form -> {
        val error = result.error?.let(::flowErrorMessage)
        val methods = result.fields.firstOrNull { it.name == FIELD_MFA_MODULE }?.options.orEmpty()
        when {
            result.stepId == STEP_MFA -> SignInMove.Ask(
                flowId = result.flowId,
                stage = SignInStage.Code(result.mfaModuleName),
                inputField = result.fields.firstOrNull()?.name ?: "code",
                error = error
            )
            result.stepId == STEP_SELECT_MFA && methods.isNotEmpty() -> SignInMove.Ask(
                flowId = result.flowId,
                stage = SignInStage.ChooseMethod(methods),
                inputField = FIELD_MFA_MODULE,
                error = error
            )
            // The username and password form again, with the reason.
            error != null -> SignInMove.Stop(
                error = error,
                passwordRejected = result.error == "invalid_auth",
                openFlowId = result.flowId
            )
            else -> SignInMove.Stop(
                error = SignInError(
                    "unknown_step",
                    "Home Assistant asked for a sign-in step this app does not know (${result.stepId}). " +
                        "Use a long-lived token instead.",
                    result.stepId
                ),
                openFlowId = result.flowId
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Device name
// ---------------------------------------------------------------------------------------------

/**
 * The name the new token is listed under in Home Assistant (Profile > Security > Long-lived
 * access tokens). [byModel] comes from `signIn.deviceNames` in `layout_config.json`, keyed by
 * the Android model; [fallback] may contain `{model}`.
 */
fun defaultDeviceName(model: String?, byModel: Map<String, String>?, fallback: String?): String {
    val m = model?.trim().orEmpty()
    byModel?.entries?.firstOrNull { it.key.trim().equals(m, ignoreCase = true) }
        ?.value?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val template = fallback?.trim()?.takeIf { it.isNotEmpty() } ?: "HVAC {model}"
    return template.replace("{model}", m.ifEmpty { "device" }).trim()
}

// ---------------------------------------------------------------------------------------------
// Errors
// ---------------------------------------------------------------------------------------------

/**
 * Why sign-in stopped, in words for the screen. [key] names the message so its wording can be
 * replaced from `signIn.messages` in `layout_config.json`; `{detail}` in a replacement is filled
 * with [detail].
 */
data class SignInError(val key: String, val text: String, val detail: String? = null)

class SignInException(val error: SignInError) : Exception(error.text)

/** The wording from `layout_config.json` if there is one for this error, else the built-in one. */
fun signInErrorText(error: SignInError, overrides: Map<String, String>?): String {
    val custom = overrides?.get(error.key)?.trim()?.takeIf { it.isNotEmpty() } ?: return error.text
    return custom.replace("{detail}", error.detail.orEmpty()).trim()
}

/**
 * A form that came back with an error. The first two are Home Assistant's own words for them,
 * from its login page.
 */
fun flowErrorMessage(errorKey: String): SignInError = when (errorKey) {
    "invalid_auth" -> SignInError("invalid_auth", "Invalid username or password")
    "invalid_code" -> SignInError("invalid_code", "Invalid authentication code")
    "invalid_auth_module" -> SignInError("invalid_auth_module", "That two-step method is not available for this account")
    else -> SignInError("flow_error", "Home Assistant refused the sign-in: $errorKey", errorKey)
}

fun flowAbortMessage(reason: String): SignInError = when (reason) {
    "too_many_retry" -> SignInError("too_many_retry", "Too many wrong codes. Start again with your username and password.")
    "login_expired" -> SignInError("login_expired", "Session expired, please log in again.")
    else -> SignInError("flow_aborted", "Home Assistant stopped the sign-in: $reason", reason)
}

/**
 * Home Assistant answered with an HTTP error. Every one of these also counted as a failed login
 * on the server, so none of them is retried.
 */
fun httpErrorMessage(status: Int, body: String?, location: String? = null): SignInError {
    val said = parseServerMessage(body)
    return when {
        status in 300..399 -> SignInError(
            "redirected",
            "The server sent this to another address" +
                (location?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") +
                ". Enter that address under CHANGE SERVER ADDRESS.",
            location
        )
        // The ban list answers 403 with no JSON; a blocked user gets 403 with a message.
        status == 403 && said == null -> SignInError(
            "blocked",
            "Home Assistant is refusing this device (403). After several failed logins it bans " +
                "the address: remove it from ip_bans.yaml and restart Home Assistant."
        )
        status == 404 && said == null -> SignInError(
            "not_home_assistant",
            "That address answered, but not as Home Assistant (404). Check the server address."
        )
        said != null -> SignInError("server_refused", "Home Assistant said: $said ($status)", said)
        else -> SignInError("server_refused", "Home Assistant answered with error $status", status.toString())
    }
}

/** Nothing answered, or the connection failed. */
fun networkErrorMessage(e: Throwable, baseUrl: String): SignInError {
    val why = when (e) {
        is java.net.UnknownHostException -> "the name was not found"
        is java.net.SocketTimeoutException -> "no answer in time"
        is java.net.ConnectException -> "nothing is answering there"
        is javax.net.ssl.SSLException -> "the secure connection failed"
        is java.net.ProtocolException, is java.io.EOFException -> "it did not answer as Home Assistant"
        else -> e.javaClass.simpleName
    }
    return SignInError("unreachable", "Can't reach the server at $baseUrl ($why)", why)
}

val SIGN_IN_NOT_HOME_ASSISTANT = SignInError(
    "not_home_assistant",
    "That address answered, but not as Home Assistant. Check the server address."
)

/** Signed in, but the last part (making this device's own token) did not finish. */
fun tokenStepMessage(detail: String?): SignInError = SignInError(
    "token_failed",
    "Signed in, but Home Assistant did not create this device's token" +
        (detail?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") +
        ". Log in again, with a different device name if it keeps happening.",
    detail
)
