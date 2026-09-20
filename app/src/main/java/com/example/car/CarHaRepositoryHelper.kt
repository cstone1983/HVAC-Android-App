package com.example.car

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.api.EntityState
import com.example.api.HaConnectionState
import com.example.api.HomeAssistantClient
import com.example.api.HomeAssistantWebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Clean helper and repository for Android Auto integration.
 * Connects to Home Assistant via WebSocket (with REST fallback) and provides
 * reactive state modeling and safe driving actions for in-vehicle screens.
 */
class CarHaRepositoryHelper private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "CarHaRepoHelper"
        private const val PREFS_NAME = "hvac_settings"

        @Volatile
        private var instance: CarHaRepositoryHelper? = null

        fun getInstance(context: Context): CarHaRepositoryHelper {
            return instance ?: synchronized(this) {
                instance ?: CarHaRepositoryHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val wsManager = HomeAssistantWebSocketManager.getInstance(appContext)

    val states: StateFlow<Map<String, EntityState>> = wsManager.states
    val connectionState: StateFlow<HaConnectionState> = wsManager.connectionState

    init {
        initializeConnection()
    }

    /**
     * Connect to Home Assistant using configured credentials from preferences
     */
    fun initializeConnection() {
        val url = prefs.getString("ha_url", "") ?: ""
        val backupUrl = prefs.getString("backup_ha_url", "") ?: ""
        val token = prefs.getString("ha_token", "") ?: ""

        if (url.isNotBlank() && token.isNotBlank()) {
            Log.d(TAG, "Initializing Car Home Assistant connection to $url")
            HomeAssistantClient.initialize(url, token)
            wsManager.connectWithFailover(url, backupUrl, token)
        } else {
            Log.w(TAG, "No HA credentials found in SharedPreferences")
        }
    }

    /**
     * Dispatch a Home Assistant service call over WebSocket with REST fallback
     */
    suspend fun callService(
        domain: String,
        service: String,
        entityId: String? = null,
        serviceData: Map<String, Any?> = emptyMap()
    ): Boolean {
        val mergedData = if (entityId != null) {
            serviceData.toMutableMap().apply { put("entity_id", entityId) }
        } else {
            serviceData
        }

        val target = if (entityId != null) mapOf("entity_id" to entityId) else null

        Log.d(TAG, "Executing service call: $domain.$service on $entityId with $mergedData")

        // 1. Try real-time WebSocket first
        val wsSuccess = wsManager.callService(
            domain = domain,
            service = service,
            serviceData = mergedData,
            target = target
        )

        if (wsSuccess) {
            return true
        }

        // 2. Fallback to REST API if WebSocket is connecting or failed
        return try {
            val response = HomeAssistantClient.service.callService(
                domain = domain,
                service = service,
                payload = mergedData.filterValues { it != null }.mapValues { it.value!! }
            )
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Failed service call fallback $domain.$service for $entityId", e)
            false
        }
    }

    // ==========================================
    // Specific Action Methods for In-Car Controls
    // ==========================================

    /**
     * Toggle South Garage Door (cover.garage_door_south or switch.konnected_d332ec_str_output)
     */
    fun toggleSouthGarage(onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val currentStates = states.value
            val coverEntity = currentStates["cover.garage_door_south"]
            val success = if (coverEntity != null) {
                callService("cover", "toggle", "cover.garage_door_south")
            } else {
                callService("switch", "toggle", "switch.konnected_d332ec_str_output")
            }
            onComplete?.invoke(success)
        }
    }

    /**
     * Toggle Left Garage Door (switch.shelly1_e8db84d7217d or cover.garage_door_left)
     */
    fun toggleLeftGarage(onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val currentStates = states.value
            val switchEntity = currentStates["switch.shelly1_e8db84d7217d"]
            val success = if (switchEntity != null) {
                callService("switch", "toggle", "switch.shelly1_e8db84d7217d")
            } else {
                callService("cover", "toggle", "cover.garage_door_left")
            }
            onComplete?.invoke(success)
        }
    }

    /**
     * Cycle Water Heater Mode: eco -> heat_pump -> high_demand -> eco
     */
    fun cycleWaterHeaterMode(onComplete: ((String) -> Unit)? = null) {
        scope.launch {
            val currentStates = states.value
            val currentMode = currentStates["input_select.water_heater_mode"]?.state?.lowercase(Locale.US)
                ?: currentStates["water_heater.heat_pump_water_heater"]?.state?.lowercase(Locale.US)
                ?: "heat_pump"

            val nextMode = when (currentMode) {
                "eco" -> "heat_pump"
                "heat_pump" -> "high_demand"
                "high_demand" -> "eco"
                else -> "heat_pump"
            }

            val success = callService(
                domain = "input_select",
                service = "select_option",
                entityId = "input_select.water_heater_mode",
                serviceData = mapOf("option" to nextMode)
            )

            if (!success) {
                callService(
                    domain = "water_heater",
                    service = "set_operation_mode",
                    entityId = "water_heater.heat_pump_water_heater",
                    serviceData = mapOf("operation_mode" to nextMode)
                )
            }
            onComplete?.invoke(nextMode)
        }
    }

    /**
     * Toggle Pool Pump (switch.pool_pump)
     */
    fun togglePoolPump(onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val success = callService("switch", "toggle", "switch.pool_pump")
            onComplete?.invoke(success)
        }
    }

    /**
     * Cycle House Schedule State: Day -> Night -> Away -> Day
     */
    fun cycleHouseScheduleState(onComplete: ((String) -> Unit)? = null) {
        scope.launch {
            val currentStates = states.value
            val currentState = currentStates["input_select.house_schedule_state"]?.state?.lowercase(Locale.US) ?: "day"
            val nextState = when (currentState) {
                "day" -> "night"
                "night" -> "away"
                "away" -> "day"
                else -> "day"
            }

            callService(
                domain = "input_select",
                service = "select_option",
                entityId = "input_select.house_schedule_state",
                serviceData = mapOf("option" to nextState)
            )
            onComplete?.invoke(nextState)
        }
    }

    /**
     * Cycle Global HVAC Mode: heat -> cool -> off -> heat
     */
    fun cycleGlobalHvacMode(onComplete: ((String) -> Unit)? = null) {
        scope.launch {
            val currentStates = states.value
            val currentMode = currentStates["input_select.global_hvac_mode"]?.state?.lowercase(Locale.US) ?: "heat"
            val nextMode = when (currentMode) {
                "heat" -> "cool"
                "cool" -> "off"
                "off" -> "heat"
                else -> "heat"
            }

            callService(
                domain = "input_select",
                service = "select_option",
                entityId = "input_select.global_hvac_mode",
                serviceData = mapOf("option" to nextMode)
            )
            onComplete?.invoke(nextMode)
        }
    }

    /**
     * Toggle individual zone HVAC mode
     */
    fun toggleZoneHvacMode(climateEntityId: String, currentMode: String, onComplete: ((String) -> Unit)? = null) {
        scope.launch {
            val nextMode = when (currentMode.lowercase(Locale.US)) {
                "heat" -> "cool"
                "cool" -> "off"
                "off" -> "heat"
                else -> "heat"
            }

            callService(
                domain = "climate",
                service = "set_hvac_mode",
                entityId = climateEntityId,
                serviceData = mapOf("hvac_mode" to nextMode)
            )
            onComplete?.invoke(nextMode)
        }
    }

    /**
     * Adjust zone target temperature by delta
     */
    fun adjustZoneTargetTemp(climateEntityId: String, currentTarget: Double, delta: Double) {
        scope.launch {
            val newTarget = Math.round((currentTarget + delta) * 2.0) / 2.0
            callService(
                domain = "climate",
                service = "set_temperature",
                entityId = climateEntityId,
                serviceData = mapOf("temperature" to newTarget)
            )
        }
    }

    // ==========================================
    // State Extractors for UI Rendering
    // ==========================================

    fun getSouthGarageState(): GarageState {
        val s = states.value
        val cover = s["cover.garage_door_south"]
        val sw = s["switch.konnected_d332ec_str_output"]

        val isOpen = when {
            cover != null -> cover.state.equals("open", ignoreCase = true) || cover.state.equals("opening", ignoreCase = true)
            sw != null -> sw.state.equals("on", ignoreCase = true)
            else -> false
        }

        val stateText = when {
            cover != null -> cover.state.uppercase(Locale.US)
            sw != null -> if (sw.state.equals("on", ignoreCase = true)) "OPEN" else "CLOSED"
            else -> "CLOSED"
        }

        return GarageState(
            name = "South Garage Door",
            entityId = cover?.entity_id ?: "cover.garage_door_south",
            isOpen = isOpen,
            statusText = stateText
        )
    }

    fun getLeftGarageState(): GarageState {
        val s = states.value
        val sw = s["switch.shelly1_e8db84d7217d"]
        val cover = s["cover.garage_door_left"]

        val isOpen = when {
            cover != null -> cover.state.equals("open", ignoreCase = true)
            sw != null -> sw.state.equals("on", ignoreCase = true)
            else -> false
        }

        val stateText = when {
            cover != null -> cover.state.uppercase(Locale.US)
            sw != null -> if (sw.state.equals("on", ignoreCase = true)) "OPEN" else "CLOSED"
            else -> "CLOSED"
        }

        return GarageState(
            name = "Left Garage Door",
            entityId = sw?.entity_id ?: cover?.entity_id ?: "switch.shelly1_e8db84d7217d",
            isOpen = isOpen,
            statusText = stateText
        )
    }

    fun getWaterHeaterState(): WaterHeaterState {
        val s = states.value
        val whEntity = s["water_heater.heat_pump_water_heater"]
        val modeSelect = s["input_select.water_heater_mode"]
        val hotWaterSensor = s["sensor.heat_pump_water_heater_available_hot_water"]

        val mode = modeSelect?.state ?: whEntity?.state ?: "heat_pump"
        val formattedMode = when (mode.lowercase(Locale.US)) {
            "eco" -> "Eco"
            "heat_pump" -> "Heat Pump"
            "high_demand" -> "High Demand"
            else -> mode.replace("_", " ").replaceFirstChar { it.uppercase() }
        }

        val percentVal = hotWaterSensor?.state?.toDoubleOrNull()
            ?: whEntity?.getDoubleAttribute("available_hot_water")
            ?: 100.0

        val currentTemp = whEntity?.getDoubleAttribute("current_temperature")
        val targetTemp = whEntity?.getDoubleAttribute("temperature")

        return WaterHeaterState(
            mode = formattedMode,
            rawMode = mode,
            availablePercent = percentVal.toInt().coerceIn(0, 100),
            currentTemp = currentTemp,
            targetTemp = targetTemp
        )
    }

    fun getPoolState(): PoolCarState {
        val s = states.value
        val tempSensor = s["sensor.my_pool_water_temperature"]
            ?: s["sensor.pool_water_temperature"]
            ?: s["sensor.pool_temperature"]

        val statusSensor = s["sensor.my_pool_water_status"]
            ?: s["sensor.pool_water_status"]

        val pumpSwitch = s["switch.pool_pump"]

        val temp = tempSensor?.state?.toDoubleOrNull()
        val status = statusSensor?.state ?: "Normal"
        val isPumpOn = pumpSwitch?.state?.equals("on", ignoreCase = true) ?: false

        return PoolCarState(
            waterTemp = temp,
            statusBadge = status.replaceFirstChar { it.uppercase() },
            isPumpOn = isPumpOn
        )
    }

    fun getHouseOverviewState(): HouseOverviewState {
        val s = states.value

        val scheduleState = s["input_select.house_schedule_state"]?.state?.replaceFirstChar { it.uppercase() } ?: "Day"
        val hvacMode = s["input_select.global_hvac_mode"]?.state?.uppercase(Locale.US) ?: "HEAT"

        val outdoorTemp = s["sensor.outdoor_temperature"]?.state?.toDoubleOrNull()
            ?: s["sensor.outside_temperature"]?.state?.toDoubleOrNull()
            ?: s["weather.home"]?.getDoubleAttribute("temperature")
            ?: s["weather.forecast_home"]?.getDoubleAttribute("temperature")

        // Compute average indoor temp from zones or fallback sensor
        val indoorTemps = listOfNotNull(
            s["sensor.living_room_temperature"]?.state?.toDoubleOrNull()
                ?: s["climate.hp_living_room"]?.getDoubleAttribute("current_temperature"),
            s["climate.hp_dining_room"]?.getDoubleAttribute("current_temperature"),
            s["climate.hp_bedroom_1"]?.getDoubleAttribute("current_temperature"),
            s["climate.hp_bedroom_2"]?.getDoubleAttribute("current_temperature"),
            s["climate.hp_master_bedroom"]?.getDoubleAttribute("current_temperature"),
            s["climate.hp_basement"]?.getDoubleAttribute("current_temperature")
        )

        val indoorAvg = if (indoorTemps.isNotEmpty()) {
            indoorTemps.average()
        } else {
            s["sensor.living_room_temperature"]?.state?.toDoubleOrNull()
        }

        return HouseOverviewState(
            scheduleState = scheduleState,
            globalHvacMode = hvacMode,
            outdoorTemp = outdoorTemp,
            indoorAvgTemp = indoorAvg
        )
    }

    /**
     * Sources zones from the same OTA-updatable layout_config.json the phone dashboard uses,
     * instead of a separate hardcoded list, so Android Auto can never drift out of sync with
     * the real zone/entity mapping after a layout config push.
     */
    fun getClimateZones(): List<ZoneCarState> {
        val s = states.value
        val configZones = com.example.viewmodel.HvacViewModel.getInstance()?.getActiveLayoutConfig()?.zones

        if (!configZones.isNullOrEmpty()) {
            return configZones.map { zone ->
                val climate = s[zone.climateEntityId]
                ZoneCarState(
                    key = zone.key,
                    name = zone.name,
                    climateEntityId = zone.climateEntityId,
                    currentTemp = climate?.getDoubleAttribute("current_temperature"),
                    targetTemp = climate?.getDoubleAttribute("temperature"),
                    hvacMode = climate?.state?.uppercase(Locale.US) ?: "OFF",
                    fanMode = climate?.getStringAttribute("fan_mode") ?: "Auto"
                )
            }
        }

        // Fallback for the rare case the ViewModel hasn't been constructed yet (e.g. the car
        // head unit launches this service before the phone app has run this boot cycle).
        // Matches the app's built-in default layout_config.json zone list.
        val fallbackZones = listOf(
            Triple("main_level", "Main Level", "climate.hp_living_room"),
            Triple("anthony", "Anthony", "climate.hp_anthony"),
            Triple("autumn", "Autumn", "climate.hp_autumn"),
            Triple("bedroom_1", "Master 1", "climate.hp_bedroom"),
            Triple("bedroom_2", "Master 2", "climate.hp_bedroom_2"),
            Triple("basement", "Basement", "climate.hp_basement")
        )
        return fallbackZones.map { (key, name, entityId) ->
            val climate = s[entityId]
            ZoneCarState(
                key = key,
                name = name,
                climateEntityId = entityId,
                currentTemp = climate?.getDoubleAttribute("current_temperature"),
                targetTemp = climate?.getDoubleAttribute("temperature"),
                hvacMode = climate?.state?.uppercase(Locale.US) ?: "OFF",
                fanMode = climate?.getStringAttribute("fan_mode") ?: "Auto"
            )
        }
    }
}

// Data models for Android Auto presentation
data class GarageState(
    val name: String,
    val entityId: String,
    val isOpen: Boolean,
    val statusText: String
)

data class WaterHeaterState(
    val mode: String,
    val rawMode: String,
    val availablePercent: Int,
    val currentTemp: Double?,
    val targetTemp: Double?
)

data class PoolCarState(
    val waterTemp: Double?,
    val statusBadge: String,
    val isPumpOn: Boolean
)

data class HouseOverviewState(
    val scheduleState: String,
    val globalHvacMode: String,
    val outdoorTemp: Double?,
    val indoorAvgTemp: Double?
)

data class ZoneCarState(
    val key: String,
    val name: String,
    val climateEntityId: String,
    val currentTemp: Double?,
    val targetTemp: Double?,
    val hvacMode: String,
    val fanMode: String
)
