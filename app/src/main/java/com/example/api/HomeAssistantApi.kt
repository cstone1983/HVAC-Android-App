package com.example.api

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class EntityState(
    /**
     * Defaulted so a `minimal_response` history payload parses.
     *
     * That form sends the first record of each entity's series in full and every record after it
     * as `{state, last_changed}` only. With no default, Moshi rejects the whole response with
     * "Required value 'entity_id' missing" — which is why history queries had to be fetched the
     * expensive way. Callers that need the id per record either query one entity at a time or
     * fill it in from the series they asked for.
     */
    val entity_id: String = "",
    val state: String,
    val attributes: Map<String, Any>? = null,
    val last_changed: String? = null,
    val last_updated: String? = null
) {
    /**
     * When this reading happened, for plotting.
     *
     * `last_updated` alone is wrong for history: `minimal_response` omits it on every record
     * after the first, so sorting by it silently collapsed every point onto epoch 0.
     * `last_changed` is also the more accurate field here — it moves when the *value* moves,
     * whereas `last_updated` also moves for attribute-only writes that plot as a duplicate point.
     */
    val historyTimestamp: String?
        get() = last_changed ?: last_updated

    fun getDoubleAttribute(key: String): Double? {
        val value = attributes?.get(key) ?: return null
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    fun getStringAttribute(key: String): String? {
        return attributes?.get(key)?.toString()
    }

    fun getBooleanAttribute(key: String): Boolean? {
        val value = attributes?.get(key) ?: return null
        return when (value) {
            is Boolean -> value
            is String -> value.lowercase().toBooleanStrictOrNull()
            is Number -> value.toInt() != 0
            else -> null
        }
    }

    fun getListAttribute(key: String): List<String>? {
        val value = attributes?.get(key) ?: return null
        if (value is List<*>) {
            return value.mapNotNull { it?.toString() }
        }
        return null
    }
}

interface HomeAssistantApi {
    @GET("api/states")
    suspend fun getStates(): List<EntityState>

    @GET("api/history/period/{timestamp}")
    suspend fun getHistory(
        @Path("timestamp") timestamp: String,
        @retrofit2.http.Query("filter_entity_id") filterEntityId: String,
        @retrofit2.http.Query("end_time") endTime: String? = null,
        @retrofit2.http.Query("minimal_response") minimalResponse: String? = null,
        @retrofit2.http.Query("no_attributes") noAttributes: String? = null
    ): List<List<EntityState>>

    @POST("api/services/{domain}/{service}")
    @JvmSuppressWildcards
    suspend fun callService(
        @Path("domain") domain: String,
        @Path("service") service: String,
        @Body payload: Map<String, Any>
    ): Response<okhttp3.ResponseBody>

    @POST("auth/login_flow")
    suspend fun startLoginFlow(
        @Body request: LoginFlowStartRequest
    ): LoginFlowResponse

    @POST("auth/login_flow/{flow_id}")
    suspend fun submitLoginStep(
        @Path("flow_id") flowId: String,
        @Body request: LoginFlowStepRequest
    ): LoginFlowResponse

    @POST("auth/token")
    @retrofit2.http.FormUrlEncoded
    suspend fun getAccessToken(
        @retrofit2.http.Field("grant_type") grantType: String,
        @retrofit2.http.Field("code") code: String,
        @retrofit2.http.Field("client_id") clientId: String,
        @retrofit2.http.Field("redirect_uri") redirectUri: String
    ): TokenResponse

    @POST("auth/token")
    @retrofit2.http.FormUrlEncoded
    suspend fun revokeToken(
        @retrofit2.http.Field("token") token: String,
        @retrofit2.http.Field("action") action: String = "revoke"
    ): okhttp3.ResponseBody
}

@JsonClass(generateAdapter = true)
data class LoginFlowStartRequest(
    val client_id: String = "http://localhost/",
    val handler: List<String> = listOf("homeassistant", "default"),
    val redirect_uri: String = "http://localhost/"
)

@JsonClass(generateAdapter = true)
data class LoginFlowResponse(
    val flow_id: String,
    val step_id: String,
    val type: String,
    val result: String? = null,
    val errors: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class LoginFlowStepRequest(
    val username: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class TokenResponse(
    val access_token: String,
    val expires_in: Int,
    val refresh_token: String,
    val token_type: String
)
