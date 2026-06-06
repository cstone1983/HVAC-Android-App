package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.HomeAssistantClient
import com.example.api.GithubClient
import com.example.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider

sealed interface HvacUiState {
    object Loading : HvacUiState
    data class Success(
        val globalSettings: GlobalSettings,
        val roomSensors: List<RoomSensor>,
        val zones: List<ClimateZone>,
        val lights: List<LightControl>,
        val switches: List<SwitchControl>,
        val covers: List<CoverControl>,
        val lastUpdated: Long
    ) : HvacUiState
    data class Error(val message: String) : HvacUiState
}

class HvacViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("hvac_settings", Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loginErrorMessage = MutableStateFlow<String?>(null)
    val loginErrorMessage: StateFlow<String?> = _loginErrorMessage.asStateFlow()

    private val _uiState = MutableStateFlow<HvacUiState>(HvacUiState.Loading)
    val uiState: StateFlow<HvacUiState> = _uiState.asStateFlow()

    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback: StateFlow<String?> = _actionFeedback.asStateFlow()

    private var syncJob: Job? = null
    private var consecutiveFailureCount = 0

    init {
        val savedUrl = sharedPrefs.getString("ha_url", null)
        val savedToken = sharedPrefs.getString("ha_token", null)
        val hasSession = sharedPrefs.getBoolean("logged_in", false)

        if (hasSession && !savedUrl.isNullOrEmpty() && !savedToken.isNullOrEmpty()) {
            HomeAssistantClient.initialize(savedUrl, savedToken)
            _isLoggedIn.value = true
            startSync()
        } else {
            _isLoggedIn.value = false
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loginErrorMessage.value = null
            if (username.isBlank() || password.isBlank()) {
                _loginErrorMessage.value = "Username and password cannot be empty."
                return@launch
            }
            try {
                // Get pre-configured background server address and token
                val buildUrl = try { com.example.BuildConfig.HA_URL } catch (e: Exception) { "" }
                val buildToken = try { com.example.BuildConfig.HA_TOKEN } catch (e: Exception) { "" }

                // Fallback option in case they were previously stored in sharedPrefs
                val savedUrl = sharedPrefs.getString("ha_url", null)
                val savedToken = sharedPrefs.getString("ha_token", null)

                val targetUrl = if (!savedUrl.isNullOrEmpty()) savedUrl else buildUrl
                val targetToken = if (!savedToken.isNullOrEmpty()) savedToken else buildToken

                if (targetUrl.isEmpty() || targetToken.isEmpty()) {
                    _loginErrorMessage.value = "Configuration error: Missing background Server Address or Token configuration."
                    return@launch
                }

                // Trim trailing slash and format helper
                val formattedUrl = HomeAssistantClient.formatBaseUrl(targetUrl)
                val tempService = HomeAssistantClient.createService(formattedUrl, targetToken)
                val states = tempService.getStates()
                if (states.isNotEmpty()) {
                    sharedPrefs.edit()
                        .putString("ha_url", formattedUrl)
                        .putString("ha_token", targetToken)
                        .putString("ha_username", username)
                        .putBoolean("logged_in", true)
                        .apply()

                    HomeAssistantClient.initialize(formattedUrl, targetToken)
                    _isLoggedIn.value = true
                    _loginErrorMessage.value = null
                    startSync()
                } else {
                    _loginErrorMessage.value = "Validation failed: No entities found."
                }
            } catch (e: Exception) {
                _loginErrorMessage.value = "Connection failed: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun logout() {
        sharedPrefs.edit()
            .putBoolean("logged_in", false)
            .apply()

        _isLoggedIn.value = false
        syncJob?.cancel()
        _uiState.value = HvacUiState.Loading
    }

    fun startSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            while (true) {
                fetchStates()
                delay(10000) // Poll every 10 seconds
            }
        }
    }

    fun clearFeedback() {
        _actionFeedback.value = null
    }

    suspend fun fetchStates() {
        var responseList: List<com.example.api.EntityState>? = null
        var lastException: Exception? = null
        val maxAttempts = 3

        for (attempt in 1..maxAttempts) {
            try {
                responseList = HomeAssistantClient.service.getStates()
                break
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxAttempts) {
                    delay(3000) // quiet reconnection wait
                }
            }
        }

        if (responseList == null) {
            consecutiveFailureCount++
            val current = _uiState.value
            if (current !is HvacUiState.Success || consecutiveFailureCount >= 3) {
                _uiState.value = HvacUiState.Error("Connectivity error: Reconnection failed. ${lastException?.localizedMessage ?: "Unknown error"}")
            }
            return
        }

        try {
            val statesMap = responseList.associateBy { it.entity_id }

            // 1. Global settings parsing
            val houseSchedule = statesMap["input_select.house_schedule_state"]?.state ?: "Day"
            val waterHeaterMode = statesMap["input_select.water_heater_mode"]?.state ?: "eco"
            val globalHvacMode = statesMap["input_select.global_hvac_mode"]?.state ?: "heat"
            val globalSettings = GlobalSettings(
                houseSchedule = houseSchedule,
                waterHeaterMode = waterHeaterMode,
                globalHvacMode = globalHvacMode
            )

            // 2. Room sensors mapping
            val templateSensors = listOf(
                RoomSensor("living_room", "LIVING", "sensor.living_room_temperature"),
                RoomSensor("dining_room", "DINING", "climate.hp_dining_room", "current_temperature"),
                RoomSensor("upstairs", "UPSTAIRS", "climate.upstairs", "current_temperature"),
                RoomSensor("bedroom", "BEDROOM", "sensor.bedroom_temperature"),
                RoomSensor("basement", "BASEMENT", "climate.basement_thermostat", "current_temperature")
            )
            val parsedSensors = templateSensors.map { sensor ->
                val stateNode = statesMap[sensor.stateId]
                val value = if (sensor.attributeName != null) {
                    stateNode?.getDoubleAttribute(sensor.attributeName)
                } else {
                    stateNode?.state?.toDoubleOrNull()
                }
                sensor.copy(temp = value)
            }

            // 3. Climate Zones mapping
            val templateZones = listOf(
                ClimateZone(
                    key = "main_level",
                    name = "Main Level",
                    climateEntityId = "climate.hp_living_room",
                    autoEntityId = "input_boolean.zone_enable_main_level",
                    overrideEntityId = "input_boolean.override_main_level",
                    tiltEntityId = "input_select.main_level_tilt_mode",
                    fanEntityId = "input_select.main_level_fan_mode",
                    presetsHeat = Presets("input_number.main_level_day_temp", "input_number.main_level_night_temp", "input_number.main_level_away_temp"),
                    presetsCool = Presets("input_number.main_level_day_cool", "input_number.main_level_night_cool", "input_number.main_level_away_cool")
                ),
                ClimateZone(
                    key = "anthony",
                    name = "Anthony",
                    climateEntityId = "climate.hp_anthony",
                    autoEntityId = "input_boolean.zone_enable_anthony",
                    overrideEntityId = "input_boolean.override_anthony",
                    tiltEntityId = "input_select.anthony_tilt_mode",
                    fanEntityId = "input_select.anthony_fan_mode",
                    presetsHeat = Presets("input_number.anthony_day_temp", "input_number.anthony_night_temp", "input_number.anthony_away_temp"),
                    presetsCool = Presets("input_number.anthony_day_cool", "input_number.anthony_night_cool", "input_number.anthony_away_cool")
                ),
                ClimateZone(
                    key = "autumn",
                    name = "Autumn",
                    climateEntityId = "climate.hp_autumn",
                    autoEntityId = "input_boolean.zone_enable_autumn",
                    overrideEntityId = "input_boolean.override_autumn",
                    tiltEntityId = "input_select.autumn_tilt_mode",
                    fanEntityId = "input_select.autumn_fan_mode",
                    presetsHeat = Presets("input_number.autumn_day_temp", "input_number.autumn_night_temp", "input_number.autumn_away_temp"),
                    presetsCool = Presets("input_number.autumn_day_cool", "input_number.autumn_night_cool", "input_number.autumn_away_cool")
                ),
                ClimateZone(
                    key = "bedroom_1",
                    name = "Master 1",
                    climateEntityId = "climate.hp_bedroom",
                    autoEntityId = "input_boolean.zone_enable_bedroom_1",
                    overrideEntityId = "input_boolean.override_bedroom_1",
                    tiltEntityId = "input_select.bedroom_1_tilt_mode",
                    fanEntityId = "input_select.bedroom_1_fan_mode",
                    presetsHeat = Presets("input_number.bedroom_1_day_temp", "input_number.bedroom_1_night_temp", "input_number.bedroom_1_away_temp"),
                    presetsCool = Presets("input_number.bedroom_1_day_cool", "input_number.bedroom_1_night_cool", "input_number.bedroom_1_away_cool")
                ),
                ClimateZone(
                    key = "bedroom_2",
                    name = "Master 2",
                    climateEntityId = "climate.hp_bedroom_2",
                    autoEntityId = "input_boolean.zone_enable_bedroom_2",
                    overrideEntityId = "input_boolean.override_bedroom_2",
                    tiltEntityId = "input_select.bedroom_2_tilt_mode",
                    fanEntityId = "input_select.bedroom_2_fan_mode",
                    presetsHeat = Presets("input_number.bedroom_2_day_temp", "input_number.bedroom_2_night_temp", "input_number.bedroom_2_away_temp"),
                    presetsCool = Presets("input_number.bedroom_2_day_cool", "input_number.bedroom_2_night_cool", "input_number.bedroom_2_away_cool")
                ),
                ClimateZone(
                    key = "basement",
                    name = "Basement",
                    climateEntityId = "climate.hp_basement",
                    autoEntityId = "input_boolean.zone_enable_basement",
                    overrideEntityId = "input_boolean.override_basement",
                    tiltEntityId = "input_select.basement_tilt_mode",
                    fanEntityId = "input_select.basement_fan_mode",
                    presetsHeat = Presets("input_number.basement_day_temp", "input_number.basement_night_temp", "input_number.basement_away_temp"),
                    presetsCool = Presets("input_number.basement_day_cool", "input_number.basement_night_cool", "input_number.basement_away_cool")
                )
            )

            val parsedZones = templateZones.map { zone ->
                val climate = statesMap[zone.climateEntityId]
                val auto = statesMap[zone.autoEntityId]
                val override = statesMap[zone.overrideEntityId]
                val tilt = statesMap[zone.tiltEntityId]
                val fan = statesMap[zone.fanEntityId]

                val hDay = statesMap[zone.presetsHeat.day]?.state?.toDoubleOrNull()
                val hNight = statesMap[zone.presetsHeat.night]?.state?.toDoubleOrNull()
                val hAway = statesMap[zone.presetsHeat.away]?.state?.toDoubleOrNull()

                val cDay = statesMap[zone.presetsCool.day]?.state?.toDoubleOrNull()
                val cNight = statesMap[zone.presetsCool.night]?.state?.toDoubleOrNull()
                val cAway = statesMap[zone.presetsCool.away]?.state?.toDoubleOrNull()

                val vOpts = tilt?.getListAttribute("options")
                val fOpts = fan?.getListAttribute("options")

                zone.copy(
                    currentTemp = climate?.getDoubleAttribute("current_temperature"),
                    targetTemp = climate?.getDoubleAttribute("temperature") ?: climate?.state?.toDoubleOrNull(),
                    currentHvacMode = climate?.state ?: "off",
                    autoOn = auto?.state?.lowercase() == "on",
                    overrideOn = override?.state?.lowercase() == "on",
                    vaneMode = tilt?.state ?: "Auto",
                    fanMode = fan?.state ?: "Auto",
                    vaneOptions = if (!vOpts.isNullOrEmpty()) vOpts else zone.vaneOptions,
                    fanOptions = if (!fOpts.isNullOrEmpty()) fOpts else zone.fanOptions,
                    presetsHeat = zone.presetsHeat.copy(
                        dayValue = hDay,
                        nightValue = hNight,
                        awayValue = hAway
                    ),
                    presetsCool = zone.presetsCool.copy(
                        dayValue = cDay,
                        nightValue = cNight,
                        awayValue = cAway
                    )
                )
            }

            // 4. Lights mapping
            val templateLights = listOf(
                LightControl("light.kitchen_main_lights", "Kitchen"),
                LightControl("light.living_room_sconces", "Living Sconces"),
                LightControl("light.dining_room_main_lights", "Dining"),
                LightControl("light.stairs_main_lights", "Stairs"),
                LightControl("light.exterior_sconces", "Exterior Sconces"),
                LightControl("light.porch_light", "Porch Side"),
                LightControl("light.porch_stairway_1", "Porch Stair 1")
            )
            val parsedLights = templateLights.map { light ->
                val node = statesMap[light.entityId]
                light.copy(
                    isOn = node?.state?.lowercase() == "on",
                    brightness = node?.getDoubleAttribute("brightness")?.toInt()
                )
            }

            // 5. Switches mapping
            val templateSwitches = listOf(
                SwitchControl("switch.shelly1_e8db84d7217d", "Garage Left"),
                SwitchControl("switch.shellyplus1_b8d61a8a78b0_switch_0", "Workshop"),
                SwitchControl("switch.exterior_backyard_flood", "Exterior Flood"),
                SwitchControl("switch.porch_led_strip", "Porch LED"),
                SwitchControl("switch.mudroom_wall_led", "Mudroom LED")
            )
            val parsedSwitches = templateSwitches.map { sm ->
                val node = statesMap[sm.entityId]
                sm.copy(isOn = node?.state?.lowercase() == "on")
            }

            // 6. Covers mapping
            val templateCovers = listOf(
                CoverControl("cover.konnected_d332ec_garage_door", "Garage South")
            )
            val parsedCovers = templateCovers.map { cv ->
                val node = statesMap[cv.entityId]
                cv.copy(state = node?.state ?: "closed")
            }

            _uiState.value = HvacUiState.Success(
                globalSettings = globalSettings,
                roomSensors = parsedSensors,
                zones = parsedZones,
                lights = parsedLights,
                switches = parsedSwitches,
                covers = parsedCovers,
                lastUpdated = System.currentTimeMillis()
            )
            consecutiveFailureCount = 0
        } catch (e: Exception) {
            _uiState.value = HvacUiState.Error("Connectivity error: Check connection setup. ${e.localizedMessage}")
        }
    }

    // Interactive Action service calls
    fun selectHouseSchedule(option: String) {
        callServiceWithOptimisticFeedback("input_select", "select_option", mapOf(
            "entity_id" to "input_select.house_schedule_state",
            "option" to option
        ), "Applying schedule: $option")
    }

    fun selectWaterHeaterMode(option: String) {
        callServiceWithOptimisticFeedback("input_select", "select_option", mapOf(
            "entity_id" to "input_select.water_heater_mode",
            "option" to option
        ), "Applying hot water mode: $option")
    }

    fun selectGlobalHvacMode(option: String) {
        callServiceWithOptimisticFeedback("input_select", "select_option", mapOf(
            "entity_id" to "input_select.global_hvac_mode",
            "option" to option
        ), "Applying active season: $option")
    }

    fun toggleInputBoolean(entityId: String, currentOn: Boolean, name: String) {
        val service = if (currentOn) "turn_off" else "turn_on"
        callServiceWithOptimisticFeedback("input_boolean", service, mapOf(
            "entity_id" to entityId
        ), "$name: ${if (currentOn) "OFF" else "ON"}")
    }

    fun selectVaneMode(entityId: String, option: String, name: String) {
        callServiceWithOptimisticFeedback("input_select", "select_option", mapOf(
            "entity_id" to entityId,
            "option" to option
        ), "$name Vane: $option")
    }

    fun selectFanMode(entityId: String, option: String, name: String) {
        callServiceWithOptimisticFeedback("input_select", "select_option", mapOf(
            "entity_id" to entityId,
            "option" to option
        ), "$name Fan: $option")
    }

    fun setTargetTemperature(climateEntityId: String, temperature: Double, name: String) {
        callServiceWithOptimisticFeedback("climate", "set_temperature", mapOf(
            "entity_id" to climateEntityId,
            "temperature" to temperature
        ), "$name setpoint updated to ${temperature.toInt()}°F")
    }

    fun setPresetTemperature(numberEntityId: String, value: Double, name: String) {
        callServiceWithOptimisticFeedback("input_number", "set_value", mapOf(
            "entity_id" to numberEntityId,
            "value" to value
        ), "Adjusting preset $name to ${value.toInt()}°F")
    }

    fun toggleZonePower(climateEntityId: String, currentHvacMode: String, globalHvacMode: String, name: String) {
        val isOff = currentHvacMode.lowercase() == "off"
        val targetMode = if (isOff) {
            if (globalHvacMode.lowercase() == "cool") "cool" else "heat"
        } else {
            "off"
        }
        callServiceWithOptimisticFeedback("climate", "set_hvac_mode", mapOf(
            "entity_id" to climateEntityId,
            "hvac_mode" to targetMode
        ), "$name power: ${targetMode.uppercase()}")
    }

    fun toggleLight(entityId: String, currentOn: Boolean, name: String) {
        val service = if (currentOn) "turn_off" else "turn_on"
        callServiceWithOptimisticFeedback("light", service, mapOf(
            "entity_id" to entityId
        ), "$name Light toggled")
    }

    fun toggleSwitch(entityId: String, currentOn: Boolean, name: String) {
        val service = if (currentOn) "turn_off" else "turn_on"
        callServiceWithOptimisticFeedback("switch", service, mapOf(
            "entity_id" to entityId
        ), "$name Switch toggled")
    }

    fun toggleCover(entityId: String, state: String, name: String) {
        val service = if (state == "open" || state == "opening") "close_cover" else "open_cover"
        callServiceWithOptimisticFeedback("cover", service, mapOf(
            "entity_id" to entityId
        ), "$name Core trigger activated")
    }

    // Clean service orchestrator
    private fun callServiceWithOptimisticFeedback(
        domain: String,
        service: String,
        payload: Map<String, Any>,
        feedbackMessage: String
    ) {
        _actionFeedback.value = feedbackMessage
        viewModelScope.launch {
            try {
                val response = HomeAssistantClient.service.callService(domain, service, payload)
                if (response.isSuccessful) {
                    fetchStates() // immediately sync to get final states
                } else {
                    _actionFeedback.value = "Failed to apply state (API returned error)"
                }
            } catch (e: Exception) {
                _actionFeedback.value = "Failed to sync action: ${e.localizedMessage}"
            }
        }
    }

    // ======================== GITHUB UPDATER FUNCTIONALITY ========================

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    fun checkForUpdates(currentVersion: String) {
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            try {
                var release: com.example.model.GithubRelease? = null
                val response = GithubClient.service.getLatestRelease()
                
                if (response.isSuccessful && response.body() != null) {
                    release = response.body()
                } else {
                    // Fallback: Check the releases list endpoint in case latest is empty or it's a pre-release
                    val listResponse = GithubClient.service.getReleases()
                    if (listResponse.isSuccessful && !listResponse.body().isNullOrEmpty()) {
                        release = listResponse.body()!!.first()
                    }
                }

                if (release != null) {
                    val latestTag = release.tagName.trim().removePrefix("v")
                    val currentClean = currentVersion.trim().removePrefix("v")

                    if (isNewerVersion(latestTag, currentClean)) {
                        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                        if (apkAsset != null) {
                            _updateState.value = UpdateState.UpdateAvailable(
                                version = release.tagName,
                                releaseNotes = release.body ?: "No release notes available.",
                                downloadUrl = apkAsset.browserDownloadUrl,
                                size = apkAsset.size
                            )
                        } else {
                            _updateState.value = UpdateState.Error("No APK found in the latest release assets.")
                        }
                    } else {
                        _updateState.value = UpdateState.UpToDate
                    }
                } else {
                    _updateState.value = UpdateState.NoReleases
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("Failed to check for updates: ${e.localizedMessage}")
            }
        }
    }

    fun simulateUpdate() {
        _updateState.value = UpdateState.UpdateAvailable(
            version = "v2.0.4-simulation",
            releaseNotes = "AUTHENTIC SYSTEM UPDATE SIMULATION:\n\n• High-performance, low-latency Home Assistant sensor ingestion\n• Elegant Jetpack Compose Canvas thermal distribution visuals\n• Fully secure update pipeline utilizing FileProvider with strict URI permissions\n\nClick 'DOWNLOAD UPDATE' below to trigger the download sequence and launch the package manager install window.",
            downloadUrl = "https://raw.githubusercontent.com/cstone1983/HVAC-Android-App/main/aistudio-repository-template/src/main/res/drawable/ic_launcher_foreground.xml",
            size = 12480L
        )
    }

    fun downloadUpdateAndInstall(context: Context, downloadUrl: String, fileName: String = "update.apk") {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _updateState.value = UpdateState.Downloading(0, 100, 0)
                val response = GithubClient.service.downloadFile(downloadUrl)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val destinationFile = File(context.cacheDir, fileName)
                        if (destinationFile.exists()) {
                            destinationFile.delete()
                        }

                        val totalBytes = body.contentLength()
                        var bytesDownloaded = 0L

                        body.byteStream().use { inputStream ->
                            FileOutputStream(destinationFile).use { outputStream ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                var lastUpdateProgress = -1

                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                    bytesDownloaded += bytesRead

                                    val progress = if (totalBytes > 0) {
                                        ((bytesDownloaded * 100) / totalBytes).toInt()
                                    } else {
                                        -1
                                    }

                                    if (progress != lastUpdateProgress) {
                                        lastUpdateProgress = progress
                                        _updateState.value = UpdateState.Downloading(progress, totalBytes, bytesDownloaded)
                                    }
                                }
                            }
                        }

                        _updateState.value = UpdateState.Success(destinationFile.absolutePath)
                        _actionFeedback.value = "Update downloaded successfully."
                    } else {
                        _updateState.value = UpdateState.Error("Empty download stream response.")
                    }
                } else {
                    _updateState.value = UpdateState.Error("Failed to reach download link: ${response.code()}")
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("Failed downloading update: ${e.localizedMessage}")
            }
        }
    }

    fun installApk(context: Context, apkPath: String) {
        val file = File(apkPath)
        if (!file.exists()) return

        val intent = Intent(Intent.ACTION_VIEW).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            setDataAndType(uri, "application/vnd.android.package-archive")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                _actionFeedback.value = "Please grant permission to install unknown apps, then click install again."
                return
            }
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            _actionFeedback.value = "Failed to launch installer: ${e.localizedMessage}"
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestClean = latest.trim().removePrefix("v")
        val currentClean = current.trim().removePrefix("v")

        if (latestClean == currentClean) return false

        // Splitting into components by dots to support sub-versions
        val latestParts = latestClean.split(".")
        val currentParts = currentClean.split(".")

        val maxLength = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val latestPart = latestParts.getOrNull(i) ?: "0"
            val currentPart = currentParts.getOrNull(i) ?: "0"

            // Extract numeric prefix from each part safely, e.g. "4b4" -> 4, "12-alpha" -> 12
            val latestNum = latestPart.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            val currentNum = currentPart.takeWhile { it.isDigit() }.toIntOrNull() ?: 0

            if (latestNum > currentNum) return true
            if (latestNum < currentNum) return false

            // Compare non-numeric suffixes if numeric parts match
            val latestSuffix = latestPart.dropWhile { it.isDigit() }
            val currentSuffix = currentPart.dropWhile { it.isDigit() }

            if (latestSuffix != currentSuffix) {
                // Releases without suffixes are newer than pre-releases (e.g. "1.0" > "1.0-alpha")
                if (latestSuffix.isEmpty() && currentSuffix.isNotEmpty()) return true
                if (latestSuffix.isNotEmpty() && currentSuffix.isEmpty()) return false
                val cmp = latestSuffix.compareTo(currentSuffix)
                if (cmp > 0) return true
                if (cmp < 0) return false
            }
        }
        return false
    }

    override fun onCleared() {
        syncJob?.cancel()
        super.onCleared()
    }
}

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    object NoReleases : UpdateState
    data class UpdateAvailable(val version: String, val releaseNotes: String, val downloadUrl: String, val size: Long) : UpdateState
    object UpToDate : UpdateState
    data class Downloading(val progress: Int, val totalSize: Long, val downloaded: Long) : UpdateState
    data class Success(val apkPath: String) : UpdateState
    data class Error(val message: String) : UpdateState
}
