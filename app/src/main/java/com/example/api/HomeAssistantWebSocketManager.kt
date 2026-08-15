package com.example.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Production-grade Home Assistant WebSocket Manager.
 * 
 * Provides:
 * - Persistent sub-second bidirectional WebSocket connectivity
 * - Automated auth handshake (auth_required -> auth -> auth_ok)
 * - Baseline state retrieval (get_states) & incremental event subscription (subscribe_events)
 * - Thread-safe service call dispatching (call_service)
 * - 30-second ping/pong heartbeat with 10-second zombie connection auto-remediation
 * - Exponential backoff with random jitter on disconnects
 * - Android ConnectivityManager network transitions (Wi-Fi/Cellular) auto-reconnection
 */
class HomeAssistantWebSocketManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "HA_WS_Manager"
        private const val PING_INTERVAL_MS = 30_000L
        private const val PING_TIMEOUT_MS = 10_000L
        private const val MIN_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L

        @Volatile
        private var instance: HomeAssistantWebSocketManager? = null

        fun getInstance(context: Context): HomeAssistantWebSocketManager {
            return instance ?: synchronized(this) {
                instance ?: HomeAssistantWebSocketManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val entityStateListAdapter = moshi.adapter<List<EntityState>>(
        Types.newParameterizedType(List::class.java, EntityState::class.java)
    )
    private val entityStateAdapter = moshi.adapter(EntityState::class.java)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read for persistent stream
        .retryOnConnectionFailure(true)
        .build()

    // Observable states
    private val _connectionState = MutableStateFlow<HaConnectionState>(HaConnectionState.Disconnected)
    val connectionState: StateFlow<HaConnectionState> = _connectionState.asStateFlow()

    private val _states = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    val states: StateFlow<Map<String, EntityState>> = _states.asStateFlow()

    private val _stateUpdates = MutableSharedFlow<EntityState>(extraBufferCapacity = 64)
    val stateUpdates: SharedFlow<EntityState> = _stateUpdates.asSharedFlow()

    // Active session configuration
    @Volatile private var currentRawUrl: String = ""
    @Volatile private var currentToken: String = ""
    @Volatile private var currentWsUrl: String = ""
    @Volatile private var isExplicitlyDisconnected = false

    // WebSocket instance & tracking
    @Volatile private var activeWebSocket: WebSocket? = null
    private val messageIdGenerator = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    
    // Heartbeat & Reconnect Jobs
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var currentBackoffMs = MIN_BACKOFF_MS
    private var networkCallbackRegistered = false

    init {
        registerNetworkCallback()
    }

    /**
     * Converts standard HTTP/HTTPS Home Assistant base URL to WS/WSS URL
     */
    fun toWebSocketUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""

        val withScheme = if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true) &&
            !trimmed.startsWith("ws://", ignoreCase = true) &&
            !trimmed.startsWith("wss://", ignoreCase = true)
        ) {
            "http://$trimmed"
        } else {
            trimmed
        }

        var wsScheme = when {
            withScheme.startsWith("https://", ignoreCase = true) -> withScheme.replaceFirst("https://", "wss://", ignoreCase = true)
            withScheme.startsWith("http://", ignoreCase = true) -> withScheme.replaceFirst("http://", "ws://", ignoreCase = true)
            else -> withScheme
        }

        if (!wsScheme.endsWith("/")) {
            wsScheme += "/"
        }

        return if (wsScheme.endsWith("/api/websocket/")) {
            wsScheme.removeSuffix("/")
        } else if (wsScheme.endsWith("/api/websocket")) {
            wsScheme
        } else {
            "${wsScheme}api/websocket"
        }
    }

    /**
     * Connect or update connection credentials
     */
    fun connect(rawUrl: String, token: String) {
        if (rawUrl.isBlank() || token.isBlank()) {
            Log.w(TAG, "Cannot connect: URL or token is blank")
            return
        }

        currentRawUrl = rawUrl
        currentToken = token.trim()
        currentWsUrl = toWebSocketUrl(rawUrl)
        isExplicitlyDisconnected = false
        currentBackoffMs = MIN_BACKOFF_MS

        Log.i(TAG, "Connecting to Home Assistant WebSocket: $currentWsUrl")
        connectInternal()
    }

    /**
     * Disconnects cleanly and stops auto-reconnect
     */
    fun disconnect() {
        isExplicitlyDisconnected = true
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        
        activeWebSocket?.close(1000, "Client initiated clean disconnect")
        activeWebSocket = null
        _connectionState.value = HaConnectionState.Disconnected
        Log.i(TAG, "Clean disconnect executed.")
    }

    private fun connectInternal() {
        if (isExplicitlyDisconnected) return
        if (currentWsUrl.isEmpty() || currentToken.isEmpty()) return

        // Clean any existing connection
        heartbeatJob?.cancel()
        activeWebSocket?.cancel()
        activeWebSocket = null

        _connectionState.value = HaConnectionState.Connecting(currentWsUrl)

        val request = try {
            Request.Builder()
                .url(currentWsUrl)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Invalid WebSocket URL: $currentWsUrl", e)
            _connectionState.value = HaConnectionState.Error("Invalid WebSocket URL: ${e.message}", canRetry = false)
            return
        }

        activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket transport opened. Awaiting auth_required...")
                _connectionState.value = HaConnectionState.Authenticating
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closing (code $code): $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closed (code $code): $reason")
                if (!isExplicitlyDisconnected) {
                    _connectionState.value = HaConnectionState.Disconnected
                    scheduleReconnect("Socket closed ($code: $reason)")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.localizedMessage}", t)
                if (!isExplicitlyDisconnected) {
                    _connectionState.value = HaConnectionState.Error("Network error: ${t.localizedMessage}")
                    scheduleReconnect("Socket failure: ${t.localizedMessage}")
                }
            }
        })
    }

    /**
     * Processes incoming WebSocket JSON messages
     */
    private fun handleIncomingMessage(webSocket: WebSocket, text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")

            when (type) {
                "auth_required" -> {
                    val haVersion = json.optString("ha_version", "unknown")
                    Log.i(TAG, "Received auth_required (HA version $haVersion). Sending auth token...")
                    _connectionState.value = HaConnectionState.Authenticating
                    
                    val authMsg = JSONObject().apply {
                        put("type", "auth")
                        put("access_token", currentToken)
                    }
                    webSocket.send(authMsg.toString())
                }

                "auth_ok" -> {
                    val haVersion = json.optString("ha_version", null)
                    Log.i(TAG, "Authentication SUCCESS! Connected to Home Assistant $haVersion")
                    _connectionState.value = HaConnectionState.Connected(haVersion)
                    currentBackoffMs = MIN_BACKOFF_MS // Reset backoff on success

                    // 1. Fetch initial baseline states
                    scope.launch {
                        fetchBaselineStates()
                        // 2. Subscribe to real-time events
                        subscribeToStateEvents()
                        // 3. Start 30s heartbeat
                        startHeartbeat()
                    }
                }

                "auth_invalid" -> {
                    val message = json.optString("message", "Invalid access token")
                    Log.e(TAG, "Authentication FAILED: $message")
                    _connectionState.value = HaConnectionState.Error("Authentication failed: $message", canRetry = false)
                    webSocket.close(4001, "Auth Invalid")
                }

                "result" -> {
                    val id = json.optInt("id", -1)
                    if (id != -1) {
                        val deferred = pendingRequests.remove(id)
                        deferred?.complete(json)
                    }
                }

                "event" -> {
                    val eventObj = json.optJSONObject("event")
                    if (eventObj != null && eventObj.optString("event_type") == "state_changed") {
                        val dataObj = eventObj.optJSONObject("data")
                        if (dataObj != null) {
                            handleStateChangeEvent(dataObj)
                        }
                    }
                }

                "pong" -> {
                    val id = json.optInt("id", -1)
                    if (id != -1) {
                        pendingRequests.remove(id)?.complete(json)
                    }
                }

                else -> {
                    Log.v(TAG, "Received unhandled HA message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing incoming WebSocket message: $text", e)
        }
    }

    /**
     * Baseline state initialization (get_states)
     */
    private suspend fun fetchBaselineStates() {
        val id = messageIdGenerator.getAndIncrement()
        val request = JSONObject().apply {
            put("id", id)
            put("type", "get_states")
        }

        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[id] = deferred

        if (sendJson(request)) {
            val response = withTimeoutOrNull(15_000L) { deferred.await() }
            if (response != null && response.optBoolean("success", false)) {
                val resultArray = response.optJSONArray("result")
                if (resultArray != null) {
                    val statesList = parseEntityStates(resultArray)
                    val statesMap = statesList.associateBy { it.entity_id }
                    _states.value = statesMap
                    Log.i(TAG, "Baseline states loaded: ${statesMap.size} entities")
                }
            } else {
                Log.w(TAG, "Failed to retrieve baseline states or request timed out.")
            }
        }
    }

    /**
     * Real-time event subscription (subscribe_events -> state_changed)
     */
    private suspend fun subscribeToStateEvents() {
        val id = messageIdGenerator.getAndIncrement()
        val request = JSONObject().apply {
            put("id", id)
            put("type", "subscribe_events")
            put("event_type", "state_changed")
        }

        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[id] = deferred

        if (sendJson(request)) {
            val response = withTimeoutOrNull(10_000L) { deferred.await() }
            if (response != null && response.optBoolean("success", false)) {
                Log.i(TAG, "Successfully subscribed to state_changed events.")
            } else {
                Log.w(TAG, "Failed to subscribe to state_changed events.")
            }
        }
    }

    /**
     * Processes incremental state updates
     */
    private fun handleStateChangeEvent(dataObj: JSONObject) {
        val entityId = dataObj.optString("entity_id")
        if (entityId.isEmpty()) return

        val newStateObj = dataObj.optJSONObject("new_state")
        if (newStateObj != null) {
            try {
                val newState = entityStateAdapter.fromJson(newStateObj.toString())
                if (newState != null) {
                    _states.update { currentMap ->
                        val updated = HashMap(currentMap)
                        updated[entityId] = newState
                        updated
                    }
                    _stateUpdates.tryEmit(newState)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing entity state update for $entityId", e)
            }
        } else {
            // Entity removed
            _states.update { currentMap ->
                val updated = HashMap(currentMap)
                updated.remove(entityId)
                updated
            }
        }
    }

    /**
     * Thread-safe service call dispatching over WebSocket
     */
    suspend fun callService(
        domain: String,
        service: String,
        serviceData: Map<String, Any?>? = null,
        target: Map<String, Any?>? = null
    ): Boolean {
        val id = messageIdGenerator.getAndIncrement()
        val request = JSONObject().apply {
            put("id", id)
            put("type", "call_service")
            put("domain", domain)
            put("service", service)
            if (!serviceData.isNullOrEmpty()) {
                put("service_data", JSONObject(serviceData))
            }
            if (!target.isNullOrEmpty()) {
                put("target", JSONObject(target))
            }
        }

        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[id] = deferred

        return if (sendJson(request)) {
            val response = withTimeoutOrNull(10_000L) { deferred.await() }
            if (response != null) {
                val success = response.optBoolean("success", false)
                if (!success) {
                    val err = response.optJSONObject("error")
                    Log.e(TAG, "call_service $domain.$service returned error: ${err?.optString("message")}")
                }
                success
            } else {
                Log.w(TAG, "call_service $domain.$service timed out after 10s.")
                false
            }
        } else {
            Log.e(TAG, "Failed sending call_service $domain.$service: WebSocket inactive.")
            false
        }
    }

    /**
     * Heartbeat & Zombie Connection Detection (every 30s ping, 10s timeout)
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                
                val pingId = messageIdGenerator.getAndIncrement()
                val pingRequest = JSONObject().apply {
                    put("id", pingId)
                    put("type", "ping")
                }

                val deferred = CompletableDeferred<JSONObject>()
                pendingRequests[pingId] = deferred

                val sent = sendJson(pingRequest)
                if (sent) {
                    val pong = withTimeoutOrNull(PING_TIMEOUT_MS) { deferred.await() }
                    if (pong == null) {
                        Log.w(TAG, "Ping heartbeat TIMEOUT! Zombie connection detected. Force-closing socket...")
                        pendingRequests.remove(pingId)
                        activeWebSocket?.cancel()
                        scheduleReconnect("Ping heartbeat timeout")
                        break
                    }
                } else {
                    Log.w(TAG, "Failed to send ping heartbeat. Socket inactive.")
                    scheduleReconnect("Failed sending ping")
                    break
                }
            }
        }
    }

    /**
     * Exponential backoff reconnection with random jitter
     */
    private fun scheduleReconnect(reason: String) {
        if (isExplicitlyDisconnected) return
        reconnectJob?.cancel()
        heartbeatJob?.cancel()

        reconnectJob = scope.launch {
            val jitter = Random.nextLong(0, 1000)
            val delayDuration = currentBackoffMs + jitter
            Log.i(TAG, "Scheduling reconnect in ${delayDuration}ms due to: $reason")
            
            delay(delayDuration)
            
            // Double backoff up to max
            currentBackoffMs = (currentBackoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            connectInternal()
        }
    }

    /**
     * Trigger immediate reconnection (e.g. from network callback or UI manual retry)
     */
    fun triggerImmediateReconnect() {
        if (isExplicitlyDisconnected) return
        Log.i(TAG, "Triggering immediate reconnection...")
        currentBackoffMs = MIN_BACKOFF_MS
        reconnectJob?.cancel()
        connectInternal()
    }

    /**
     * Helper to safely serialize and send JSON
     */
    private fun sendJson(json: JSONObject): Boolean {
        val socket = activeWebSocket ?: return false
        return try {
            socket.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Socket send exception: ${e.localizedMessage}", e)
            false
        }
    }

    /**
     * Parses a JSONArray into List<EntityState>
     */
    private fun parseEntityStates(jsonArray: JSONArray): List<EntityState> {
        val list = mutableListOf<EntityState>()
        for (i in 0 until jsonArray.length()) {
            val itemObj = jsonArray.optJSONObject(i) ?: continue
            try {
                val parsed = entityStateAdapter.fromJson(itemObj.toString())
                if (parsed != null) {
                    list.add(parsed)
                }
            } catch (e: Exception) {
                // Ignore single malformed entity and continue
            }
        }
        return list
    }

    /**
     * Android ConnectivityManager.NetworkCallback Integration
     */
    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.i(TAG, "Network became AVAILABLE. Reconnecting if disconnected...")
                        val state = _connectionState.value
                        if (state is HaConnectionState.Disconnected || state is HaConnectionState.Error) {
                            triggerImmediateReconnect()
                        }
                    }

                    override fun onLost(network: Network) {
                        Log.w(TAG, "Network LOST.")
                    }

                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        if (hasInternet) {
                            val state = _connectionState.value
                            if (state is HaConnectionState.Disconnected || state is HaConnectionState.Error) {
                                triggerImmediateReconnect()
                            }
                        }
                    }
                })
                networkCallbackRegistered = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed registering NetworkCallback: ${e.message}", e)
        }
    }
}
