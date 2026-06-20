package com.example.api

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class EntityState(
    val entity_id: String,
    val state: String,
    val attributes: Map<String, Any>? = null,
    val last_changed: String? = null,
    val last_updated: String? = null
) {
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
}
