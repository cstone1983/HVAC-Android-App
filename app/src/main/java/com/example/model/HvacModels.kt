package com.example.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HvacThemeConfig(
    val accentColorHex: String? = "#F59E0B",
    // Heat used to borrow accentColorHex, so a green accent painted "heating" green while the
    // quick-control row drew it orange. Heat now has a colour of its own; when it is absent the
    // accent is still used, which keeps the built-in theme presets looking as they always did.
    val heatColorHex: String? = null,
    val coolColorHex: String? = "#2196F3",
    val dryColorHex: String? = "#8B5CF6",
    val offColorHex: String? = "#64748B",
    val bgStartColorHex: String? = "#0F172A",
    val bgEndColorHex: String? = "#1E293B",
    val glowColorHex: String? = "#2196F3",
    val glowAlpha: Float? = 0.12f,
    val chartShadingAlpha: Float? = 0.05f,
    val showChartShading: Boolean? = true
)

@JsonClass(generateAdapter = true)
data class TabConfig(
    val id: String,
    val title: String,
    val icon: String,
    val sections: List<String>,
    // Short label used where horizontal space is tight (the phone's bottom navigation bar).
    // Falls back to the first word of `title` when omitted.
    val shortTitle: String? = null,
    /**
     * Keeps a finished tab out of the navigation until it is wanted.
     *
     * The tab's rendering code still ships in the app; only its button is withheld. That means
     * revealing it later is a change to `layout_config.json` alone — flip this to false, push, and
     * the panels pick it up on their next update check. No rebuild, no reinstall, and hiding it
     * again is the same one-word change. Deleting the entry instead would work identically, but
     * leaving it here documents the tab and makes turning it on a flag rather than a rewrite.
     */
    val hidden: Boolean = false
)

@JsonClass(generateAdapter = true)
data class DynamicChartPointConfig(
    val label: String,
    val value: Float
)

@JsonClass(generateAdapter = true)
data class DynamicStatConfig(
    val label: String,
    val value: String,
    val icon: String? = null,
    val tintColor: String? = null,
    // If entityId is set, the tile shows the entity's live state (or attribute) instead of the
    // static `value` above — `value` remains the fallback shown before the first state arrives.
    val entityId: String? = null,
    val attribute: String? = null,
    val unit: String? = null
)

@JsonClass(generateAdapter = true)
data class DynamicCardConfig(
    val type: String, // "placeholder", "stats_row", "chart", "entity_toggle", "action_button", "live_chart"
    val title: String? = null,
    val subtitle: String? = null,
    val icon: String? = null,
    val tintColor: String? = null, // "eco", "cool", "heating", "accent", etc.
    val statusText: String? = null,
    val stats: List<DynamicStatConfig>? = null,
    val chartData: List<DynamicChartPointConfig>? = null,
    // entity_toggle: binds a single switch/light to entityId, calling turn_on/turn_off on the
    // domain inferred from the entity_id prefix (or `domain` if given).
    val entityId: String? = null,
    // action_button: calls `domain`.`service` (with optional serviceData) against `entityId` on tap.
    val domain: String? = null,
    val service: String? = null,
    val serviceData: Map<String, String>? = null,
    // live_chart: a real history line chart for `entityId` over `historyRange` ("6h", "24h", or "7d").
    val historyRange: String? = null,
    val unit: String? = null
)

