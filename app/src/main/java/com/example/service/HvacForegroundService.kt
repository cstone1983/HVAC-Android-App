package com.example.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.api.HomeAssistantClient
import com.example.api.HomeAssistantWebSocketManager
import com.example.viewmodel.HvacViewModel
import com.example.viewmodel.HvacUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class HvacForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "hvac_foreground_connection"
        const val NOTIFICATION_ID = 54321

        /**
         * The channel a triggered alarm shouts through.
         *
         * A channel’s settings are fixed when it is first created, and Android will not
         * revise them on an app update — so the id carries a version. Changing the sound or
         * the importance means bumping the suffix, otherwise the existing channel quietly
         * keeps its old behaviour and the change appears to do nothing at all.
         */
        const val ALARM_CHANNEL_ID = "hvac_alarm_triggered_v2"
        const val ALARM_NOTIFICATION_ID = 54322

        /** The quiet channel: armed, disarmed, and whatever gets added after. */
        const val ALARM_STATUS_CHANNEL_ID = "hvac_alarm_status_v1"
        const val ALARM_STATUS_NOTIFICATION_ID = 54323

        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_HEAT = "com.example.action.HEAT"
        const val ACTION_COOL = "com.example.action.COOL"
        const val ACTION_OFF = "com.example.action.OFF"
        const val ACTION_DISMISS = "com.example.action.DISMISS"

        /**
         * How often the last-known readings are written to disk.
         *
         * They are a cold-start cache for the notification, read once at launch. Writing
         * them on every state snapshot meant a disk write for every batch of Home
         * Assistant events.
         */
        private const val PERSIST_INTERVAL_MS = 60_000L

        fun startService(context: Context) {
            val intent = Intent(context, HvacForegroundService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("HvacForegroundService", "startService failed", e)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, HvacForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e("HvacForegroundService", "stopService failed", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollJob: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                startForegroundNotification()
                acquireWifiLock()
                startPollingLoop()
            }
            ACTION_STOP -> {
                releaseWifiLock()
                stopForeground(true)
                stopSelf()
            }
            ACTION_HEAT -> {
                triggerHvacMode("heat")
            }
            ACTION_COOL -> {
                triggerHvacMode("cool")
            }
            ACTION_OFF -> {
                triggerHvacMode("off")
            }
            ACTION_DISMISS -> {
                scheduleRestartInFiveMinutes()
                releaseWifiLock()
                stopForeground(true)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Android 15 gives some foreground service types a time budget, and kills the app outright if
     * the service is still running when it expires — `ForegroundServiceDidNotStopInTimeException`,
     * thrown on the main thread, which is a crash and not something the app can catch.
     *
     * That is what was happening: the manifest declared `dataSync`, which is capped at six hours
     * in any 24, and this service is meant to run indefinitely. It was reached on both the phone
     * and the tablets. The type is now `connectedDevice`, which is not capped, but this stays as
     * the backstop: if the system ever does time us out, shut down cleanly and come back in five
     * minutes rather than being killed.
     *
     * Two-argument overload, added in API 35. The one-argument form (API 34) only ever applies to
     * `shortService`, which this is not.
     */
    @androidx.annotation.RequiresApi(35)
    override fun onTimeout(startId: Int, fgsType: Int) {
        android.util.Log.w(
            "HvacForegroundService",
            "Foreground service timed out (type=$fgsType); stopping cleanly and restarting in 5 min."
        )
        scheduleRestartInFiveMinutes()
        releaseWifiLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        pollJob?.cancel()
        serviceScope.coroutineContext[Job]?.cancelChildren()
        releaseWifiLock()
        // Nothing else can stop it once this service is gone, and a siren with no owner is
        // worse than one that ends early. START_STICKY brings the service back, and it will
        // start sounding again if the panel is still triggered.
        AlarmSiren.stop(this)
        AlarmBeeper.release()
        cancelAlarmTriggeredNotification()
        super.onDestroy()
    }

    /**
     * Keeps the WiFi radio at full performance for the life of the service, so Android's
     * power-save state transitions don't stall or drop the persistent HA WebSocket while
     * the screen is on but the radio would otherwise idle down.
     */
    private fun acquireWifiLock() {
        try {
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "HvacDeck:WifiHighPerfLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            wifiLock?.let {
                if (!it.isHeld) {
                    it.acquire()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("HvacForegroundService", "Could not acquire WifiLock on this device", e)
        }
    }

    private fun releaseWifiLock() {
        try {
            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("HvacForegroundService", "Error releasing WifiLock", e)
        }
    }

    private fun startForegroundNotification() {
        try {
            val notification = buildHvacNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Must match android:foregroundServiceType in the manifest, or startForeground
                // throws. Both are `connectedDevice` now: `dataSync` carries a six-hour daily cap
                // on Android 15 and this service is meant to stay up, which is what was killing
                // the app. See onTimeout above.
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("HvacForegroundService", "Failed to start foreground service, posting fallback notification", e)
            try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildHvacNotification())
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    /** One zone as the notification shows it. */
    private data class NotificationZone(
        val name: String,
        val temp: Double?,
        val mode: String,
        val offSchedule: Boolean
    )

    /**
     * The ongoing notification: a small copy of the panel.
     *
     * Collapsed, one line of temperatures and one of the house. Expanded, the six zones in the
     * panel's colours — amber heating, blue cooling, purple drying, a red edge when a zone is off
     * its schedule — then energy and hot water, then Heat / Cool / Off for the whole house with
     * the current mode lit. Chose 2026-10-17 from three mockups (option C). Alarm arming is not
     * here: Away and Night are automatic.
     *
     * The big number is the room sensor, as on the panel, never the head's return-air reading.
     * Everything comes from the running view model; the zones are also cached, so a service that
     * restarted before the app has loaded still shows the last grid rather than nothing.
     *
     * Built for the Fold. The wall panels show it too — the service needs a notification — but
     * nobody pulls their shade down.
     */
    private fun buildHvacNotification(): Notification {
        val prefs = getSharedPreferences("hvac_settings", Context.MODE_PRIVATE)
        val viewModel = HvacViewModel.getInstance()
        val uiState = viewModel?.uiState?.value as? HvacUiState.Success
        val config = viewModel?.getActiveLayoutConfig()
        val states = viewModel?.entityStates?.value.orEmpty()

        val zones = uiState?.zones?.map { z ->
            NotificationZone(
                name = z.name,
                temp = z.roomTemp ?: z.headTemp,
                mode = z.currentHvacMode.lowercase(Locale.US),
                offSchedule = !z.autoOn || z.overrideOn
            )
        }?.also { cacheZones(prefs, it) } ?: cachedZones(prefs)

        val outdoor = config?.homeStatus?.outdoorTempEntityId
            ?.let { states[it]?.state?.toDoubleOrNull() }
            ?: prefs.getFloat("last_known_outdoor_temp", -1f).takeIf { it != -1f }?.toDouble()

        val houseMode = (uiState?.globalSettings?.globalHvacMode
            ?: prefs.getString("last_known_hvac_mode", null))?.lowercase(Locale.US)

        val alarm = viewModel?.alarmState?.value
        val alarmLabel = when (alarm?.phase) {
            com.example.model.AlarmPhase.DISARMED -> "Disarmed"
            com.example.model.AlarmPhase.ARMING -> "Arming"
            com.example.model.AlarmPhase.PENDING -> "Entry delay"
            com.example.model.AlarmPhase.TRIGGERED -> "Alarm triggered"
            com.example.model.AlarmPhase.ARMED -> alarm.armMode
                ?.removePrefix("armed_")?.replace('_', ' ')
                ?.replaceFirstChar { it.uppercase() }
                ?.let { "Armed $it" } ?: "Armed"
            else -> null
        }

        val presence = config?.homeStatus?.presenceEntityIds.orEmpty()
        val peopleHome = if (viewModel == null || presence.isEmpty()) null
            else presence.count { states[it]?.state == "home" }

        val tiles = viewModel?.solarToday?.value?.response?.tiles.orEmpty()
        fun soFar(name: String): String? =
            tiles.firstOrNull { it.id == "so_far" }?.items?.firstOrNull { it.name == name }?.value
        val made = soFar("Made")
        val used = soFar("Used")

        val hotWater = uiState?.globalSettings?.waterHeaterFullness
            ?: prefs.getFloat("last_known_hot_water_fullness", -1f).takeIf { it >= 0f }?.toDouble()

        fun deg(t: Double?) = t?.let { "${Math.round(it)}°" } ?: "--°"
        val temps = zones.mapNotNull { it.temp?.let { t -> Math.round(t) } }
        val inside = when {
            temps.isEmpty() -> "--°"
            temps.min() == temps.max() -> "${temps.min()}°"
            else -> "${temps.min()}–${temps.max()}°"
        }
        val title = "$inside inside · ${deg(outdoor)} outside"
        val text = listOfNotNull(
            peopleHome?.let { "$it home" },
            made?.let { "solar $it kWh today" },
            hotWater?.let { "hot water ${it.toInt()}%" }
        ).joinToString(" · ").ifBlank { "Connecting to Home Assistant" }
        val summary = listOfNotNull(
            if (made != null || used != null) "Made ${made ?: "--"} · used ${used ?: "--"} kWh" else null,
            hotWater?.let { "hot water ${it.toInt()}%" },
            peopleHome?.let { "$it home" }
        ).joinToString(" · ")

        val collapsed = android.widget.RemoteViews(packageName, com.example.R.layout.notification_hvac_collapsed).apply {
            setTextViewText(com.example.R.id.n_title, title)
            setTextViewText(com.example.R.id.n_text, text)
        }

        val big = android.widget.RemoteViews(packageName, com.example.R.layout.notification_hvac_expanded)
        big.setTextViewText(com.example.R.id.n_title, title)
        val cellIds = intArrayOf(com.example.R.id.nz0, com.example.R.id.nz1, com.example.R.id.nz2,
            com.example.R.id.nz3, com.example.R.id.nz4, com.example.R.id.nz5)
        val nameIds = intArrayOf(com.example.R.id.nz0_name, com.example.R.id.nz1_name, com.example.R.id.nz2_name,
            com.example.R.id.nz3_name, com.example.R.id.nz4_name, com.example.R.id.nz5_name)
        val tempIds = intArrayOf(com.example.R.id.nz0_temp, com.example.R.id.nz1_temp, com.example.R.id.nz2_temp,
            com.example.R.id.nz3_temp, com.example.R.id.nz4_temp, com.example.R.id.nz5_temp)
        for (i in cellIds.indices) {
            val zone = zones.getOrNull(i)
            if (zone == null) {
                // Keeps its share of the row, so a missing zone never stretches the others.
                big.setInt(cellIds[i], "setVisibility", android.view.View.INVISIBLE)
                continue
            }
            big.setInt(cellIds[i], "setVisibility", android.view.View.VISIBLE)
            big.setTextViewText(nameIds[i], zone.name)
            big.setTextViewText(tempIds[i], deg(zone.temp))
            big.setInt(cellIds[i], "setBackgroundResource", cellBackground(zone))
        }
        big.setTextViewText(com.example.R.id.n_summary, summary)
        big.setViewVisibility(
            com.example.R.id.n_summary,
            if (summary.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        )

        fun modeIntent(action: String, code: Int) = PendingIntent.getService(
            this, code,
            Intent(this, HvacForegroundService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        big.setOnClickPendingIntent(com.example.R.id.n_btn_heat, modeIntent(ACTION_HEAT, 101))
        big.setOnClickPendingIntent(com.example.R.id.n_btn_cool, modeIntent(ACTION_COOL, 102))
        big.setOnClickPendingIntent(com.example.R.id.n_btn_off, modeIntent(ACTION_OFF, 103))
        // The current house mode is lit, so the buttons double as the readout.
        big.setInt(com.example.R.id.n_btn_heat, "setBackgroundResource",
            if (houseMode == "heat") com.example.R.drawable.nc_btn_heat else com.example.R.drawable.nc_btn)
        big.setInt(com.example.R.id.n_btn_cool, "setBackgroundResource",
            if (houseMode == "cool") com.example.R.drawable.nc_btn_cool else com.example.R.drawable.nc_btn)
        big.setInt(com.example.R.id.n_btn_off, "setBackgroundResource",
            if (houseMode == "off") com.example.R.drawable.nc_btn_off else com.example.R.drawable.nc_btn)

        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            // Plain title and text too: they are what the lock screen, a watch and the
            // accessibility services read, and the fallback anywhere custom views are not drawn.
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(alarmLabel)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(big)
            .setSmallIcon(com.example.R.drawable.ic_climate_zone)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingOpenApp)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Swiping it away still parks the service for five minutes, as before; the
            // separate DISMISS (5M) button went with the old layout.
            .setDeleteIntent(modeIntent(ACTION_DISMISS, 104))
            .build()
    }

    private fun cellBackground(zone: NotificationZone): Int = when (zone.mode) {
        "heat" -> if (zone.offSchedule) com.example.R.drawable.nc_cell_heat_hold else com.example.R.drawable.nc_cell_heat
        "cool" -> if (zone.offSchedule) com.example.R.drawable.nc_cell_cool_hold else com.example.R.drawable.nc_cell_cool
        "dry" -> if (zone.offSchedule) com.example.R.drawable.nc_cell_dry_hold else com.example.R.drawable.nc_cell_dry
        else -> if (zone.offSchedule) com.example.R.drawable.nc_cell_plain_hold else com.example.R.drawable.nc_cell_plain
    }

    private fun cacheZones(prefs: android.content.SharedPreferences, zones: List<NotificationZone>) {
        val packed = zones.joinToString("\n") { z ->
            listOf(z.name.replace('\t', ' '), z.temp?.toString() ?: "", z.mode, z.offSchedule.toString())
                .joinToString("\t")
        }
        if (prefs.getString("notification_zones", null) != packed) {
            prefs.edit().putString("notification_zones", packed).apply()
        }
    }

    private fun cachedZones(prefs: android.content.SharedPreferences): List<NotificationZone> =
        prefs.getString("notification_zones", null).orEmpty().lines().mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 4) null
            else NotificationZone(f[0], f[1].toDoubleOrNull(), f[2], f[3].toBoolean())
        }

    private fun startPollingLoop() {
        pollJob?.cancel()
        pollJob = serviceScope.launch {
            val sharedPrefs = getSharedPreferences("hvac_settings", Context.MODE_PRIVATE)
            val wsManager = HomeAssistantWebSocketManager.getInstance(applicationContext)

            // Resolve from BuildConfig first, then the saved preference.
            //
            // This was `sharedPrefs.getString("ha_token", "") ?: ""`, the same dead-fallback shape
            // that stopped the panel's WebSocket ever connecting: getString with a non-null
            // default never returns null, so nothing fell through to BuildConfig. Neither
            // `ha_token` nor `ha_url` is written to preferences on a normal install — verified on
            // all three devices — so both were empty, the `isNotEmpty()` guard below failed, and
            // this service has never once opened a connection or kept one alive. It still posted
            // its notification, so it looked like it was working.
            val buildUrl = try { com.example.BuildConfig.HA_URL } catch (e: Exception) { "" }
            val buildToken = try { com.example.BuildConfig.HA_TOKEN } catch (e: Exception) { "" }
            val url = buildUrl.takeIf { it.isNotEmpty() && it != "https://localhost/" }
                ?: sharedPrefs.getString("ha_url", null).orEmpty()
            val backupUrl = try { com.example.BuildConfig.HA_BACKUP_URL } catch (e: Exception) { "" }
                .takeIf { it.isNotEmpty() }
                ?: sharedPrefs.getString("backup_ha_url", null).orEmpty()
            val token = buildToken.takeIf { it.isNotEmpty() && it != "YOUR_HOME_ASSISTANT_TOKEN" }
                ?: sharedPrefs.getString("ha_token", null).orEmpty()

            if (url.isNotEmpty() && token.isNotEmpty()) {
                HomeAssistantClient.initialize(url, token)
                wsManager.connectWithFailover(url, backupUrl, token)
            }

            // Sound the siren when the panel goes off.
            //
            // This belongs here rather than in the UI, and it used to be in the UI. A Compose effect
            // only runs while the activity is composed, so a panel whose screen had been torn down
            // never sounded and never logged a reason. The service has no such dependency: it is
            // already running, already holds this WebSocket, and survives the screen going away.
            //
            // Only on TRIGGERED, never on the entry delay — those sixty seconds belong to whoever
            // just walked in to disarm, and a siren over the top of that is noise while they type.
            //
            // Edge-triggered. states emits on every batch of Home Assistant events, and reacting to
            // the level rather than the change would re-issue stop() hundreds of times an hour,
            // cancelling the vibrator on each one. The preference is re-read inside the loop rather
            // than captured, so turning the siren off in settings takes effect mid-alarm instead of
            // at the next service restart.
            launch {
                var lastState: String? = null
                var beepJob: Job? = null
                wsManager.states.collect { statesMap ->
                    val alarmCfg = HvacViewModel.getInstance()?.getActiveLayoutConfig()?.alarm
                    val alarmId = alarmCfg?.entityId ?: "alarm_control_panel.alarmo"
                    // One decision, read once: wall panel or phone. Everything below follows from it
                    // rather than from its own separate flag.
                    val mode = com.example.model.DeviceMode.resolve(
                        stored = sharedPrefs.getString("device_mode", null),
                        legacySirenEnabled = if (sharedPrefs.contains("alarm_siren_enabled"))
                            sharedPrefs.getBoolean("alarm_siren_enabled", false) else null,
                        isWallPanel = AlarmSiren.isWallPanel(this@HvacForegroundService)
                    )
                    val notifyEnabled = alarmCfg?.notifyOnTrigger != false && mode.notifiesOnTrigger
                    
                    // Keep the shared clock fed. Throttled inside HaClock, so this costs nothing on
                    // most emissions.
                    HaClock.sampleFrom(statesMap)
                    
                    val node = statesMap[alarmId]
                    val state = node?.state
                    if (state == lastState) return@collect
                    val previousState = lastState
                    lastState = state
                    
                    // The siren is gated per device; the notification is not. A phone kept
                    // deliberately silent should still light up and buzz.
                    if (state == "triggered") {
                        if (mode.soundsSiren) AlarmSiren.start(this@HvacForegroundService)
                        if (notifyEnabled) postAlarmTriggeredNotification()
                    } else {
                        AlarmSiren.stop(this@HvacForegroundService)
                        cancelAlarmTriggeredNotification()
                    }

                    // The quiet ones. Skipped on the first state seen after a restart: that is this
                    // device catching up rather than the house changing, and announcing "alarm armed"
                    // every time the app reconnects would teach you to ignore it.
                    if (mode.showsStatusNotifications && previousState != null) {
                        val event = alarmEventKey(state)
                        val wanted = alarmCfg?.notifyEvents ?: listOf("armed")
                        if (event != null && event != "triggered" &&
                            wanted.any { it.equals(event, ignoreCase = true) }
                        ) {
                            postAlarmStatusNotification(event, state, node)
                        }
                    }
                    
                    // The countdown tick. Both modes do this — see DeviceMode.beepsCountdown.
                    //
                    // Entry by default, exit on request: beeping every time the house is armed
                    // from the sofa wears thin quickly, whereas the tick on the way in is the
                    // one that stops a false alarm.
                    val beepNow = mode.beepsCountdown && when (state) {
                        "pending" -> alarmCfg?.entryBeep != false
                        "arming" -> alarmCfg?.exitBeep == true
                        else -> false
                    }
                    // cancelAndJoin, not cancel: the old loop releases the shared AudioTrack
                    // in its finally block, and letting that race a newly started loop would
                    // release the track out from under it.
                    beepJob?.cancelAndJoin()
                    beepJob = null
                    if (beepNow) {
                        // Alarmo reports the running delay on the entity; the configured value
                        // is the fallback for a panel that does not.
                        val total = node?.getDoubleAttribute("delay")?.toInt()
                            ?: alarmCfg?.entryDelaySeconds
                        // When the countdown started according to Home Assistant, not according
                        // to this device. Both panels get the same figure, which is what keeps
                        // them in the same speed tier — see runCountdownBeeps.
                        val startedAt = com.example.ui.parseHaTimestamp(
                            node?.last_changed ?: node?.last_updated
                        )
                        beepJob = launch { runCountdownBeeps(total, startedAt) }
                    }
                }
            }

            // Real-time state collector via WebSocket StateFlow.
            //
            // Throttled. These preferences exist only so the notification and a cold start have
            // something to show before the first fetch completes, but this collector ran on every
            // snapshot and ended with editor.apply() — a disk write per batch of Home Assistant
            // events, all day, for values nothing reads until the next launch. Once a minute is
            // ample for a cache of last-known readings, and it takes the service off the hot path
            // entirely.
            launch {
                var lastPersistMs = 0L
                wsManager.states.collect { statesMap ->
                    val nowMs = System.currentTimeMillis()
                    if (statesMap.isNotEmpty() && nowMs - lastPersistMs >= PERSIST_INTERVAL_MS) {
                        lastPersistMs = nowMs
                        val editor = sharedPrefs.edit()

                        val globalMode = statesMap["input_select.global_hvac_mode"]?.state ?: "heat"
                        editor.putString("last_known_hvac_mode", globalMode)

                        // Living room temp
                        val livingRoomClimate = statesMap["climate.hp_living_room"]
                        val parsedLivingTemp = livingRoomClimate?.getDoubleAttribute("current_temperature")
                            ?: statesMap["sensor.living_room_temperature"]?.state?.toDoubleOrNull()
                        if (parsedLivingTemp != null) {
                            editor.putFloat("last_known_living_temp", parsedLivingTemp.toFloat())
                            editor.putFloat("last_known_indoor_temp", parsedLivingTemp.toFloat())
                        }

                        // Bedroom 2 temp
                        val bed2Climate = statesMap["climate.hp_bedroom_2"]
                        val parsedBed2Temp = bed2Climate?.getDoubleAttribute("current_temperature")
                            ?: statesMap["sensor.bedroom_2_temperature"]?.state?.toDoubleOrNull()
                        if (parsedBed2Temp != null) {
                            editor.putFloat("last_known_bedroom2_temp", parsedBed2Temp.toFloat())
                        }

                        // Outside temp. sensor.outdoor_temperature and sensor.outside_temperature
                        // do not exist here; the configured sensor is the one the panel uses.
                        val outdoorId = HvacViewModel.getInstance()
                            ?.getActiveLayoutConfig()?.homeStatus?.outdoorTempEntityId
                        val outdoorTemp = outdoorId?.let { statesMap[it]?.state?.toDoubleOrNull() }
                            ?: statesMap["weather.home"]?.getDoubleAttribute("temperature")
                            ?: statesMap["weather.forecast_home"]?.getDoubleAttribute("temperature")
                        if (outdoorTemp != null) {
                            editor.putFloat("last_known_outdoor_temp", outdoorTemp.toFloat())
                        }

                        // Pool temp
                        val poolTemp = statesMap["sensor.my_pool_water_temperature"]?.state?.toDoubleOrNull()
                            ?: statesMap["sensor.pool_water_temperature"]?.state?.toDoubleOrNull()
                            ?: statesMap["sensor.pool_temperature"]?.state?.toDoubleOrNull()
                        if (poolTemp != null) {
                            editor.putFloat("last_known_pool_temp", poolTemp.toFloat())
                        }

                        // Solar production, summed across both phases. The Envoy and
                        // sensor.solar_power ids previously read here do not exist, so this line
                        // never populated. The phase sensors report kW directly.
                        val solarCfg = HvacViewModel.getInstance()?.getActiveLayoutConfig()?.solarSensors
                        val phaseA = solarCfg?.phaseAPowerEntityId?.let { statesMap[it]?.state?.toDoubleOrNull() }
                        val phaseB = solarCfg?.phaseBPowerEntityId?.let { statesMap[it]?.state?.toDoubleOrNull() }
                        if (phaseA != null || phaseB != null) {
                            editor.putFloat("last_known_solar_kw", ((phaseA ?: 0.0) + (phaseB ?: 0.0)).toFloat())
                        }

                        // Hot water
                        val hwFullness = statesMap["sensor.heat_pump_water_heater_available_hot_water"]?.state?.toDoubleOrNull()
                        if (hwFullness != null) {
                            editor.putFloat("last_known_hot_water_fullness", hwFullness.toFloat())
                        }

                        // water_heater.my_water_heater does not exist; the real entity is the one
                        // the car module already reads.
                        val hwTemp = statesMap["water_heater.heat_pump_water_heater"]?.getDoubleAttribute("current_temperature")
                        if (hwTemp != null) {
                            editor.putFloat("last_known_hot_water_temp", hwTemp.toFloat())
                        }

                        // Master 1. climate.hp_master_bedroom does not exist; the head is
                        // climate.hp_bedroom, which the config calls bedroom_1.
                        val masterTemp = statesMap["climate.hp_bedroom"]?.getDoubleAttribute("current_temperature")
                        if (masterTemp != null) {
                            editor.putFloat("last_known_master_temp", masterTemp.toFloat())
                        }

                        val basementTemp = statesMap["climate.hp_basement"]?.getDoubleAttribute("current_temperature")
                        if (basementTemp != null) {
                            editor.putFloat("last_known_basement_temp", basementTemp.toFloat())
                        }

                        editor.apply()
                        updateNotification()
                    }
                }
            }

            // Fallback baseline sync or periodic weather update check
            var minutes = 0
            while (isActive) {
                try {
                    // The notification shows today's energy, and only the home screen polls
                    // Solar Calc — so with the app closed the figures went stale. Every five
                    // minutes is plenty for counters that move a few kWh an hour.
                    if (minutes++ % 5 == 0) {
                        try {
                            HvacViewModel.getInstance()?.fetchSolarToday()
                        } catch (e: Exception) {
                            android.util.Log.w("HvacForegroundService", "Solar refresh failed", e)
                        }
                    }
                    // Refresh the cached outdoor reading from the real sensor. This used to write
                    // the day's forecast HIGH over the cache every minute, so even the fallback
                    // value was wrong.
                    val activeViewModel = HvacViewModel.getInstance()
                    val outdoorId = activeViewModel?.getActiveLayoutConfig()?.homeStatus?.outdoorTempEntityId
                    val liveOutdoor = outdoorId?.let {
                        activeViewModel.entityStates.value[it]?.state?.toFloatOrNull()
                    }
                    if (liveOutdoor != null) {
                        sharedPrefs.edit().putFloat("last_known_outdoor_temp", liveOutdoor).apply()
                    }
                    updateNotification()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(60_000L) // Quiet fallback check every minute
            }
        }
    }

    private fun updateNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildHvacNotification())
        } catch (e: Exception) {
            android.util.Log.e("HvacForegroundService", "updateNotification failed", e)
        }
    }

    private fun triggerHvacMode(mode: String) {
        serviceScope.launch {
            try {
                val sharedPrefs = getSharedPreferences("hvac_settings", Context.MODE_PRIVATE)
                val url = sharedPrefs.getString("ha_url", "") ?: ""
                val token = sharedPrefs.getString("ha_token", "") ?: ""

                if (url.isNotEmpty() && token.isNotEmpty()) {
                    val wsManager = HomeAssistantWebSocketManager.getInstance(applicationContext)
                    val success = wsManager.callService(
                        domain = "input_select",
                        service = "select_option",
                        serviceData = mapOf("option" to mode),
                        target = mapOf("entity_id" to "input_select.global_hvac_mode")
                    )

                    if (!success) {
                        HomeAssistantClient.initialize(url, token)
                        HomeAssistantClient.service.callService(
                            "input_select",
                            "select_option",
                            mapOf(
                                "entity_id" to "input_select.global_hvac_mode",
                                "option" to mode
                            )
                        )
                    }

                    sharedPrefs.edit().putString("last_known_hvac_mode", mode).apply()
                }
                updateNotification()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun scheduleRestartInFiveMinutes() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, HvacForegroundService::class.java).apply {
                action = ACTION_START
            }
            val pendingIntent = PendingIntent.getService(
                this,
                2468,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerTime = System.currentTimeMillis() + 5 * 60 * 1000 // 5 minutes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("HvacForegroundService", "Failed to schedule restart", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "HVAC Background Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps connection with smart home HVAC server alive and provides fast actions deck."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
            // 25.0 shipped a v1 of the alarm channel carrying the device default alarm sound.
            // A channel is frozen at creation, so correcting the sound required a new id, and
            // leaving the old one behind would list two identical-looking Alarm triggered
            // entries in notification settings, one of which does nothing.
            manager.deleteNotificationChannel("hvac_alarm_triggered_v1")
            manager.createNotificationChannel(buildAlarmChannel())
            manager.createNotificationChannel(buildAlarmStatusChannel())
        }
    }

    /**
     * Nothing like the HVAC channel, deliberately.
     *
     * That one is IMPORTANCE_LOW and silent by design — a status line that sits in the shade
     * all day. This one has to reach a phone that is face-down in another room, on silent,
     * inside Do Not Disturb. Three things get it there:
     *
     *  - **An alarm-usage sound.** USAGE_ALARM is the one stream Do Not Disturb lets through
     *    by default, which is why the wall panels sound on a tablet that has DND on. The same
     *    sound with ordinary notification usage would simply be swallowed.
     *  - **setBypassDnd**, for the case where alarms have been excluded from DND as well.
     *    This needs Notification Policy Access, which only the user can grant in system
     *    settings. It does not throw without it — it is silently ignored — so it cannot be
     *    relied on alone, and the alarm-usage sound is what does the work in the normal case.
     *  - **IMPORTANCE_HIGH**, so it arrives as a heads-up rather than a line found later.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun buildAlarmChannel(): NotificationChannel =
        NotificationChannel(
            ALARM_CHANNEL_ID,
            "Alarm triggered",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Sounds when the house alarm goes off, including through Do Not Disturb."
            // An alert, deliberately not the siren.
            //
            // This channel fires on every device, so on a wall panel it plays over the top
            // of the real siren — two wails at once is mud. Short hard two-tone beeps cut
            // through the siren instead of fighting it, and on the phone, where there is no
            // siren, they still read immediately as something wrong rather than as a
            // message arriving.
            //
            // Not the device default alarm either: on this phone that resolves to "Morning
            // Xylophone", the gentle wake-up tone that made the panels sound comical.
            //
            // Rendered by tools/make_alert_wav.py. A channel sound has to be a Uri to a
            // real file, so it cannot share the generated AudioTrack the panels use.
            setSound(
                Uri.parse("android.resource://" + packageName + "/" + com.example.R.raw.alarm_alert),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }

    /**
     * Posted on every device, phone included, whether or not this one sounds a siren.
     *
     * The siren is a wall-panel thing — a phone that becomes a siren in a meeting gets the
     * whole feature switched off. But a phone in a pocket is the device most likely to be
     * *with* whoever needs to know, so it gets this instead: an alarm-stream sound, a
     * heads-up, and a full-screen intent that opens straight to the keypad the way an
     * incoming call would.
     */
    private fun postAlarmTriggeredNotification() {
        try {
            val openKeypad = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                this,
                1,
                openKeypad,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("ALARM TRIGGERED")
                .setContentText("Tap to disarm")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // Not swipeable away, and it does not clear itself on tap: it is withdrawn
                // when the panel actually leaves triggered, not when somebody notices it.
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pending)
                .setFullScreenIntent(pending, true)
                .build()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(ALARM_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // POST_NOTIFICATIONS can be refused on Android 13+. The siren does not depend on
            // this, so losing the notification must not take the noise down with it.
            android.util.Log.w("HvacForegroundService", "Could not post the alarm notification", e)
        }
    }

    /**
     * Tick while the panel waits for a code, faster as the time runs out.
     *
     * The acceleration is the information: it says how long is left without asking anyone to
     * find a screen, at exactly the moment they are least inclined to go looking for one.
     *
     * Ends on its own when the countdown reaches zero, and is cancelled by the collector the
     * moment the panel leaves the delay — disarmed, or triggered and the siren takes over.
     * The track is released in a finally so a cancellation cannot strand it.
     *
     * ### Keeping two panels together
     *
     * Every time here is [HaClock] time, not device time, and the countdown is measured from
     * Home Assistant’s own stamp on the state change rather than from the moment this panel
     * happened to see it.
     *
     * Both matter, and the second is the one that was missed first time round. Pinning the
     * beats to a shared grid is not enough on its own: if one panel sees the state change a
     * few hundred milliseconds after the other, the two disagree about how many seconds are
     * left, and near a tier boundary one ends up ticking twice as fast as the other. Grid
     * alignment cannot rescue that — they are not even trying to play the same rhythm.
     * Measuring from the server’s timestamp makes the disagreement impossible.
     */
    private suspend fun runCountdownBeeps(totalSeconds: Int?, startedAtServerMs: Long?) {
        val startMs = startedAtServerMs ?: HaClock.nowMs()
        try {
            while (true) {
                val elapsed = ((HaClock.nowMs() - startMs) / 1000L).toInt()
                val remaining = totalSeconds?.let { (it - elapsed).coerceAtLeast(0) }
                if (remaining != null && remaining <= 0) break
                // Wait for the shared beat, then pip — rather than pipping immediately and
                // waiting afterwards, which would put the first beep wherever this device
                // happened to start and leave the panels out of step from the opening tick.
                delay(
                    AlarmBeeper.millisUntilNextSlot(
                        HaClock.nowMs(),
                        AlarmBeeper.intervalMsFor(remaining)
                    )
                )
                AlarmBeeper.beep(this)
            }
        } finally {
            AlarmBeeper.release()
        }
    }

    /**
     * The panel’s state, reduced to the key used in configuration.
     *
     * Every armed mode collapses to "armed". Someone asking to be told when the house arms
     * means exactly that, and would not thank you for having to list armed_away, armed_home,
     * armed_night and armed_vacation separately — nor for silence when a mode they had not
     * thought of turns up.
     */
    private fun alarmEventKey(rawState: String?): String? = when {
        rawState.isNullOrBlank() -> null
        rawState.startsWith("armed_") -> "armed"
        else -> rawState
    }

    /**
     * Nothing like the triggered notification: quiet, dismissable, and replaced rather than
     * stacked.
     *
     * One id for all of them, so arming and then disarming leaves one current line rather
     * than a history of the day sitting in the shade. The state of the alarm is a single
     * fact, and the notification should read as that fact rather than as a log.
     */
    private fun postAlarmStatusNotification(
        event: String,
        rawState: String?,
        node: com.example.api.EntityState?
    ) {
        try {
            val title = when (event) {
                "armed" -> "Alarm armed"
                "disarmed" -> "Alarm disarmed"
                "arming" -> "Alarm arming"
                "pending" -> "Entry delay running"
                else -> "Alarm " + event
            }
            val armedMode = rawState?.takeIf { event == "armed" }
                ?.removePrefix("armed_")
                ?.replace('_', ' ')
                ?.replaceFirstChar { it.uppercase() }
            val who = node?.getStringAttribute("changed_by")?.takeIf { it.isNotBlank() }
            val detail = listOfNotNull(armedMode, who?.let { "by " + it })
                .joinToString(" · ")

            val open = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                this,
                2,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, ALARM_STATUS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle(title)
                .setContentText(detail.ifBlank { "Alarm state changed" })
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(ALARM_STATUS_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            android.util.Log.w(
                "HvacForegroundService",
                "Could not post the alarm status notification",
                e
            )
        }
    }

    /**
     * Ordinary importance and the device’s own notification sound.
     *
     * Pointedly not the alarm channel’s treatment: no USAGE_ALARM, no Do Not Disturb bypass,
     * no full-screen intent. Knowing the house armed itself is worth a glance, not worth
     * overriding a silenced phone for — and a status line that shouts is one that gets the
     * whole app muted, which would take the notification that does matter down with it.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun buildAlarmStatusChannel(): NotificationChannel =
        NotificationChannel(
            ALARM_STATUS_CHANNEL_ID,
            "Alarm activity",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Armed, disarmed and other alarm status changes."
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

    private fun cancelAlarmTriggeredNotification() {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(ALARM_NOTIFICATION_ID)
        } catch (e: Exception) {
            android.util.Log.w("HvacForegroundService", "Could not cancel the alarm notification", e)
        }
    }
}
