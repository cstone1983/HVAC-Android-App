package com.example.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HvacLayoutConfig(
    val version: String,
    val roomSensors: List<RoomSensorConfig>,
    val zones: List<ClimateZoneConfig>,
    val lights: List<LightControlConfig>,
    val switches: List<SwitchControlConfig>,
    val covers: List<CoverControlConfig>
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
    val globalHvacMode: String = "heat" // heat, cool, off
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
