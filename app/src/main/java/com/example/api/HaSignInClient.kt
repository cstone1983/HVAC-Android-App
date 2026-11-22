package com.example.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The network half of username and password sign-in. See [HaSignIn.kt][signInClientId] for the
 * flow and for why every request here is sent exactly once.
 *
 * Each call either returns or throws a [SignInException] carrying the words for the screen.
 * Nothing is logged, and nothing is retried: a request Home Assistant refuses counts as a failed
 * login there, and enough of those ban the device's address.
 */
class HaSignInClient(private val http: OkHttpClient = defaultHttp) {

    /** Step 1. Expect a [LoginFlowResult.Form] with step `init`. */
    suspend fun startFlow(baseUrl: String): LoginFlowResult =
        flowRequest(baseUrl, null, loginFlowStartBody(signInClientId(baseUrl)))

    /** Step 2, and again for the two-step code. [fields] are the step's own inputs. */
    suspend fun submitStep(baseUrl: String, flowId: String, fields: Map<String, String>): LoginFlowResult =
        flowRequest(baseUrl, flowId, loginFlowStepBody(signInClientId(baseUrl), fields))

    private suspend fun flowRequest(baseUrl: String, flowId: String?, json: String): LoginFlowResult =
        withContext(Dispatchers.IO) {
            val url = endpoint(baseUrl, "auth", "login_flow", flowId)
            val request = Request.Builder().url(url).post(json.toRequestBody(JSON)).build()
            execute(baseUrl, request) { status, body, location ->
                if (status != 200) throw SignInException(httpErrorMessage(status, body, location))
                parseLoginFlowResponse(body).also {
                    if (it is LoginFlowResult.Unreadable) throw SignInException(SIGN_IN_NOT_HOME_ASSISTANT)
                }
            }
        }

    /** Step 3. The code works once; `client_id` has to be the one the flow was started with. */
    suspend fun exchangeCode(baseUrl: String, code: String): TokenPair = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", signInClientId(baseUrl))
            .build()
        val request = Request.Builder().url(endpoint(baseUrl, "auth", "token")).post(form).build()
        execute(baseUrl, request) { status, body, location ->
            if (status != 200) throw SignInException(httpErrorMessage(status, body, location))
            parseTokenResponse(body) ?: throw SignInException(SIGN_IN_NOT_HOME_ASSISTANT)
        }
    }

    /**
     * Step 4. Opens a WebSocket of its own with the short-lived token, asks which long-lived
     * tokens the account already has (so the name can be made unique), and creates one.
     * Returns the new token. The socket is closed before this returns.
     */
    suspend fun createLongLivedToken(
        baseUrl: String,
        accessToken: String,
        deviceName: String,
        lifespanDays: Int
    ): String = withContext(Dispatchers.IO) {
        val done = CompletableDeferred<String>()
        val request = Request.Builder().url(signInWebSocketUrl(baseUrl)).build()
        val socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (val message = parseSignInWsMessage(text)) {
                    SignInWsMessage.AuthRequired -> webSocket.send(wsAuthMessage(accessToken))
                    SignInWsMessage.AuthOk -> webSocket.send(wsListTokensMessage(ID_LIST))
                    is SignInWsMessage.AuthInvalid ->
                        done.completeExceptionally(SignInException(tokenStepMessage(message.message)))
                    is SignInWsMessage.Result -> when (message.id) {
                        ID_LIST -> {
                            // If the list cannot be read, try the name as given.
                            val taken = if (message.success) longLivedTokenNames(message.result) else emptyList()
                            webSocket.send(
                                wsCreateTokenMessage(ID_CREATE, uniqueTokenName(deviceName, taken), lifespanDays)
                            )
                        }
                        ID_CREATE -> {
                            val token = (message.result as? String)?.takeIf { it.isNotBlank() }
                            if (message.success && token != null) {
                                done.complete(token)
                            } else {
                                done.completeExceptionally(SignInException(tokenStepMessage(message.errorMessage)))
                            }
                        }
                    }
                    SignInWsMessage.Other -> Unit
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                done.completeExceptionally(SignInException(tokenStepMessage(networkErrorMessage(t, baseUrl).detail)))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                done.completeExceptionally(SignInException(tokenStepMessage("the connection closed")))
                webSocket.close(1000, null)
            }
        })
        try {
            withTimeout(TOKEN_STEP_TIMEOUT_MS) { done.await() }
        } catch (e: TimeoutCancellationException) {
            throw SignInException(tokenStepMessage("no answer in time"))
        } finally {
            if (!socket.close(1000, null)) socket.cancel()
        }
    }

    /**
     * Step 5. Removes the short-lived refresh token and with it the access token it issued.
     * The long-lived token made in step 4 is a separate entry and is not affected.
     *
     * Home Assistant always answers 200 here. Failure is ignored: the pair expires by itself,
     * and there is nothing useful the person at the tablet could do about it.
     */
    suspend fun revoke(baseUrl: String, refreshToken: String) = withContext(Dispatchers.IO) {
        try {
            val form = FormBody.Builder().add("token", refreshToken).add("action", "revoke").build()
            http.newCall(Request.Builder().url(endpoint(baseUrl, "auth", "token")).post(form).build())
                .execute().close()
        } catch (e: Exception) {
            // See above.
        }
    }

    /** Tells Home Assistant to forget a flow that is being given up. Best effort. */
    suspend fun abandonFlow(baseUrl: String, flowId: String) = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(endpoint(baseUrl, "auth", "login_flow", flowId)).delete().build())
                .execute().close()
        } catch (e: Exception) {
            // The flow is dropped by the server on its own in time.
        }
    }

    private fun endpoint(baseUrl: String, vararg segments: String?): HttpUrl {
        val base = HomeAssistantClient.formatBaseUrl(baseUrl).toHttpUrlOrNull()
            ?: throw SignInException(
                SignInError("bad_address", "\"$baseUrl\" is not a server address. It should look like http://192.168.1.10:8123")
            )
        val builder = base.newBuilder()
        segments.filterNotNull().forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private inline fun <T> execute(
        baseUrl: String,
        request: Request,
        read: (status: Int, body: String?, location: String?) -> T
    ): T {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw SignInException(networkErrorMessage(e, baseUrl))
        }
        return response.use {
            val body = try { it.body?.string() } catch (e: IOException) { null }
            read(it.code, body, it.header("Location"))
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val ID_LIST = 1
        private const val ID_CREATE = 2
        private const val TOKEN_STEP_TIMEOUT_MS = 20_000L

        /**
         * A client of its own for sign-in:
         * - no interceptors, so no auth header and no request logging at all;
         * - no retry and no redirects, so a request with the password in it is sent once, to the
         *   address on the screen and nowhere else;
         * - short timeouts, so "can't reach the server" shows up while someone is still standing
         *   at the tablet.
         */
        val defaultHttp: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
