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
     * The doors the dashboard is configured with, in order. These used to be hardcoded, and
     * the identifiers had drifted: the "south" door pointed at cover.garage_door_south, which
     * has never existed in this install, so the car silently fell through to pulsing the raw
     * relay while reporting success. Reading the same config the panel uses keeps the car
     * correct when doors are added or replaced.
     */
    private fun configuredCovers(): List<com.example.model.CoverControlConfig> {
        // The car app routinely starts cold, without the phone UI ever having run, so the
        // ViewModel singleton may not exist yet. Fall back to the shipped defaults rather
        // than showing the driver an empty, unusable list.
        val fromConfig = com.example.viewmodel.HvacViewModel.getInstance()
            ?.getActiveLayoutConfig()?.covers
        if (!fromConfig.isNullOrEmpty()) return fromConfig
        return listOf(
            com.example.model.CoverControlConfig("cover.konnected_d332ec_garage_door", "Garage South"),
            com.example.model.CoverControlConfig("cover.garage_garage_door_north_garage_door", "Garage North"),
            com.example.model.CoverControlConfig("switch.shellyplus1_b8d61a8a78b0_switch_0", "Workshop")
        )
    }

    private fun coverAt(index: Int): com.example.model.CoverControlConfig? =
        configuredCovers().getOrNull(index)

    private suspend fun toggleConfiguredCover(index: Int): Boolean {
        val cover = coverAt(index) ?: return false
        val domain = cover.entityId.substringBefore('.', "cover")
        // A relay-backed door only accepts a pulse; a real cover entity accepts toggle.
        return callService(domain, "toggle", cover.entityId)
    }

    fun toggleSouthGarage(onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch { onComplete?.invoke(toggleConfiguredCover(0)) }
    }

    fun toggleLeftGarage(onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch { onComplete?.invoke(toggleConfiguredCover(1)) }
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
    /**
     * The helper's options are capitalised — Day, Night, Away — and `input_select.select_option`
     * matches them exactly, so a lowercase option is rejected outright. The result is reported
     * rather than assumed: this call used to invoke the callback unconditionally, which told the
     * driver the schedule had changed every time it had not.
     *
     * A null in the callback means the write failed.
     */
    fun cycleHouseScheduleState(onComplete: ((String?) -> Unit)? = null) {
        scope.launch {
            val currentStates = states.value
            val currentState = currentStates["input_select.house_schedule_state"]?.state?.lowercase(Locale.US) ?: "day"
            val nextState = when (currentState) {
                "day" -> "Night"
                "night" -> "Away"
                "away" -> "Day"
                else -> "Day"
            }

            val success = callService(
                domain = "input_select",
                service = "select_option",
                entityId = "input_select.house_schedule_state",
                serviceData = mapOf("option" to nextState)
            )
            onComplete?.invoke(if (success) nextState else null)
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

    /** What a car-surface mode tap actually did, so the driver is told the truth. */
    data class ZoneModeResult(val applied: Boolean, val message: String)

    /**
     * Cycle one zone's mode from the car: HEAT -> COOL -> OFF -> HEAT.
     *
     * Two guards the touch panel has and this surface did not:
     *
     * The mode is re-read live rather than taken from the caller. The car screens render from
     * the WebSocket map, which is empty whenever the app is on REST fallback, and an absent
     * entity was being rendered as "OFF" — so one tap on a disconnected car screen sent HEAT to
     * a head regardless of what it was really doing.
     *
     * A head is refused if another head on the SAME outdoor unit is already running the other
     * thermal family, which is the check `detectModeConflict` performs on the panel. There is no
     * override path here: a driver should not be arbitrating house-wide HVAC conflicts.
     */
    fun toggleZoneHvacMode(
        climateEntityId: String,
        @Suppress("UNUSED_PARAMETER") currentMode: String,
        onComplete: ((ZoneModeResult) -> Unit)? = null
    ) {
        scope.launch {
            val live = states.value[climateEntityId]?.state?.lowercase(Locale.US)
            if (live == null || live == "unavailable" || live == "unknown") {
                onComplete?.invoke(ZoneModeResult(false, "No live reading — not changed"))
                return@launch
            }

            val nextMode = when (live) {
                "heat" -> "cool"
                "cool", "dry" -> "off"
                "off" -> "heat"
                else -> "heat"
            }

            val requested = com.example.model.hvacFamilyOf(nextMode)
            if (requested != com.example.model.HvacFamily.NEUTRAL) {
                val unit = com.example.model.headOutdoorUnit[climateEntityId]
                val blocker = com.example.model.headOutdoorUnit.entries.firstOrNull { (head, u) ->
                    head != climateEntityId && u == unit &&
                        com.example.model.hvacFamilyOf(states.value[head]?.state).let {
                            it != com.example.model.HvacFamily.NEUTRAL && it != requested
                        }
                }?.key
                if (blocker != null) {
                    val blockerName = states.value[blocker]?.getStringAttribute("friendly_name") ?: blocker
                    val blockerMode = states.value[blocker]?.state?.uppercase(Locale.US) ?: "?"
                    onComplete?.invoke(
                        ZoneModeResult(false, "Blocked: $blockerName is $blockerMode on the same unit")
                    )
                    return@launch
                }
            }

            // A powered-down head ignores set_hvac_mode, so power it up first and give it a
            // moment, the same order n8n's sequencer uses.
            if (live == "off" && nextMode != "off") {
                callService(domain = "climate", service = "turn_on", entityId = climateEntityId)
                kotlinx.coroutines.delay(1200)
            }

            val success = callService(
                domain = "climate",
                service = "set_hvac_mode",
                entityId = climateEntityId,
                serviceData = mapOf("hvac_mode" to nextMode)
            )
            onComplete?.invoke(
                if (success) ZoneModeResult(true, nextMode.uppercase(Locale.US))
                else ZoneModeResult(false, "Change failed")
            )
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

    private fun garageStateAt(index: Int, fallbackName: String): GarageState {
        val cover = coverAt(index)
            ?: return GarageState(fallbackName, "", isOpen = false, statusText = "UNAVAILABLE")
        val entity = states.value[cover.entityId]
        val raw = entity?.state

        // A relay-backed door reports its own on/off, which says nothing about the door, so
        // it is reported as UNKNOWN rather than being dressed up as OPEN or CLOSED.
        val isCover = cover.entityId.startsWith("cover.")
        val isOpen = isCover && (raw.equals("open", true) || raw.equals("opening", true))
        val statusText = when {
            raw == null -> "UNAVAILABLE"
            isCover -> raw.uppercase(Locale.US)
            else -> "UNKNOWN"
        }

        return GarageState(
            name = cover.name,
            entityId = cover.entityId,
            isOpen = isOpen,
            statusText = statusText
        )
    }

    fun getSouthGarageState(): GarageState = garageStateAt(0, "Garage Door 1")

    fun getLeftGarageState(): GarageState = garageStateAt(1, "Garage Door 2")

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

        // Missing used to mean 100.0, so a dead sensor reported a full tank — the one direction
        // this reading must never fail in.
        val percentVal = hotWaterSensor?.state?.toDoubleOrNull()
            ?: whEntity?.getDoubleAttribute("available_hot_water")

        val currentTemp = whEntity?.getDoubleAttribute("current_temperature")
        val targetTemp = whEntity?.getDoubleAttribute("temperature")

        return WaterHeaterState(
            mode = formattedMode,
            rawMode = mode,
            availablePercent = percentVal?.toInt()?.coerceIn(0, 100),
            currentTemp = currentTemp,
            targetTemp = targetTemp
        )
    }

    fun getPoolState(): PoolCarState {
        val s = states.value
        // The sensor.pool_water_temperature / sensor.pool_temperature / sensor.pool_water_status
        // fallbacks that used to sit here do not exist in HA and never have.
        val tempSensor = s["sensor.my_pool_water_temperature"]
        val statusSensor = s["sensor.my_pool_water_status"]
        val pumpSwitch = s["switch.pool_pump"]

        val temp = tempSensor?.state?.toDoubleOrNull()
        // A missing status used to display as "Normal", so an offline monitor reported a healthy
        // pool. Say nothing rather than vouch for water nobody has measured.
        val status = statusSensor?.state?.takeIf {
            !it.equals("unavailable", true) && !it.equals("unknown", true)
        } ?: "No data"
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

        // Average across all seven heads. This list previously named climate.hp_bedroom_1 and
        // climate.hp_master_bedroom, neither of which exists, and left out Anthony and Autumn —
        // so the car averaged four real rooms, two phantoms and two omissions.
        val indoorTemps = listOfNotNull(
            s["sensor.living_room_temperature"]?.state?.toDoubleOrNull()
                ?: s["climate.hp_living_room"]?.getDoubleAttribute("current_temperature"),
            s["climate.hp_dining_room"]?.getDoubleAttribute("current_temperature"),
            s["climate.hp_anthony"]?.getDoubleAttribute("current_temperature"),
            s["climate.hp_autumn"]?.getDoubleAttribute("current_temperature"),
            s["climate.hp_bedroom"]?.getDoubleAttribute("current_temperature"),
            s["climate.hp_bedroom_2"]?.getDoubleAttribute("current_temperature"),
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
    /** Null when no reading is available. Never assume a full tank. */
    val availablePercent: Int?,
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
