package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Home Assistant WebSocket Connection States
 */
sealed class HaConnectionState {
    object Disconnected : HaConnectionState()
    data class Connecting(val url: String) : HaConnectionState()
    object Authenticating : HaConnectionState()
    data class Connected(val haVersion: String? = null) : HaConnectionState()
    data class Error(val message: String, val canRetry: Boolean = true) : HaConnectionState()

    val isConnected: Boolean get() = this is Connected
}

/**
 * Outgoing Authentication Payload
 */
@JsonClass(generateAdapter = true)
data class WsAuthMessage(
    val type: String = "auth",
    @Json(name = "access_token") val accessToken: String
)

/**
 * Standard WebSocket Request with incrementing ID
 */
@JsonClass(generateAdapter = true)
data class WsCommandRequest(
    val id: Int,
    val type: String,
    @Json(name = "event_type") val eventType: String? = null,
    val domain: String? = null,
    val service: String? = null,
    @Json(name = "service_data") val serviceData: Map<String, Any?>? = null,
    val target: Map<String, Any?>? = null
)

/**
 * Incoming WebSocket Base Message Envelope
 */
@JsonClass(generateAdapter = true)
data class WsIncomingMessage(
    val id: Int? = null,
    val type: String,
    val success: Boolean? = null,
    @Json(name = "ha_version") val haVersion: String? = null,
    val message: String? = null,
    val error: WsErrorDetails? = null
)

@JsonClass(generateAdapter = true)
data class WsErrorDetails(
    val code: String? = null,
    val message: String? = null
)

/**
 * State Changed Event Payload
 */
@JsonClass(generateAdapter = true)
data class WsStateChangedEvent(
    val id: Int,
    val type: String = "event",
    val event: StateChangedEventBody? = null
)

@JsonClass(generateAdapter = true)
data class StateChangedEventBody(
    @Json(name = "event_type") val eventType: String,
    val data: StateChangedData? = null
)

@JsonClass(generateAdapter = true)
data class StateChangedData(
    @Json(name = "entity_id") val entityId: String,
    @Json(name = "new_state") val newState: EntityState? = null,
    @Json(name = "old_state") val oldState: EntityState? = null
)
