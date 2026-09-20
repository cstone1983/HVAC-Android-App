package com.example.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HvacThemeConfig(
    val accentColorHex: String? = "#F59E0B",
    val coolColorHex: String? = "#2196F3",
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
    val sections: List<String>
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
    val poolSensors: PoolSensorConfig? = PoolSensorConfig(),
    val solarSensors: SolarSensorConfig? = SolarSensorConfig(),
    val dynamicSections: List<DynamicSectionConfig>? = emptyList(),
    val showWeatherCard: Boolean? = true,
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
    val autoOn: Boolean = false,
    val overrideOn: Boolean = false,
    val vaneMode: String = "Auto",
    val fanMode: String = "Auto",
    val vaneOptions: List<String> = listOf("Auto", "Swing", "1", "2", "3", "4", "5"),
    val fanOptions: List<String> = listOf("Auto", "Quiet", "Low", "High")
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