@JsonClass(generateAdapter = true)
data class DynamicSectionConfig(
    val id: String,
    val title: String? = null,
    val cards: List<DynamicCardConfig> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SystemLimitsConfig(
    val minCoolingTemp: Double? = 64.0,
    val maxHeatingTemp: Double? = 80.0
)

@JsonClass(generateAdapter = true)
data class PoolSensorConfig(
    val waterTemperatureEntityId: String? = "sensor.my_pool_water_temperature",
    val phEntityId: String? = "sensor.my_pool_ph",
    val orpEntityId: String? = "sensor.my_pool_orp",
    val batteryEntityId: String? = "sensor.my_pool_battery",
    val lastSyncedEntityId: String? = "sensor.my_pool_last_synced",
    val lastUpdatedEntityId: String? = "sensor.my_pool_last_updated",
    val monitorSerialEntityId: String? = "sensor.my_pool_monitor_serial",
    val sensorSerialEntityId: String? = "sensor.my_pool_sensor_serial",
    val wifiSignalEntityId: String? = "sensor.my_pool_wifi_signal",
    val waterStatusEntityId: String? = "sensor.my_pool_water_status",
    val actionsPendingEntityId: String? = "sensor.my_pool_actions_pending",
    val pumpSwitchEntityId: String? = "switch.pool_pump",
    val tempLowLimitEntityId: String? = "input_number.pool_temp_low_limit",
    val tempHighLimitEntityId: String? = "input_number.pool_temp_high_limit",
    val phLowLimitEntityId: String? = "input_number.pool_ph_low_limit",
    val phHighLimitEntityId: String? = "input_number.pool_ph_high_limit",
    val orpLowLimitEntityId: String? = "input_number.pool_orp_low_limit",
    val orpHighLimitEntityId: String? = "input_number.pool_orp_high_limit",
    val batteryLowLimitEntityId: String? = "input_number.pool_battery_low_limit",
    val batteryHighLimitEntityId: String? = "input_number.pool_battery_high_limit"
)

@JsonClass(generateAdapter = true)
data class SolarSensorConfig(
    val usagePowerEntityId: String? = "sensor.basement_ct_panel_total_active_power",
    val phaseAPowerEntityId: String? = "sensor.imeter_2pn_phase_a_power",
    val phaseBPowerEntityId: String? = "sensor.imeter_2pn_phase_b_power",
    val usageEnergyEntityId: String? = "sensor.basement_ct_panel_total_forward_active_energy",
    val productionEnergyEntityId: String? = "sensor.imeter_2pn_total_production",
    val diagSolarGenerationEntityId: String? = "sensor.exterior_imeter_2pn_solar_generation",
    val diagSolarProductionDailyEntityId: String? = "sensor.exterior_imeter_2pn_solar_production_daily",
    val diagPanelUsageEntityId: String? = "sensor.basement_ct_panel_panel_usage",
    val diagDailyPanelUsageEntityId: String? = "sensor.basement_ct_panel_daily_panel_usage",
    val solcastForecastTodayEntityId: String? = "sensor.solcast_solar_enhanced_forecast_today",
    val solcastForecastNowEntityId: String? = "sensor.solcast_solar_enhanced_forecast_now",
    val gridUsageL1EntityId: String? = "sensor.daily_grid_usage_l1",
    val gridUsageL2EntityId: String? = "sensor.daily_grid_usage_l2",
    val gridExportL1EntityId: String? = "sensor.daily_grid_export_l1",
    val gridExportL2EntityId: String? = "sensor.daily_grid_export_l2",
    val solarGenL1EntityId: String? = "sensor.daily_solar_generation_l1",
    val solarGenL2EntityId: String? = "sensor.daily_solar_generation_l2",
    val cmpBankBalanceEntityId: String? = "sensor.cmp_bank_balance"
)

@JsonClass(generateAdapter = true)
data class HumiditySensorConfig(
    val name: String,
    val entityId: String,
    // Optional attribute to read instead of the entity's own state (e.g. "current_humidity"
    // on a climate entity).
    val attribute: String? = null
)

@JsonClass(generateAdapter = true)
data class DoorAlertConfig(
    val name: String,
    // A binary_sensor / cover whose state tells the truth about the door position. This is
    // deliberately separate from the switch that operates it, because a relay's on/off says
    // nothing about whether the door is open.
    val stateEntityId: String,
    val openStates: List<String>? = listOf("on", "open", "opening")
)

@JsonClass(generateAdapter = true)
data class QuickActionConfig(
    val label: String,
    val icon: String? = null,
    val domain: String,
    val service: String,
    val entityId: String? = null,
    val confirm: Boolean? = false
)

/**
 * Everything the home screen shows that isn't a zone: the outdoor reading, humidity, who's
 * home, door alerts and the one-tap scripts. All of it is entity-driven so it stays editable
 * over the air.
 */
@JsonClass(generateAdapter = true)
data class HomeStatusConfig(
    val outdoorTempEntityId: String? = null,
    val humiditySensors: List<HumiditySensorConfig>? = null,
    val presenceEntityIds: List<String>? = null,
    val doorAlerts: List<DoorAlertConfig>? = null,
    val quickActions: List<QuickActionConfig>? = null,
    // Room sensors that already appear as zone cards; hidden from the secondary room strip so
    // the same room never shows two different temperatures on one screen.
    val hideRoomSensorIds: List<String>? = null,
    // How far back the who's-home popup looks for a real arrival/departure. Should track
    // Home Assistant's recorder purge_keep_days: querying further back than the recorder
    // retains just reports "no change recorded" for everyone.
    val presenceHistoryDays: Int? = 30
)

@JsonClass(generateAdapter = true)
data class HvacLayoutConfig(
    val version: String,
    val roomSensors: List<RoomSensorConfig>,
    val zones: List<ClimateZoneConfig>,
    val lights: List<LightControlConfig>,
    val switches: List<SwitchControlConfig>,
    val covers: List<CoverControlConfig>,
    val appTitle: String? = "Home Control",
    val appSubtitle: String? = "HVAC SYSTEM CONTROLLER",
    val theme: HvacThemeConfig? = HvacThemeConfig(),
    val limits: SystemLimitsConfig? = SystemLimitsConfig(),
    val waterHeaterEntityId: String? = "input_select.water_heater_mode",
    val waterHeaterFullnessEntityId: String? = "sensor.heat_pump_water_heater_available_hot_water",
    // Binary sensor that reports whether the tank is actually heating right now, so the card
    // can explain itself instead of appearing to change mode on its own.
    val waterHeaterRunningEntityId: String? = "binary_sensor.heat_pump_water_heater_running",
    val poolSensors: PoolSensorConfig? = PoolSensorConfig(),
    val solarSensors: SolarSensorConfig? = SolarSensorConfig(),
    val alarm: AlarmConfig? = AlarmConfig(),
    val homeStatus: HomeStatusConfig? = HomeStatusConfig(),
    val dynamicSections: List<DynamicSectionConfig>? = emptyList(),
    val showWeatherCard: Boolean? = true,
    // Idle behaviour, in seconds. Both were hardcoded to 30, which was too short to read a
    // chart and short enough to close a popup while it was being used.
    val idleReturnSeconds: Int? = 20,
    val popupTimeoutSeconds: Int? = 20,
    val weatherLatitude: Double? = 37.7749,
    val weatherLongitude: Double? = -122.4194,
    val tabs: List<TabConfig>? = listOf(
        TabConfig("zones", "ZONES & UNITS", "layers", listOf("sensors", "zones")),
        TabConfig("aux", "POWER CONTROL", "lightbulb", listOf("condensed_power")),
        TabConfig("solar", "SOLAR & ENERGY", "solar_power", listOf("solar")),
        TabConfig("pool", "POOL DATA", "pool", listOf("pool")),
        TabConfig("updates", "UPDATES", "cloud_download", listOf("updates"))
    )
)

@JsonClass(generateAdapter = true)
data class RoomSensorConfig(
    val id: String,
    val name: String,
    val stateId: String,
    val attributeName: String? = null,
    val unit: String = "°F"
)

@JsonClass(generateAdapter = true)
data class PresetsConfig(
    val day: String,
    val night: String,
    val away: String
)

@JsonClass(generateAdapter = true)
data class ClimateZoneConfig(
    val key: String,
    val name: String,
    val climateEntityId: String,
    /**
     * A second head belonging to the same zone, if it has one.
     *
     * Main level is one open space served by two heads on two different outdoor units, kept
     * mirrored by the sequencer. The app knew about only the living-room head, which had two
     * consequences: the card could not show a split between them (the 40-minute split on
     * 2026-09-20 was invisible on the wall), and the conflict guard read the zone's mode from
     * the living-room head alone — so when the DINING head was the one committed to a family,
     * the guard reported no conflict and let a clashing mode through to the other unit.
     */
    val secondaryClimateEntityId: String? = null,
    /**
     * A sensor that measures the room, used in preference to the head's own reading.
     *
     * The heads measure their own return air, and that thermistor sits inside the casing of a
     * unit mounted high on the wall. While the fan is off nothing draws room air over it, so it
     * reads the warm air trapped in its own housing plus whatever the control board gives off,
     * and it climbs the longer the unit stays idle. Measured 2026-09-23 with the house off for
     * two days: the living-room head reported 92 °F in a 71.9 °F room, and Master 2 reported
     * 95 °F. It corrects itself within about two minutes of the fan starting — on 2026-09-21 the
     * bedroom head fell from 80.2 to 75.7 in the two minutes after it switched to heat — which is
     * why this has always been present (5–9 °F most days) but only became obvious during a long
     * shutdown.
     *
     * Null leaves the zone on the head's own reading, which is the only option for a room with no
     * sensor of its own.
     */
    val roomTemperatureEntityId: String? = null,
    /**
     * Attribute to read from [roomTemperatureEntityId] instead of its state — for a thermostat
     * such as `climate.basement_thermostat`, where the temperature is `current_temperature` and
     * the state is the hvac mode.
     */
    val roomTemperatureAttribute: String? = null,
    val autoEntityId: String,
    val overrideEntityId: String,
    val tiltEntityId: String,
    val fanEntityId: String,
    val presetsHeat: PresetsConfig,
    val presetsCool: PresetsConfig
)

@JsonClass(generateAdapter = true)
data class LightControlConfig(
    val entityId: String,
    val name: String
)

@JsonClass(generateAdapter = true)
data class SwitchControlConfig(
    val entityId: String,
    val name: String
)

@JsonClass(generateAdapter = true)
data class CoverControlConfig(
    val entityId: String,
    val name: String
)

data class RoomSensor(
    val id: String,
    val name: String,
    val stateId: String,
    val attributeName: String? = null,
    val temp: Double? = null,
    val unit: String = "°F"
)

data class Presets(
    val day: String,
    val night: String,
    val away: String,
    val dayValue: Double? = null,
    val nightValue: Double? = null,
    val awayValue: Double? = null
)

data class ClimateZone(
    val key: String,
    val name: String,
    val climateEntityId: String,
    val autoEntityId: String,
    val overrideEntityId: String,
    val tiltEntityId: String,
    val fanEntityId: String,
    val presetsHeat: Presets,
    val presetsCool: Presets,

    // Parsed properties from fetched entities
    val currentTemp: Double? = null,
    val targetTemp: Double? = null,
    val currentHvacMode: String = "off",
    // Home Assistant's `hvac_action` where the device reports it (the thermostats do; the
    // Fujitsu heads do not). Null means we have to infer activity from the temperatures.
    val hvacAction: String? = null,
    val autoOn: Boolean = false,
    val overrideOn: Boolean = false,
    /** The zone's second head, where it has one. See [ClimateZoneConfig.secondaryClimateEntityId]. */
    val secondaryClimateEntityId: String? = null,
    val secondaryHvacMode: String? = null,
    val secondaryTargetTemp: Double? = null,
    val vaneMode: String = "Auto",
    val fanMode: String = "Auto",
    // Fallbacks for when the helper is missing from the state map. These are the real option
    // lists the tilt/fan helpers carry, verified against live Home Assistant on 2026-09-21.
    // The previous vane fallback was listOf("Auto", "Swing", "1".."5"), of which *not one* is a
    // value the helper accepts — so a missing helper rendered seven buttons that every
    // select_option call would reject, while the optimistic UI showed the new vane as applied.
    // The old fan fallback was valid but silently dropped "Medium".
    val vaneOptions: List<String> = listOf("Highest", "High", "Low", "Lowest", "Vertical Swing"),
    val fanOptions: List<String> = listOf("Quiet", "Low", "Medium", "High", "Auto")
) {
    /** True only while the unit should actually be moving air to reach its target. */
    val isCalling: Boolean
        get() {
            val mode = currentHvacMode.lowercase()
            if (mode in notReportingModes) return false
            hvacAction?.lowercase()?.let { action ->
                return action == "heating" || action == "cooling" || action == "drying"
            }
            val current = currentTemp ?: return false
            val target = targetTemp ?: return false
            return when (mode) {
                "heat" -> current < target - 0.5
                "cool", "dry" -> current > target + 0.5
                "fan_only" -> true
                else -> false
            }
        }

    /**
     * Short status for the card. Deliberately says nothing about which mode the zone is in:
     * the card's colour already carries that (orange heating, blue cooling, purple drying),
     * so repeating "HEATING" in text spends the only line available restating what the tint
     * just said. What colour cannot show is whether the unit is actually working, which is
     * all this reports.
     */
    /**
     * True when this zone's two heads disagree about what they are doing.
     *
     * Only meaningful for a zone that has a second head. Both heads serve one open space and are
     * kept mirrored by the sequencer, so a disagreement means the mirror has not taken — which is
     * worth saying on the card rather than quietly showing one head and hiding the other.
     */
    val isSplit: Boolean
        get() {
            val other = secondaryHvacMode?.lowercase() ?: return false
            val mine = currentHvacMode.lowercase()
            if (mine in notReportingModes || other in notReportingModes) return false
            return mine != other
        }

    val statusLabel: String
        get() {
            val mode = currentHvacMode.lowercase()
            // A split outranks everything else this can say: the two heads are doing different
            // things, so any single verdict about the zone would be picking one and hiding the
            // other. This is what made the 40-minute main-level split invisible on the wall.
            if (isSplit) return "SPLIT"
            if (mode == "off") return "OFF"
            // A head we cannot hear from says nothing about the room. This used to fall through
            // to the temperature comparison below and report "AT TARGET" for a head whose state
            // was "unknown", which reads as a reassurance nobody had verified.
            if (mode in notReportingModes) return "UNAVAILABLE"
            if (isCalling) return "RUNNING"
            if (currentTemp != null && targetTemp != null) return "AT TARGET"
            return "IDLE"
        }

    private companion object {
        /** States that mean "no usable reading", as opposed to a head deliberately switched off. */
        val notReportingModes = NOT_REPORTING_MODES
    }
}

/** States that mean "no usable reading", as opposed to a head deliberately switched off. */
val NOT_REPORTING_MODES = setOf("unavailable", "unknown", "")

/**
 * The range a room temperature has to fall in to be believed, in °F.
 *
 * Deliberately wide — this is here to reject a sensor that has failed to something absurd, not to
 * second-guess a real reading. An unheated Maine house in January and an attic in August both sit
 * comfortably inside it.
 */
private val PLAUSIBLE_ROOM_TEMP_F = -40.0..150.0

/**
 * A room-sensor reading, or null when there isn't a usable one.
 *
 * Null for a missing entity, `unavailable`/`unknown`, anything non-numeric, and anything outside
 * [PLAUSIBLE_ROOM_TEMP_F]. Callers fall back to the head's own reading, so returning null has to
 * mean "I have nothing", never "here is a guess" — a dead sensor reading 0 would otherwise replace
 * a working head and show the house at freezing.
 */
fun usableRoomTemperature(raw: String?): Double? {
    if (raw == null) return null
    val trimmed = raw.trim()
    if (trimmed.lowercase() in NOT_REPORTING_MODES) return null
    // Matched strictly rather than handed to toDoubleOrNull, which accepts Java float literals:
    // "71.9F" parses as 71.9 and "NaN"/"Infinity" parse as themselves, so a sensor reporting a
    // unit suffix would have been read as a temperature.
    if (!PLAIN_DECIMAL.matches(trimmed)) return null
    val value = trimmed.toDoubleOrNull() ?: return null
    return if (value in PLAUSIBLE_ROOM_TEMP_F) value else null
}

private val PLAIN_DECIMAL = Regex("""^[-+]?\d+(\.\d+)?$""")

/**
 * The temperature to show for a zone, given the head's own reading and a room sensor.
 *
 * Which one is right depends on whether the head is running:
 *
 *  - **Running.** The head's thermistor sits in moving return air and is measuring the room the
 *    unit is actually conditioning — the most relevant number there is, and it responds faster
 *    than a battery sensor on a several-minute reporting interval. Use it.
 *  - **Off or unreachable.** Nothing draws room air over that thermistor, so it reads the air
 *    trapped in its own casing and climbs the longer it idles. Use the room sensor.
 *
 * Either side falls back to the other when it has nothing, so a zone with no room sensor behaves
 * exactly as it did before and a failed room sensor cannot blank the card.
 */
fun zoneDisplayTemperature(
    headMode: String?,
    headReading: Double?,
    roomSensorReading: Double?
): Double? {
    val running = !isNotReporting(headMode) && headMode?.lowercase() != "off"
    return if (running) headReading ?: roomSensorReading
    else roomSensorReading ?: headReading
}

/**
 * True when a head is telling us nothing usable about itself.
 *
 * Shared deliberately. The render path and the command-dispatch path each used to carry their own
 * idea of this, and they disagreed: the card correctly showed UNAVAILABLE while `setZoneHvacMode`
 * tested only `== "off"`, so tapping a mode on an unreachable head skipped `turn_on` entirely and
 * then fired two commands at an entity that could not take them — while the banner reported
 * success. A single definition is the fix, not a second copy of the same set.
 */
fun isNotReporting(state: String?): Boolean =
    state == null || state.lowercase() in NOT_REPORTING_MODES

/**
 * The thermal families a head can be in. A multi-split serves one family at a time, and dry is
 * refrigeration on these heads, so heat + dry is a genuine conflict rather than a cosmetic one.
 */
enum class HvacFamily { HEAT, COOL, NEUTRAL }

fun hvacFamilyOf(mode: String?): HvacFamily = when (mode?.lowercase()) {
    "heat" -> HvacFamily.HEAT
    "cool", "dry" -> HvacFamily.COOL
    else -> HvacFamily.NEUTRAL
}

/**
 * Which outdoor unit each zone's head(s) hang off.
 *
 * There are two condensers, and they are independent: heat on one while the other cools is
 * physically fine. Only heads sharing a condenser can conflict. Main Level is the exception —
 * it is one open space served by two heads, the living room on unit 1 and the dining room on
 * unit 2 — so it touches both and can conflict with anything.
 */
val zoneOutdoorUnits: Map<String, Set<Int>> = mapOf(
    "main_level" to setOf(1, 2),
    "bedroom_1" to setOf(1),
    "bedroom_2" to setOf(1),
    "basement" to setOf(1),
    "anthony" to setOf(2),
    "autumn" to setOf(2)
)

/**
 * Unknown zone keys share a unit with everything, so a config addition fails safe (warns)
 * rather than silently permitting a combination the equipment cannot serve.
 */
fun outdoorUnitsFor(zoneKey: String): Set<Int> = zoneOutdoorUnits[zoneKey] ?: setOf(1, 2)

/** The same split, keyed by head, for callers that only hold a climate entity id. */
val headOutdoorUnit: Map<String, Int> = mapOf(
    "climate.hp_living_room" to 1,
    "climate.hp_bedroom" to 1,
    "climate.hp_bedroom_2" to 1,
    "climate.hp_basement" to 1,
    "climate.hp_dining_room" to 2,
    "climate.hp_anthony" to 2,
    "climate.hp_autumn" to 2
)

fun sharesOutdoorUnit(zoneKeyA: String, zoneKeyB: String): Boolean =
    outdoorUnitsFor(zoneKeyA).any { it in outdoorUnitsFor(zoneKeyB) }

/** The little a zone's identity and mode that the conflict rule actually needs. */
data class ZoneModeSnapshot(
    val key: String,
    val name: String,
    val mode: String
)

/**
 * Whether [requestedMode] can be served for [targetZoneKey] alongside whatever else is running.
 *
 * Pure so it can be tested. This is the guard that stops a head being asked for cool while its
 * condenser is heating, and it has to get three things right:
 *
 *  - The house mode outranks everything. When it disagrees it is the blocker. When it *agrees*
 *    the request is the corrective one, and a single stray head must not be allowed to veto it,
 *    or a house and a zone that disagree can never be brought back into line.
 *  - Only heads on the same outdoor unit can conflict. There are two condensers and they are
 *    free to differ.
 *  - The zones named as affected are the ones an override would actually move, not every zone
 *    that happens to be running.
 *
 * Returns null when the request is fine. This is a courtesy check for immediate feedback; the
 * n8n watchdog is the real enforcement.
 */
fun findModeConflict(
    zones: List<ZoneModeSnapshot>,
    globalHvacMode: String,
    targetZoneKey: String,
    requestedMode: String
): ModeConflict? {
    val requested = hvacFamilyOf(requestedMode)
    if (requested == HvacFamily.NEUTRAL) return null

    val targetName = zones.firstOrNull { it.key == targetZoneKey }?.name
    fun conflicting(mode: String) =
        hvacFamilyOf(mode).let { it != HvacFamily.NEUTRAL && it != requested }

    val globalFamily = hvacFamilyOf(globalHvacMode)
    if (globalFamily != HvacFamily.NEUTRAL) {
        if (globalFamily == requested) return null
        val wouldSwitch = zones.filter { conflicting(it.mode) }.map { it.name }
        return ModeConflict(
            requestedMode, "The house mode", globalHvacMode,
            (wouldSwitch + listOfNotNull(targetName)).distinct()
        )
    }

    // House mode off means scheduling is paused and manual per-zone control is allowed, so only
    // a head sharing this one's condenser can actually block it.
    val clashing = zones.filter {
        it.key != targetZoneKey &&
            conflicting(it.mode) &&
            sharesOutdoorUnit(it.key, targetZoneKey)
    }
    val blocker = clashing.firstOrNull { it.key == "main_level" }
        ?: clashing.firstOrNull()
        ?: return null

    return ModeConflict(
        requestedMode, blocker.name, blocker.mode,
        (clashing.map { it.name } + listOfNotNull(targetName)).distinct()
    )
}

/**
 * Lowest temperature these heads may be asked to cool or dry to. n8n clamps to the same value in
 * both the sequencer and the watchdog; if the app used a different floor the two would take turns
 * correcting each other.
 */
const val COOL_FLOOR_F = 64.5

/**
 * Lowest temperature these heads may be asked to heat to.
 *
 * The hardware works in 0.5 degree Celsius steps and its heating range starts at 16.0 C. Home
 * Assistant advertises `min_temp: 60` because it rounds 60.8 F down, but it validates the request
 * against the unrounded 16.0 C — so anything below 60.8 F is rejected outright with "the service
 * was not able to process your request", and the head silently keeps whatever setpoint it had.
 *
 * That is worse than a wrong number. The head then reads different from what the system intended,
 * the watchdog scores it as drift, and the zone latches into manual override on every schedule
 * transition. Two zones were configured at 60 and had been doing exactly this.
 *
 * 61 is the first value on the 0.5 C ladder that survives the conversion, landing on `set_tmp 160`
 * — which is the same rung Fujitsu's own app labels "60". Nothing is lost by clamping here.
 */
const val HEAT_FLOOR_F = 61.0

/**
 * The scheduled setpoint a zone should sit at when running [targetMode], or null when there is
 * no meaningful target.
 *
 * Mirrors n8n's suffix rule exactly rather than being tidier than it: the cool helpers are used
 * only for `cool`, so `dry` reads the heat number, while the cool floor applies to both. The two
 * systems agreeing matters more here than the rule being elegant — a disagreement shows up as
 * the head being corrected back and forth.
 */
fun scheduledSetpoint(zone: ClimateZone, houseSchedule: String, targetMode: String): Double? {
    val mode = targetMode.lowercase()
    if (mode == "off" || mode == "fan_only" || mode == "unavailable") return null

    val presets = if (mode == "cool") zone.presetsCool else zone.presetsHeat
    val value = when (houseSchedule.lowercase()) {
        "night" -> presets.nightValue
        "away" -> presets.awayValue
        else -> presets.dayValue
    } ?: return null

    return clampToHardwareFloor(value, mode)
}

/**
 * Raises a setpoint to the lowest value the equipment will actually accept for that family.
 *
 * Shared so the panel, the car and the n8n sequencer cannot drift apart on it. A value below the
 * floor is not merely wrong — the write is rejected outright and the head keeps its previous
 * setpoint, which the watchdog then scores as a manual adjustment and suspends the zone for.
 */
fun clampToHardwareFloor(value: Double, targetMode: String): Double {
    val mode = targetMode.lowercase()
    return if (mode == "cool" || mode == "dry") maxOf(value, COOL_FLOOR_F)
    else maxOf(value, HEAT_FLOOR_F)
}

/**
 * Which alarm panel to drive, and which contact sensors to show beside it.
 *
 * `alarm_control_panel.alarmo` is Alarmo's default entity id. It is configurable because Alarmo
 * also creates one panel per area when areas are used, and because nothing here should be
 * hardcoded to an entity that did not exist when this was written.
 *
 * Note there is deliberately no field for the PIN. The code is typed on the keypad, passed
 * straight to the service call, and never stored, cached or logged.
 */
@JsonClass(generateAdapter = true)
data class AlarmConfig(
    val entityId: String? = "alarm_control_panel.alarmo",
    val sensors: List<AlarmSensorConfig>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class AlarmSensorConfig(
    val entityId: String,
    val name: String,
    /**
     * Optional override for entities that do not set a `device_class`: "motion" or "contact".
     * Normally left out, because Home Assistant already knows.
     */
    val type: String? = null
)

// ---------------------------------------------------------------------------------------------
// Alarm (Alarmo integration)
// ---------------------------------------------------------------------------------------------
//
// Written against Alarmo's documented contract rather than against a live entity: the integration
// was not installed when this was built, so nothing here has been exercised against a real panel.
// Everything therefore degrades to UNAVAILABLE rather than guessing, and the arm buttons are
// derived from what the entity actually reports it supports instead of being hardcoded.

/** What the alarm is doing, reduced to the states the UI needs to behave differently for. */
enum class AlarmPhase {
    /** Off. Nothing is watching. */
    DISARMED,

    /** Exit delay: it is going to arm shortly and you are expected to leave. */
    ARMING,

    /** Entry delay running. This is the state where someone needs the keypad NOW. */
    PENDING,

    /** Armed in some mode. */
    ARMED,

    /** Going off. */
    TRIGGERED,

    /** No panel, or it is not reporting. Never guessed at. */
    UNAVAILABLE
}

/**
 * Maps an alarm_control_panel state string to a [AlarmPhase].
 *
 * Alarmo reports `armed_away`, `armed_home`, `armed_night`, `armed_vacation` and
 * `armed_custom_bypass` as separate states; the UI treats them all as ARMED and shows the specific
 * mode separately. An unrecognised value is UNAVAILABLE rather than being lumped in with DISARMED,
 * because "I do not know" and "it is off" must not look the same on an alarm panel.
 */
fun alarmPhaseOf(state: String?): AlarmPhase = when (state?.lowercase()) {
    "disarmed" -> AlarmPhase.DISARMED
    "arming" -> AlarmPhase.ARMING
    "pending" -> AlarmPhase.PENDING
    "triggered" -> AlarmPhase.TRIGGERED
    "armed_away", "armed_home", "armed_night",
    "armed_vacation", "armed_custom_bypass" -> AlarmPhase.ARMED
    else -> AlarmPhase.UNAVAILABLE
}

/**
 * True when the keypad should be put in front of the user without being asked for.
 *
 * PENDING is the entry delay — the window in which you must disarm — and TRIGGERED is after it
 * has gone off. Both are cases where hunting for the right tab is the wrong thing to be doing.
 * ARMING is deliberately excluded: that is the exit delay, when you are walking out, and a modal
 * keypad would be in the way.
 */
fun alarmDemandsKeypad(phase: AlarmPhase): Boolean =
    phase == AlarmPhase.PENDING || phase == AlarmPhase.TRIGGERED

/** Home Assistant's AlarmControlPanelEntityFeature bit flags. */
object AlarmFeature {
    const val ARM_HOME = 1
    const val ARM_AWAY = 2
    const val ARM_NIGHT = 4
    const val TRIGGER = 8
    const val ARM_CUSTOM_BYPASS = 16
    const val ARM_VACATION = 32
}

/** One arming option offered by the panel, with the service that applies it. */
data class AlarmArmOption(val label: String, val service: String, val feature: Int)

/**
 * The arm buttons this panel actually supports, in the order they should be shown.
 *
 * Read from `supported_features` rather than hardcoded, because which modes exist depends on how
 * Alarmo has been configured. A panel that only does Away gets one button, not four dead ones.
 */
fun alarmArmOptions(supportedFeatures: Int): List<AlarmArmOption> = listOf(
    AlarmArmOption("AWAY", "alarm_arm_away", AlarmFeature.ARM_AWAY),
    AlarmArmOption("HOME", "alarm_arm_home", AlarmFeature.ARM_HOME),
    AlarmArmOption("NIGHT", "alarm_arm_night", AlarmFeature.ARM_NIGHT),
    AlarmArmOption("VACATION", "alarm_arm_vacation", AlarmFeature.ARM_VACATION)
).filter { supportedFeatures and it.feature != 0 }

/**
 * What kind of thing a sensor is, which decides the words used to describe it.
 *
 * Three kinds rather than two because they genuinely answer different questions. A contact says
 * whether something is open. A motion detector says whether something just moved. An occupancy
 * sensor — the Ecobee room sensors are these — says whether a room currently has someone in it,
 * which is a state rather than an event and reads badly as "MOTION / NONE".
 */
enum class AlarmSensorKind { CONTACT, MOTION, OCCUPANCY }

/**
 * Works out whether a sensor is a contact or a motion detector.
 *
 * Read from Home Assistant's own `device_class` rather than requiring it to be configured, with
 * an optional config override for entities that do not set one. It matters because the two read
 * completely differently: a motion sensor is never "closed", and calling it that on a security
 * screen invites the reader to think a door is shut when nothing of the sort was measured.
 */
fun alarmSensorKind(deviceClass: String?, configuredType: String? = null): AlarmSensorKind {
    when (configuredType?.lowercase()) {
        "occupancy", "presence" -> return AlarmSensorKind.OCCUPANCY
        "motion", "moving" -> return AlarmSensorKind.MOTION
        "contact", "door", "window", "garage" -> return AlarmSensorKind.CONTACT
    }
    return when (deviceClass?.lowercase()) {
        "occupancy", "presence" -> AlarmSensorKind.OCCUPANCY
        "motion", "moving", "vibration" -> AlarmSensorKind.MOTION
        else -> AlarmSensorKind.CONTACT
    }
}

/**
 * The words shown beside a sensor.
 *
 * Handles covers (`open`/`closed`) and binary sensors (`on`/`off`) in one place, since the alarm
 * list mixes both. Anything unrecognised — including a missing entity — reads UNKNOWN and never
 * falls back to the reassuring answer.
 */
fun alarmSensorStatusLabel(kind: AlarmSensorKind, rawState: String?): String {
    val s = rawState?.lowercase()
    val active = when (s) {
        "open", "on", "opening" -> true
        "closed", "off", "closing" -> false
        else -> return "UNKNOWN"
    }
    return when (kind) {
        // Motion and occupancy share wording on purpose. Both answer "is anything going on in
        // there", and one pair of words across the list is easier to read at a glance on a wall
        // than MOTION/NONE next to OCCUPIED/CLEAR. The icon still distinguishes them.
        AlarmSensorKind.MOTION, AlarmSensorKind.OCCUPANCY -> if (active) "DETECTED" else "CLEAR"
        AlarmSensorKind.CONTACT -> if (active) "OPEN" else "CLOSED"
    }
}

/** Live alarm state, parsed from the entity. */
data class AlarmState(
    val entityId: String = "",
    val phase: AlarmPhase = AlarmPhase.UNAVAILABLE,
    val rawState: String = "",
    /** e.g. "armed_away", for showing WHICH armed mode is active. */
    val armMode: String? = null,
    /** "number" means digits only, which is what the keypad renders. */
    val codeFormat: String? = null,
    val codeArmRequired: Boolean = true,
    /** Sensors Alarmo reports as open, which is why an arm attempt was refused. */
    val openSensors: List<String> = emptyList(),
    val changedBy: String? = null,
    val supportedFeatures: Int = 0,
    /** Alarmo reports the configured entry/exit delay in seconds while arming or pending. */
    val delaySeconds: Int? = null,
    /** When the panel entered its current state, for counting the delay down. */
    val phaseSinceEpochMs: Long? = null
) {
    val armOptions: List<AlarmArmOption> get() = alarmArmOptions(supportedFeatures)
    val demandsKeypad: Boolean get() = alarmDemandsKeypad(phase)
    val isPresent: Boolean get() = entityId.isNotEmpty() && phase != AlarmPhase.UNAVAILABLE

    /**
     * Seconds left on the entry or exit delay, or null when there isn't one running.
     *
     * Returns null rather than zero when the delay is unknown, so the popup can omit the
     * countdown entirely instead of displaying a confident "0" it has not actually counted.
     */
    fun secondsRemaining(nowMs: Long = System.currentTimeMillis()): Int? {
        if (phase != AlarmPhase.PENDING && phase != AlarmPhase.ARMING) return null
        val total = delaySeconds ?: return null
        val since = phaseSinceEpochMs ?: return null
        val elapsed = ((nowMs - since) / 1000L).toInt()
        return (total - elapsed).coerceAtLeast(0)
    }

    /** Headline for the status banner. Never invents a state it does not have. */
    val headline: String
        get() = when (phase) {
            AlarmPhase.DISARMED -> "DISARMED"
            AlarmPhase.ARMING -> "ARMING — EXIT NOW"
            AlarmPhase.PENDING -> "ENTRY DELAY — DISARM"
            AlarmPhase.TRIGGERED -> "ALARM TRIGGERED"
            AlarmPhase.ARMED -> when (armMode) {
                "armed_home" -> "ARMED — HOME"
                "armed_night" -> "ARMED — NIGHT"
                "armed_vacation" -> "ARMED — VACATION"
                "armed_custom_bypass" -> "ARMED — CUSTOM"
                else -> "ARMED — AWAY"
            }
            AlarmPhase.UNAVAILABLE -> "ALARM UNAVAILABLE"
        }
}

/** Which UI zone each head belongs to. Main level is two heads across both outdoor units. */
val headZoneKey: Map<String, String> = mapOf(
    "climate.hp_living_room" to "main_level",
    "climate.hp_dining_room" to "main_level",
    "climate.hp_bedroom" to "bedroom_1",
    "climate.hp_bedroom_2" to "bedroom_2",
    "climate.hp_basement" to "basement",
    "climate.hp_anthony" to "anthony",
    "climate.hp_autumn" to "autumn"
)

/**
 * The preset helper holding a zone's target for a schedule slot and mode, or null when there
 * isn't one.
 *
 * Mirrors n8n's suffix rule exactly rather than being tidier than it: the `_cool` helpers are
 * used only for `cool`, so `dry` reads the heat number in both systems.
 */
fun presetHelperId(zoneKey: String, houseSchedule: String, targetMode: String): String? {
    val mode = targetMode.lowercase()
    if (mode == "off" || mode == "fan_only" || mode == "unavailable" || mode == "unknown") return null
    val slot = when (houseSchedule.lowercase()) {
        "night" -> "night"
        "away" -> "away"
        "day" -> "day"
        else -> return null
    }
    val suffix = if (mode == "cool") "cool" else "temp"
    return "input_number.${zoneKey}_${slot}_$suffix"
}

/**
 * Why a requested mode cannot be applied, and what it would take to apply it anyway.
 */
data class ModeConflict(
    val requestedMode: String,
    /** The zone (or "House mode") already committed to the other family. */
    val blockedBy: String,
    val blockingMode: String,
    /** Every running zone that an override would switch, blocker included. */
    val affectedZones: List<String>
)

data class GlobalSettings(
    val houseSchedule: String = "Day", // Day, Night, Away
    val waterHeaterMode: String = "eco", // eco, heat_pump, high_demand
    val globalHvacMode: String = "heat", // heat, cool, off
    val lastNonOffHvacMode: String = "heat",
    val waterHeaterFullness: Double? = null // e.g. 0.0 - 100.0 (sensor.heat_pump_water_heater_available_hot_water)
)

data class LightControl(
    val entityId: String,
    val name: String,
    val isOn: Boolean = false,
    val brightness: Int? = null // 0-255
)

data class SwitchControl(
    val entityId: String,
    val name: String,
    val isOn: Boolean = false
)

data class CoverControl(
    val entityId: String,
    val name: String,
    val state: String = "closed" // open, closed, opening, closing
)

data class PoolHistoryPoint(
    val timestamp: String,
    val temp: Float,
    val ph: Float,
    val orp: Float
)

data class PoolState(
    val waterTemperature: Double? = null,
    val ph: Double? = null,
    val orp: Double? = null,
    val battery: Double? = null,
    val lastSynced: String? = null,
    val lastUpdated: String? = null,
    val monitorSerial: String? = null,
    val sensorSerial: String? = null,
    val wifiSignal: Int? = null,
    val waterStatus: String? = null,
    val actionsPending: Int? = null
)

