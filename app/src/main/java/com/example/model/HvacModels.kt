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
    val shortTitle: String? = null
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
    val vaneMode: String = "Auto",
    val fanMode: String = "Auto",
    val vaneOptions: List<String> = listOf("Auto", "Swing", "1", "2", "3", "4", "5"),
    val fanOptions: List<String> = listOf("Auto", "Quiet", "Low", "High")
) {
    /** True only while the unit should actually be moving air to reach its target. */
    val isCalling: Boolean
        get() {
            val mode = currentHvacMode.lowercase()
            if (mode == "off" || mode == "unavailable") return false
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
    val statusLabel: String
        get() {
            val mode = currentHvacMode.lowercase()
            if (mode == "off") return "OFF"
            if (mode == "unavailable") return "UNAVAILABLE"
            if (isCalling) return "RUNNING"
            if (currentTemp != null && targetTemp != null) return "AT TARGET"
            return "IDLE"
        }
}

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

