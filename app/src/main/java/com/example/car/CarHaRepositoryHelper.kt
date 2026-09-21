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

    /**
     * Entity states for the car screens.
     *
     * Prefers the ViewModel's processed map, which is fed by whichever transport delivered the
     * states. Binding straight to `wsManager.states` leaves this empty whenever the app is on
     * REST fallback, and an empty map renders every zone as OFF — a fabricated reading the car
     * would then let you act on. Falls back to the socket only when no ViewModel exists yet,
     * which is the case if Android Auto starts before the phone UI has ever run.
     */
    val states: StateFlow<Map<String, EntityState>> =
        com.example.viewmodel.HvacViewModel.getInstance()?.entityStates ?: wsManager.states
    val connectionState: StateFlow<HaConnectionState> = wsManager.connectionState

    init {
        initializeConnection()
    }

    /**
     * Connect to Home Assistant using configured credentials from preferences
     */
    fun initializeConnection() {
        // Same resolution the ViewModel uses. Reading prefs alone returns "" on a panel whose
        // credentials come from .env rather than the login screen, and the guard below then
        // skipped the connect entirely — which is why the car logged "No HA credentials found"
        // and never connected on devices that were working perfectly well otherwise.
        val buildUrl = try { com.example.BuildConfig.HA_URL } catch (e: Exception) { "" }
        val buildToken = try { com.example.BuildConfig.HA_TOKEN } catch (e: Exception) { "" }
        val url = (prefs.getString("ha_url", null)
            ?: buildUrl.takeIf { it.isNotBlank() && it != "https://localhost/" }).orEmpty()
        val backupUrl = (prefs.getString("backup_ha_url", null)
            ?: try { com.example.BuildConfig.HA_BACKUP_URL } catch (e: Exception) { "" }).orEmpty()
        val token = if (buildToken.isNotBlank() && buildToken != "YOUR_HOME_ASSISTANT_TOKEN") {
            buildToken
        } else {
            prefs.getString("ha_token", null).orEmpty()
        }

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
    fun cycleWaterHeaterMode(onComplete: ((String?) -> Unit)? = null) {
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

            // Both results used to be discarded and onComplete invoked unconditionally, so the
            // car toasted "Water Heater: HIGH_DEMAND" whether or not either call landed — the
            // same defect already fixed in cycleHouseScheduleState. Report null on failure so
            // the caller can say so.
            val applied = if (success) {
                true
            } else {
                callService(
                    domain = "water_heater",
                    service = "set_operation_mode",
                    entityId = "water_heater.heat_pump_water_heater",
                    serviceData = mapOf("operation_mode" to nextMode)
                )
            }
            onComplete?.invoke(if (applied) nextMode else null)
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

    // cycleGlobalHvacMode was removed here. It had no callers anywhere in app/src, and it carried
    // both of the defects that were deliberately fixed in cycleHouseScheduleState and
    // toggleZoneHvacMode in this same file: it defaulted a missing live reading to "heat", and it
    // reported success unconditionally by ignoring callService's result. Wired to any car surface
    // it would have flipped the whole house's thermal family from one tap on a stale screen and
    // said it worked. Deleted rather than left as a trap for whoever wires up the next row.

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
                // fan_only and auto are real modes these heads support, and the old `else`
                // jumped straight to HEAT from either. Treat them as "already running" and
                // continue the cycle rather than committing a compressor from one tap.
                "fan_only", "auto" -> "off"
                else -> "off"
            }

            // The SAME rule the panel applies, not a weaker copy of half of it. This used to
            // scan only for a sibling head on the same outdoor unit — the second of the two
            // tiers in findModeConflict — and omitted the first, where a heat/cool house mode
            // is the blocker outright. So with the house in HEAT and Autumn the only head
            // running on unit 2, the car would happily send COOL: a change the panel refuses
            // and explains, and which the watchdog then reverses by shutting the zone down.
            //
            // Feeding one snapshot per HEAD also means main level is evaluated as the two heads
            // it actually is, on both units, rather than through its single configured entity.
            val zoneKey = com.example.model.headZoneKey[climateEntityId]
            if (zoneKey != null) {
                val snapshots = com.example.model.headZoneKey.map { (head, zk) ->
                    com.example.model.ZoneModeSnapshot(
                        key = zk,
                        name = states.value[head]?.getStringAttribute("friendly_name") ?: zk,
                        mode = states.value[head]?.state ?: "unavailable"
                    )
                }
                val globalMode = states.value["input_select.global_hvac_mode"]?.state ?: "off"
                val conflict = com.example.model.findModeConflict(
                    snapshots, globalMode, zoneKey, nextMode
                )
                if (conflict != null) {
                    onComplete?.invoke(
                        ZoneModeResult(
                            false,
                            "Blocked: ${conflict.blockedBy} is ${conflict.blockingMode.uppercase(Locale.US)}"
                        )
                    )
                    return@launch
                }
            }

            // A powered-down head ignores set_hvac_mode, so power it up first, the same order
            // n8n's sequencer uses. The result used to be discarded and the wait was a fixed
            // 1.2s sleep — shorter than the Airstage integration's ~10s report cadence, so the
            // mode routinely landed on a head that was still asleep and was dropped, while the
            // driver was toasted a success.
            if (live == "off" && nextMode != "off") {
                val powered = callService(
                    domain = "climate", service = "turn_on", entityId = climateEntityId
                )
                if (!powered) {
                    onComplete?.invoke(ZoneModeResult(false, "Could not power on"))
                    return@launch
                }
                awaitHeadState(climateEntityId) { it != "off" }
            }

            val success = callService(
                domain = "climate",
                service = "set_hvac_mode",
                entityId = climateEntityId,
                serviceData = mapOf("hvac_mode" to nextMode)
            )
            if (!success) {
                onComplete?.invoke(ZoneModeResult(false, "Change failed"))
                return@launch
            }

            // These heads carry ONE setpoint across modes, so a head brought up in heat after
            // last cooling holds the cool number — a gap of ten degrees on some zones, which the
            // watchdog reads as a manual adjustment and suspends the zone for within a minute.
            // The panel has sent the scheduled setpoint since ae019a5; the car never did, so the
            // two surfaces left the system in different states for the same user action.
            if (zoneKey != null && nextMode != "off" && awaitHeadState(climateEntityId) { it == nextMode }) {
                val sched = states.value["input_select.house_schedule_state"]?.state ?: ""
                com.example.model.presetHelperId(zoneKey, sched, nextMode)?.let { helperId ->
                    states.value[helperId]?.state?.toDoubleOrNull()?.let { raw ->
                        val wanted = com.example.model.clampToHardwareFloor(raw, nextMode)
                        val current = states.value[climateEntityId]
                            ?.getDoubleAttribute("temperature")
                        if (current == null || kotlin.math.abs(current - wanted) > 0.6) {
                            callService(
                                domain = "climate",
                                service = "set_temperature",
                                entityId = climateEntityId,
                                serviceData = mapOf("temperature" to wanted)
                            )
                        }
                    }
                }
            }

            onComplete?.invoke(ZoneModeResult(true, nextMode.uppercase(Locale.US)))
        }
    }

    /**
     * Waits for a head to report a state satisfying [predicate], or gives up.
     *
     * The car used a fixed `delay(1200)` here. These heads sit behind the Airstage cloud and
     * report on roughly a 10s round robin, so that sleep was shorter than a single reporting
     * interval and the following command routinely landed on a head that had not moved yet.
     */
    private suspend fun awaitHeadState(
        climateEntityId: String,
        timeoutMs: Long = 15000,
        predicate: (String) -> Boolean
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val s = states.value[climateEntityId]?.state?.lowercase(Locale.US)
            if (s != null && predicate(s)) return true
            kotlinx.coroutines.delay(250)
        }
        return false
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
