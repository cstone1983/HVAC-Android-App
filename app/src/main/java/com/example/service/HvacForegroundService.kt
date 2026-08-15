package com.example.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class HvacForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "hvac_foreground_connection"
        const val NOTIFICATION_ID = 54321

        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_HEAT = "com.example.action.HEAT"
        const val ACTION_COOL = "com.example.action.COOL"
        const val ACTION_OFF = "com.example.action.OFF"
        const val ACTION_DISMISS = "com.example.action.DISMISS"

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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                startForegroundNotification()
                startPollingLoop()
            }
            ACTION_STOP -> {
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
                stopForeground(true)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        try {
            val notification = buildHvacNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
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

    private fun formatTemp(temp: Double?): String {
        return if (temp != null && temp > -50.0 && temp < 200.0) {
            "${Math.round(temp)}°F"
        } else {
            "--"
        }
    }

    private fun buildHvacNotification(): Notification {
        val sharedPrefs = getSharedPreferences("hvac_settings", Context.MODE_PRIVATE)
        val viewModel = HvacViewModel.getInstance()
        val uiState = viewModel?.uiState?.value

        // 1. Living Room Temp
        var livingRoomTemp: Double? = null
        if (uiState is HvacUiState.Success) {
            val lrZone = uiState.zones.find { it.key == "living_room" || it.key == "main_level" }
            livingRoomTemp = lrZone?.currentTemp
        }
        if (livingRoomTemp == null) {
            val cached = sharedPrefs.getFloat("last_known_living_temp", -1f)
            if (cached != -1f) {
                livingRoomTemp = cached.toDouble()
            } else {
                val cachedIndoor = sharedPrefs.getFloat("last_known_indoor_temp", -1f)
                if (cachedIndoor != -1f) livingRoomTemp = cachedIndoor.toDouble()
            }
        }

        // 2. Bedroom 2 Temp
        var bedroom2Temp: Double? = null
        if (uiState is HvacUiState.Success) {
            val b2Zone = uiState.zones.find { it.key == "bedroom_2" }
            bedroom2Temp = b2Zone?.currentTemp
        }
        if (bedroom2Temp == null) {
            val cached = sharedPrefs.getFloat("last_known_bedroom2_temp", -1f)
            if (cached != -1f) bedroom2Temp = cached.toDouble()
        }

        // 3. Outside Temp
        var outdoorTemp: Double? = null
        val weatherStateVal = viewModel?.weatherState?.value
        val firstDay = weatherStateVal?.days?.firstOrNull()
        if (firstDay != null) {
            outdoorTemp = firstDay.avgHighTemp
        }
        if (outdoorTemp == null) {
            val cached = sharedPrefs.getFloat("last_known_outdoor_temp", -1f)
            if (cached != -1f) outdoorTemp = cached.toDouble()
        }

        // 4. Pool Temp
        var poolTemp: Double? = viewModel?.poolState?.value?.waterTemperature
        if (poolTemp == null) {
            val cached = sharedPrefs.getFloat("last_known_pool_temp", -1f)
            if (cached != -1f) poolTemp = cached.toDouble()
        }

        // 5. Global HVAC Season Mode
        val currentMode = if (uiState is HvacUiState.Success) {
            uiState.globalSettings.globalHvacMode
        } else {
            sharedPrefs.getString("last_known_hvac_mode", "heat") ?: "heat"
        }

        // 6. Additional info (Solar, Battery, Hot Water, other zones)
        var solarKw: Double? = viewModel?.solarLiveState?.value?.let {
            if (it.liveProductionWatts > 0f) (it.liveProductionWatts / 1000.0) else null
        }
        if (solarKw == null) {
            val cached = sharedPrefs.getFloat("last_known_solar_kw", -1f)
            if (cached != -1f) solarKw = cached.toDouble()
        }

        var batterySoc: Double? = null
        val cachedBatt = sharedPrefs.getFloat("last_known_battery_soc", -1f)
        if (cachedBatt != -1f) {
            batterySoc = cachedBatt.toDouble()
        }

        var hwFullness: Double? = if (uiState is HvacUiState.Success) {
            uiState.globalSettings.waterHeaterFullness
        } else null
        if (hwFullness == null) {
            val cached = sharedPrefs.getFloat("last_known_hot_water_fullness", -1f)
            if (cached != -1f) hwFullness = cached.toDouble()
        }

        var hwTemp: Double? = null
        val cachedHwTemp = sharedPrefs.getFloat("last_known_hot_water_temp", -1f)
        if (cachedHwTemp != -1f) {
            hwTemp = cachedHwTemp.toDouble()
        }

        val masterTemp = (if (uiState is HvacUiState.Success) uiState.zones.find { it.key == "master_bedroom" }?.currentTemp else null)
            ?: sharedPrefs.getFloat("last_known_master_temp", -1f).takeIf { it != -1f }?.toDouble()

        val basementTemp = (if (uiState is HvacUiState.Success) uiState.zones.find { it.key == "basement" }?.currentTemp else null)
            ?: sharedPrefs.getFloat("last_known_basement_temp", -1f).takeIf { it != -1f }?.toDouble()

        val bed1Temp = (if (uiState is HvacUiState.Success) uiState.zones.find { it.key == "bedroom_1" }?.currentTemp else null)
            ?: sharedPrefs.getFloat("last_known_bed1_temp", -1f).takeIf { it != -1f }?.toDouble()

        // Formatting text tokens
        val livingStr = formatTemp(livingRoomTemp)
        val bed2Str = formatTemp(bedroom2Temp)
        val outdoorStr = formatTemp(outdoorTemp)
        val poolStr = formatTemp(poolTemp)
        val modeStr = currentMode.uppercase(Locale.US)

        // Collapsed View
        val collapsedTitle = "🏠 Living: $livingStr  •  🛏️ Bed 2: $bed2Str"
        val collapsedText = "⛅ Out: $outdoorStr  •  🏊 Pool: $poolStr  •  $modeStr"

        // Expanded BigTextStyle Content
        val bigTextBuilder = StringBuilder()
        bigTextBuilder.append("🏠 Living Room: $livingStr    •    🛏️ Bedroom 2: $bed2Str\n")
        bigTextBuilder.append("⛅ Outside Temp: $outdoorStr    •    🏊 Pool Water: $poolStr\n")

        val extraTelemetry = mutableListOf<String>()
        if (solarKw != null && solarKw >= 0.0) {
            val solarText = String.format(Locale.US, "☀️ Solar: %.1f kW", solarKw)
            val battText = if (batterySoc != null && batterySoc >= 0.0) " (🔋 ${batterySoc.toInt()}%)" else ""
            extraTelemetry.add("$solarText$battText")
        } else if (batterySoc != null && batterySoc >= 0.0) {
            extraTelemetry.add("🔋 Battery: ${batterySoc.toInt()}%")
        }

        if (hwFullness != null && hwFullness >= 0.0) {
            val hwText = "💧 Hot Water: ${hwFullness.toInt()}%"
            val hwTempText = if (hwTemp != null && hwTemp > 0) " (${hwTemp.toInt()}°F)" else ""
            extraTelemetry.add("$hwText$hwTempText")
        }
        if (extraTelemetry.isNotEmpty()) {
            bigTextBuilder.append(extraTelemetry.joinToString("   •   ")).append("\n")
        }

        val extraRooms = mutableListOf<String>()
        if (masterTemp != null) extraRooms.add("Master: ${formatTemp(masterTemp)}")
        if (basementTemp != null) extraRooms.add("Basement: ${formatTemp(basementTemp)}")
        if (bed1Temp != null) extraRooms.add("Gym: ${formatTemp(bed1Temp)}")
        if (extraRooms.isNotEmpty()) {
            bigTextBuilder.append("🛏️ " + extraRooms.joinToString("  •  ")).append("\n")
        }

        bigTextBuilder.append("⚙️ Season Mode: $modeStr   •   Active Deck Connection")

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .setBigContentTitle("🏠 Living: $livingStr  |  🛏️ Bed 2: $bed2Str  |  🏊 Pool: $poolStr")
            .setSummaryText("Home Climate & Pool Deck")
            .bigText(bigTextBuilder.toString())

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action Buttons Setup
        val heatIntent = Intent(this, HvacForegroundService::class.java).apply { action = ACTION_HEAT }
        val pendingHeat = PendingIntent.getService(this, 101, heatIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val coolIntent = Intent(this, HvacForegroundService::class.java).apply { action = ACTION_COOL }
        val pendingCool = PendingIntent.getService(this, 102, coolIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val offIntent = Intent(this, HvacForegroundService::class.java).apply { action = ACTION_OFF }
        val pendingOff = PendingIntent.getService(this, 103, offIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val dismissIntent = Intent(this, HvacForegroundService::class.java).apply { action = ACTION_DISMISS }
        val pendingDismiss = PendingIntent.getService(this, 104, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(collapsedTitle)
            .setContentText(collapsedText)
            .setSubText("Climate & Pool")
            .setStyle(bigTextStyle)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingOpenApp)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setDeleteIntent(pendingDismiss)
            .addAction(0, "HEAT", pendingHeat)
            .addAction(0, "COOL", pendingCool)
            .addAction(0, "OFF", pendingOff)
            .addAction(0, "DISMISS (5M)", pendingDismiss)
            .build()
    }

    private fun startPollingLoop() {
        pollJob?.cancel()
        pollJob = serviceScope.launch {
            val sharedPrefs = getSharedPreferences("hvac_settings", Context.MODE_PRIVATE)
            val wsManager = HomeAssistantWebSocketManager.getInstance(applicationContext)

            val url = sharedPrefs.getString("ha_url", "") ?: ""
            val token = sharedPrefs.getString("ha_token", "") ?: ""

            if (url.isNotEmpty() && token.isNotEmpty()) {
                HomeAssistantClient.initialize(url, token)
                wsManager.connect(url, token)
            }

            // Real-time state collector via WebSocket StateFlow
            launch {
                wsManager.states.collect { statesMap ->
                    if (statesMap.isNotEmpty()) {
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

                        // Outside temp
                        val outdoorTemp = statesMap["sensor.outdoor_temperature"]?.state?.toDoubleOrNull()
                            ?: statesMap["sensor.outside_temperature"]?.state?.toDoubleOrNull()
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

                        // Solar power & Battery
                        val solarWatts = statesMap["sensor.envoy_122223062334_current_power_production"]?.state?.toDoubleOrNull()
                            ?: statesMap["sensor.solar_power"]?.state?.toDoubleOrNull()
                        if (solarWatts != null) {
                            editor.putFloat("last_known_solar_kw", (solarWatts / 1000.0).toFloat())
                        }

                        val batterySoc = statesMap["sensor.encharge_battery_percentage"]?.state?.toDoubleOrNull()
                            ?: statesMap["sensor.battery_soc"]?.state?.toDoubleOrNull()
                        if (batterySoc != null) {
                            editor.putFloat("last_known_battery_soc", batterySoc.toFloat())
                        }

                        // Hot water
                        val hwFullness = statesMap["sensor.heat_pump_water_heater_available_hot_water"]?.state?.toDoubleOrNull()
                        if (hwFullness != null) {
                            editor.putFloat("last_known_hot_water_fullness", hwFullness.toFloat())
                        }

                        val hwTemp = statesMap["water_heater.my_water_heater"]?.getDoubleAttribute("current_temperature")
                        if (hwTemp != null) {
                            editor.putFloat("last_known_hot_water_temp", hwTemp.toFloat())
                        }

                        // Other zones
                        val masterTemp = statesMap["climate.hp_master_bedroom"]?.getDoubleAttribute("current_temperature")
                        if (masterTemp != null) {
                            editor.putFloat("last_known_master_temp", masterTemp.toFloat())
                        }

                        val basementTemp = statesMap["climate.hp_basement"]?.getDoubleAttribute("current_temperature")
                        if (basementTemp != null) {
                            editor.putFloat("last_known_basement_temp", basementTemp.toFloat())
                        }

                        val bed1Temp = statesMap["climate.hp_bedroom_1"]?.getDoubleAttribute("current_temperature")
                        if (bed1Temp != null) {
                            editor.putFloat("last_known_bed1_temp", bed1Temp.toFloat())
                        }

                        editor.apply()
                        updateNotification()
                    }
                }
            }

            // Fallback baseline sync or periodic weather update check
            while (isActive) {
                try {
                    val activeViewModel = HvacViewModel.getInstance()
                    val firstHigh = activeViewModel?.weatherState?.value?.days?.firstOrNull()?.avgHighTemp
                    if (firstHigh != null) {
                        sharedPrefs.edit().putFloat("last_known_outdoor_temp", firstHigh.toFloat()).apply()
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
        }
    }
}
