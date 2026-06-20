package com.example.service

import android.app.AlarmManager
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
import com.example.viewmodel.HvacViewModel
import com.example.viewmodel.HvacUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, HvacForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
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
                // Swipe or button dismiss action: stops foreground, clears notification, and schedules a wake-up in 5 minutes
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
                // Post as standard persistent notification so user still sees it
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildHvacNotification())
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun buildHvacNotification(): android.app.Notification {
        val sharedPrefs = getSharedPreferences("hvac_settings", Context.MODE_PRIVATE)

        // Extract values from active static instance or fallbacks stored in preferences
        val viewModel = HvacViewModel.getInstance()
        val uiState = viewModel?.uiState?.value

        var mainLevelTemp: Double? = null
        if (uiState is HvacUiState.Success) {
            val mainLevelZone = uiState.zones.find { it.key == "main_level" || it.key == "living_room" }
            mainLevelTemp = mainLevelZone?.currentTemp
        }
        if (mainLevelTemp == null) {
            val cachedTemp = sharedPrefs.getFloat("last_known_indoor_temp", -1f)
            if (cachedTemp != -1f) {
                mainLevelTemp = cachedTemp.toDouble()
            }
        }

        var outdoorTemp: Double? = null
        val weatherStateVal = viewModel?.weatherState?.value
        val firstDay = weatherStateVal?.days?.firstOrNull()
        if (firstDay != null) {
            outdoorTemp = firstDay.avgHighTemp
        }
        if (outdoorTemp == null) {
            val cachedWeather = sharedPrefs.getFloat("last_known_outdoor_temp", -1f)
            if (cachedWeather != -1f) {
                outdoorTemp = cachedWeather.toDouble()
            }
        }

        val currentMode = if (uiState is HvacUiState.Success) {
            uiState.globalSettings.globalHvacMode
        } else {
            sharedPrefs.getString("last_known_hvac_mode", "heat") ?: "heat"
        }

        val indoorStr = if (mainLevelTemp != null) "${mainLevelTemp.toInt()}°F" else "N/A"
        val outdoorStr = if (outdoorTemp != null) "${outdoorTemp.toInt()}°F" else "N/A"
        val modeStr = currentMode.uppercase()

        val textContent = "Main Indoor: $indoorStr | Outdoor: $outdoorStr | Season: $modeStr"

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
            .setContentTitle("HVAC Persistent Deck Connection")
            .setContentText(textContent)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingOpenApp)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setDeleteIntent(pendingDismiss) // Trigger if swiped under compatible OS versions
            .addAction(0, "HEAT", pendingHeat)
            .addAction(0, "COOL", pendingCool)
            .addAction(0, "OFF", pendingOff)
            .addAction(0, "DISMISS (5M)", pendingDismiss)
            .build()
    }

    private fun startPollingLoop() {
        pollJob?.cancel()
        pollJob = serviceScope.launch {
            while (isActive) {
                try {
                    val sharedPrefs = getSharedPreferences("hvac_settings", Context.MODE_PRIVATE)
                    val isLoggedIn = sharedPrefs.getBoolean("logged_in", false)
                    val url = sharedPrefs.getString("ha_url", "") ?: ""
                    val token = sharedPrefs.getString("ha_token", "") ?: ""

                    if (isLoggedIn && url.isNotEmpty() && token.isNotEmpty()) {
                        HomeAssistantClient.initialize(url, token)
                        val states = HomeAssistantClient.service.getStates()
                        if (states.isNotEmpty()) {
                            val statesMap = states.associateBy { it.entity_id }

                            val globalMode = statesMap["input_select.global_hvac_mode"]?.state ?: "heat"
                            sharedPrefs.edit().putString("last_known_hvac_mode", globalMode).apply()

                            val livingRoomClimate = statesMap["climate.hp_living_room"]
                            val parsedIndoorTemp = livingRoomClimate?.getDoubleAttribute("current_temperature")

                            if (parsedIndoorTemp != null) {
                                sharedPrefs.edit().putFloat("last_known_indoor_temp", parsedIndoorTemp.toFloat()).apply()
                            }

                            // Trigger active view model updates if currently visible
                            val activeViewModel = HvacViewModel.getInstance()
                            if (activeViewModel != null) {
                                activeViewModel.fetchStates()
                            }
                        }
                    }

                    // Read weather forecast max high temperature
                    val activeViewModel = HvacViewModel.getInstance()
                    val firstHigh = activeViewModel?.weatherState?.value?.days?.firstOrNull()?.avgHighTemp
                    if (firstHigh != null) {
                        sharedPrefs.edit().putFloat("last_known_outdoor_temp", firstHigh.toFloat()).apply()
                    }

                    updateNotification()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(15000L) // Refresh states every 15 seconds to remain active and connected
            }
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildHvacNotification())
    }

    private fun triggerHvacMode(mode: String) {
        serviceScope.launch {
            try {
                val sharedPrefs = getSharedPreferences("hvac_settings", Context.MODE_PRIVATE)
                val url = sharedPrefs.getString("ha_url", "") ?: ""
                val token = sharedPrefs.getString("ha_token", "") ?: ""

                if (url.isNotEmpty() && token.isNotEmpty()) {
                    HomeAssistantClient.initialize(url, token)
                    HomeAssistantClient.service.callService(
                        "input_select",
                        "select_option",
                        mapOf(
                            "entity_id" to "input_select.global_hvac_mode",
                            "option" to mode
                        )
                    )
                    sharedPrefs.edit().putString("last_known_hvac_mode", mode).apply()

                    val activeViewModel = HvacViewModel.getInstance()
                    if (activeViewModel != null) {
                        activeViewModel.fetchStates()
                    }
                }
                updateNotification()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun scheduleRestartInFiveMinutes() {
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
