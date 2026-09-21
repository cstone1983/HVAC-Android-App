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
 * - 15-second ping/pong heartbeat with 5-second zombie connection auto-remediation
 * - Exponential backoff with random jitter on disconnects
 * - Android ConnectivityManager network transitions (Wi-Fi/Cellular) auto-reconnection
 */
class HomeAssistantWebSocketManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "HA_WS_Manager"
        private const val PING_INTERVAL_MS = 15_000L
        private const val PING_TIMEOUT_MS = 5_000L
        private const val MIN_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 10_000L
        private const val HOST_FAILOVER_THRESHOLD = 2
        private const val RECONCILE_INTERVAL_MS = 300_000L

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
        .pingInterval(10, TimeUnit.SECONDS) // Transport-level ping: a dead/half-open socket fails within ~10s instead of hanging
        .retryOnConnectionFailure(true)
        .build()

    // Observable states
    private val _connectionState = MutableStateFlow<HaConnectionState>(HaConnectionState.Disconnected)
    val connectionState: StateFlow<HaConnectionState> = _connectionState.asStateFlow()

    private val _states = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    val states: StateFlow<Map<String, EntityState>> = _states.asStateFlow()

    private val _stateUpdates = MutableSharedFlow<EntityState>(extraBufferCapacity = 64)
    val stateUpdates: SharedFlow<EntityState> = _stateUpdates.asSharedFlow()

    private val _usingBackupUrl = MutableStateFlow(false)
    val usingBackupUrl: StateFlow<Boolean> = _usingBackupUrl.asStateFlow()

    // Active session configuration
    @Volatile private var currentRawUrl: String = ""
    @Volatile private var currentToken: String = ""
    @Volatile private var currentWsUrl: String = ""
    @Volatile private var isExplicitlyDisconnected = false

    /**
     * Set when Home Assistant rejects our token, cleared only when fresh credentials arrive.
     *
     * Without it, `auth_invalid` set `canRetry = false` and closed with 4001 -- and then
     * onClosed, which does not special-case that, overwrote the state with Disconnected and
     * scheduled a reconnect anyway. `canRetry` was written twice and read nowhere. The result
     * was a new socket and a fresh failed login roughly every 10s forever: ~8,600 rejected
     * logins per panel per day against HA's auth endpoint, which will trip ip_ban where it is
     * enabled. Because consecutiveHostFailures increments on each one, it also flapped
     * primary/backup every two attempts. The panel showed a generic "Network error" and never
     * said the token was the problem.
     */
    @Volatile private var authRejected = false

    // Primary/backup host failover (independent of the ViewModel's own REST-level failover)
    @Volatile private var primaryRawUrl: String = ""
    @Volatile private var backupRawUrl: String = ""
    @Volatile private var usingBackup: Boolean = false
    private var consecutiveHostFailures = 0

    // WebSocket instance & tracking
    @Volatile private var activeWebSocket: WebSocket? = null
    private val messageIdGenerator = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    
    // Heartbeat & Reconnect Jobs
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var sessionJob: Job? = null
    private var reconcileJob: Job? = null
    private var currentBackoffMs = MIN_BACKOFF_MS
    private var networkCallbackRegistered = false

    // Bumped every time a socket is created or torn down. Each socket's callbacks capture the value
    // they were created with and ignore themselves once it is stale, so a dying old socket can never
    // trigger a reconnect that kills the new, healthy one.
    private val connectionGeneration = AtomicInteger(0)
    @Volatile private var lastConnectStartMs = 0L

    // Entities updated by live events since the last state snapshot was requested.
    private val eventTouchedIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

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
        // New credentials deserve a fresh attempt even after a rejection.
        authRejected = false
        currentBackoffMs = MIN_BACKOFF_MS
        reconnectJob?.cancel()

        Log.i(TAG, "Connecting to Home Assistant WebSocket: $currentWsUrl")
        connectInternal()
    }

    /**
     * Connect with awareness of a backup URL: if the currently active host fails to
     * (re)connect [HOST_FAILOVER_THRESHOLD] times in a row, subsequent reconnect attempts
     * automatically swap to the other host instead of retrying a possibly-dead one forever.
     * Pass a blank/identical [backupUrl] to behave exactly like [connect].
     */
    fun connectWithFailover(primaryUrl: String, backupUrl: String, token: String, preferBackup: Boolean = false) {
        if (primaryUrl.isBlank() || token.isBlank()) {
            Log.w(TAG, "Cannot connect: URL or token is blank")
            return
        }

        // The service, the ViewModel and the Android Auto helper all ask for a connection at startup;
        // don't tear down a connection that is already up (or coming up) with identical settings.
        val effectiveBackup = backupUrl.takeIf { it.isNotBlank() && it != primaryUrl } ?: ""
        val state = _connectionState.value
        val alreadyRunning = !isExplicitlyDisconnected && activeWebSocket != null &&
            primaryRawUrl == primaryUrl && backupRawUrl == effectiveBackup && currentToken == token.trim() &&
            (state is HaConnectionState.Connected || state is HaConnectionState.Authenticating || state is HaConnectionState.Connecting)
        if (alreadyRunning) {
            Log.d(TAG, "Already connected/connecting with identical settings; skipping redundant connect")
            return
        }

        primaryRawUrl = primaryUrl
        backupRawUrl = effectiveBackup
        consecutiveHostFailures = 0
        usingBackup = preferBackup && backupRawUrl.isNotEmpty()
        _usingBackupUrl.value = usingBackup

        val startRawUrl = if (usingBackup) backupRawUrl else primaryRawUrl
        currentToken = token.trim()
        currentRawUrl = startRawUrl
        currentWsUrl = toWebSocketUrl(startRawUrl)
        isExplicitlyDisconnected = false
        // New credentials deserve a fresh attempt even after a rejection.
        authRejected = false
        currentBackoffMs = MIN_BACKOFF_MS
        reconnectJob?.cancel()

        Log.i(TAG, "Connecting to Home Assistant WebSocket (${if (usingBackup) "backup" else "primary"}): $currentWsUrl")
        connectInternal()
    }

    /**
     * Disconnects cleanly and stops auto-reconnect
     */
    fun disconnect() {
        isExplicitlyDisconnected = true
        connectionGeneration.incrementAndGet()
        heartbeatJob?.cancel()
        reconcileJob?.cancel()
        sessionJob?.cancel()
        reconnectJob?.cancel()

        activeWebSocket?.close(1000, "Client initiated clean disconnect")
        activeWebSocket = null
        _connectionState.value = HaConnectionState.Disconnected
        Log.i(TAG, "Clean disconnect executed.")
    }

    private fun connectInternal() {
        if (isExplicitlyDisconnected) return
        if (currentWsUrl.isEmpty() || currentToken.isEmpty()) return

        // Invalidate every callback from any previous socket BEFORE tearing it down.
        val myGen = connectionGeneration.incrementAndGet()
        lastConnectStartMs = System.currentTimeMillis()

        // Clean any existing connection
        heartbeatJob?.cancel()
        reconcileJob?.cancel()
        sessionJob?.cancel()
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
                if (myGen != connectionGeneration.get()) return
                Log.d(TAG, "WebSocket transport opened. Awaiting auth_required...")
                _connectionState.value = HaConnectionState.Authenticating
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (myGen != connectionGeneration.get()) return
                handleIncomingMessage(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closing (code $code): $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (myGen != connectionGeneration.get()) return
                Log.w(TAG, "WebSocket closed (code $code): $reason")
                // authRejected: retrying a token HA has already refused just hammers the
                // auth endpoint. Keep the Error state so the UI keeps saying why.
                if (!isExplicitlyDisconnected && !authRejected) {
                    _connectionState.value = HaConnectionState.Disconnected
                    scheduleReconnect("Socket closed ($code: $reason)")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (myGen != connectionGeneration.get()) return
                Log.e(TAG, "WebSocket failure: ${t.localizedMessage}", t)
                if (!isExplicitlyDisconnected && !authRejected) {
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
                    consecutiveHostFailures = 0 // Current host is healthy again

                    sessionJob?.cancel()
                    sessionJob = scope.launch {
                        // Subscribe FIRST so no change can slip between the snapshot and the subscription,
                        // then load the snapshot and merge it beneath anything already received via events.
                        // If either step fails the connection is useless (connected but frozen), so reconnect.
                        if (!subscribeToStateEvents()) {
                            forceReconnect("subscribe_events failed")
                            return@launch
                        }
                        if (!fetchBaselineStates()) {
                            forceReconnect("get_states failed")
                            return@launch
                        }
                        startHeartbeat()
                        startReconcileLoop()
                    }
                }

                "auth_invalid" -> {
                    val message = json.optString("message", "Invalid access token")
                    Log.e(TAG, "Authentication FAILED: $message")
                    authRejected = true
                    _connectionState.value = HaConnectionState.Error(
                        "Home Assistant rejected the access token. Update it in Settings.",
                        canRetry = false
                    )
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
     * Full state snapshot (get_states), merged UNDER anything newer already received via live events,
     * so the snapshot can never overwrite a fresher value. Also used as a periodic self-healing
     * reconcile. Returns false if the snapshot could not be loaded.
     */
    private suspend fun fetchBaselineStates(): Boolean {
        val id = messageIdGenerator.getAndIncrement()
        val request = JSONObject().apply {
            put("id", id)
            put("type", "get_states")
        }

        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[id] = deferred

        // Events applied from here on may be newer than the snapshot we are about to receive.
        eventTouchedIds.clear()

        if (!sendJson(request)) {
            pendingRequests.remove(id)
            Log.w(TAG, "Failed to request state snapshot: WebSocket inactive.")
            return false
        }

        val response = withTimeoutOrNull(20_000L) { deferred.await() }
        if (response == null || !response.optBoolean("success", false)) {
            pendingRequests.remove(id)
            Log.w(TAG, "Failed to retrieve state snapshot or request timed out.")
            return false
        }

        val resultArray = response.optJSONArray("result") ?: return false
        val baseline = parseEntityStates(resultArray).associateBy { it.entity_id }
        val touched = HashSet(eventTouchedIds)

        _states.update { current ->
            val merged = HashMap(baseline)
            for ((entityId, cur) in current) {
                val base = baseline[entityId]
                val keepCurrent = if (base == null) {
                    // Only in our map: keep it if a live event created it after the snapshot was taken,
                    // otherwise it is a leftover from an older connection and has since been removed.
                    entityId in touched
                } else {
                    // ISO-8601 UTC timestamps compare correctly as strings; ties keep the existing object.
                    (cur.last_updated ?: "") >= (base.last_updated ?: "")
                }
                if (keepCurrent) merged[entityId] = cur
            }
            merged
        }
        Log.i(TAG, "State snapshot merged: ${baseline.size} entities")
        return true
    }

    /**
     * Real-time event subscription (subscribe_events -> state_changed).
     * Returns false if Home Assistant did not confirm the subscription.
     */
    private suspend fun subscribeToStateEvents(): Boolean {
        val id = messageIdGenerator.getAndIncrement()
        val request = JSONObject().apply {
            put("id", id)
            put("type", "subscribe_events")
            put("event_type", "state_changed")
        }

        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[id] = deferred

        if (!sendJson(request)) {
            pendingRequests.remove(id)
            Log.w(TAG, "Failed to send subscribe_events: WebSocket inactive.")
            return false
        }

        val response = withTimeoutOrNull(10_000L) { deferred.await() }
        return if (response != null && response.optBoolean("success", false)) {
            Log.i(TAG, "Successfully subscribed to state_changed events.")
            true
        } else {
            pendingRequests.remove(id)
            Log.w(TAG, "Failed to subscribe to state_changed events.")
            false
        }
    }

    /**
     * Periodically re-syncs the full state (merged, never overwriting newer live data) so that any
     * event that was somehow missed is healed within minutes instead of staying wrong indefinitely.
     */
    private fun startReconcileLoop() {
        reconcileJob?.cancel()
        reconcileJob = scope.launch {
            var failures = 0
            while (isActive) {
                delay(RECONCILE_INTERVAL_MS)
                if (_connectionState.value !is HaConnectionState.Connected) continue
                if (fetchBaselineStates()) {
                    failures = 0
                } else if (++failures >= 2) {
                    forceReconnect("periodic state reconcile failed twice")
                    break
                }
            }
        }
    }

    /**
     * Drops the current socket and reconnects through the normal backoff path.
     */
    private fun forceReconnect(reason: String) {
        if (isExplicitlyDisconnected) return
        Log.w(TAG, "Forcing reconnect: $reason")
        scheduleReconnect(reason)
        activeWebSocket?.cancel()
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
                    eventTouchedIds.add(entityId)
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

        // Every exit from here has to drop the pending entry. The timeout and send-failure
        // branches used to return without removing it, so each one stranded a CompletableDeferred
        // in a map nothing ever sweeps. On a wall panel that runs for months behind flaky wifi,
        // that grows without bound. The other request paths already remove on failure.
        return try {
            if (!sendJson(request)) {
                Log.e(TAG, "Failed sending call_service $domain.$service: WebSocket inactive.")
                return false
            }
            val response = withTimeoutOrNull(10_000L) { deferred.await() }
            if (response == null) {
                Log.w(TAG, "call_service $domain.$service timed out after 10s.")
                return false
            }
            val success = response.optBoolean("success", false)
            if (!success) {
                val err = response.optJSONObject("error")
                Log.e(TAG, "call_service $domain.$service returned error: ${err?.optString("message")}")
            }
            success
        } finally {
            pendingRequests.remove(id)
        }
    }

    /**
     * Heartbeat & Zombie Connection Detection (every 15s ping, 5s timeout)
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
        if (isExplicitlyDisconnected || authRejected) return
        // A reconnect is already pending for this failure (e.g. the socket callback and the heartbeat
        // both report the same dead connection): don't count it twice or reset its delay.
        if (reconnectJob?.isActive == true) return
        heartbeatJob?.cancel()
        reconcileJob?.cancel()
        sessionJob?.cancel()

        consecutiveHostFailures++
        if (backupRawUrl.isNotEmpty() && consecutiveHostFailures >= HOST_FAILOVER_THRESHOLD) {
            usingBackup = !usingBackup
            val nextRawUrl = if (usingBackup) backupRawUrl else primaryRawUrl
            currentRawUrl = nextRawUrl
            currentWsUrl = toWebSocketUrl(nextRawUrl)
            _usingBackupUrl.value = usingBackup
            consecutiveHostFailures = 0
            Log.w(TAG, "Repeated failures on previous host; failing over to ${if (usingBackup) "backup" else "primary"} URL: $currentWsUrl")
        }

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
        // Network callbacks can fire several times in a burst; don't restart an attempt that just began.
        if (System.currentTimeMillis() - lastConnectStartMs < 1_500L) return
        Log.i(TAG, "Triggering immediate reconnection...")
        currentBackoffMs = MIN_BACKOFF_MS
        consecutiveHostFailures = 0
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
