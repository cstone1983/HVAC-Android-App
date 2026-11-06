package com.example

import com.example.model.ClimateZone
import com.example.model.HouseConditionConfig
import com.example.model.HvacLayoutConfig
import com.example.model.Presets
import com.example.model.scheduledSetpoint
import com.example.model.zoneConditionEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Heat pump settings page's house conditions (2026-11-06). The app must reach the same target
 * as n8n's zoneTarget() (see tools/n8n-logic-tests, cases K*, B*, N*), or the card's SET and the
 * "Setting to" check disagree with what the heads are given.
 */
class HouseConditionsTest {

    private val conditions = listOf(
        HouseConditionConfig("input_boolean.hvac_early_night", zones = listOf("*"), fromSchedule = "day", schedule = "night", setLabel = "NIGHT"),
        HouseConditionConfig("input_boolean.hvac_kids_away", zones = listOf("anthony", "autumn"), schedule = "away", setLabel = "KIDS AWAY"),
        HouseConditionConfig(
            "timer.hvac_boost_main_level", onState = "active", zones = listOf("main_level"),
            boostDegreesEntityId = "input_number.hvac_boost_degrees", boostDefault = 3.0, setLabel = "BOOST"
        )
    )

    private fun effect(zone: String, schedule: String, vararg on: Pair<String, String>) =
        zoneConditionEffect(zone, schedule, conditions) { id -> on.toMap()[id] }

    private val kids = "input_boolean.hvac_kids_away" to "on"
    private val early = "input_boolean.hvac_early_night" to "on"
    private val boost = "timer.hvac_boost_main_level" to "active"

    @Test
    fun `kids away puts Anthony and Autumn on Away, nobody else`() {
        assertEquals("Away", effect("anthony", "Day", kids).schedule)
        assertEquals("Away", effect("autumn", "Night", kids).schedule)
        assertEquals("KIDS AWAY", effect("anthony", "Day", kids).label)
        assertNull(effect("bedroom_1", "Day", kids).schedule)
        assertNull(effect("anthony", "Away", kids).schedule)
    }

    @Test
    fun `everything off changes nothing`() {
        val e = effect("main_level", "Day")
        assertNull(e.schedule)
        assertEquals(0.0, e.boostDegrees, 0.0)
        assertNull(e.label)
    }

    @Test
    fun `night early applies only over Day`() {
        assertEquals("Night", effect("bedroom_1", "Day", early).schedule)
        assertNull(effect("bedroom_1", "Night", early).schedule)
        assertNull(effect("bedroom_1", "Away", early).schedule)
    }

    @Test
    fun `kids away wins over night early`() {
        assertEquals("Away", effect("anthony", "Day", early, kids).schedule)
        assertEquals("Night", effect("main_level", "Day", early, kids).schedule)
    }

    @Test
    fun `boost uses the degrees helper, defaulting to 3`() {
        assertEquals(3.0, effect("main_level", "Day", boost).boostDegrees, 0.0)
        assertEquals(2.0, effect("main_level", "Day", boost, "input_number.hvac_boost_degrees" to "2.0").boostDegrees, 0.0)
        assertEquals(3.0, effect("main_level", "Day", boost, "input_number.hvac_boost_degrees" to "unavailable").boostDegrees, 0.0)
        assertEquals(0.0, effect("anthony", "Day", boost).boostDegrees, 0.0)
        assertEquals(0.0, effect("main_level", "Day", "timer.hvac_boost_main_level" to "paused").boostDegrees, 0.0)
        assertEquals("BOOST", effect("main_level", "Day", boost).label)
    }

    private fun main(override: String? = null, boost: Double = 0.0, day: Double = 69.0, dayCool: Double = 71.0) = ClimateZone(
        key = "main_level", name = "Main Level", climateEntityId = "climate.hp_living_room",
        autoEntityId = "a", overrideEntityId = "o", tiltEntityId = "t", fanEntityId = "f",
        presetsHeat = Presets("d", "n", "a", dayValue = day, nightValue = 67.0, awayValue = 66.0),
        presetsCool = Presets("dc", "nc", "ac", dayValue = dayCool, nightValue = 75.0, awayValue = 75.0),
        scheduleOverride = override, boostDegrees = boost
    )

    @Test
    fun `scheduled setpoint matches n8n's zoneTarget`() {
        assertEquals(72.0, scheduledSetpoint(main(boost = 3.0), "Day", "heat")!!, 0.0)          // B1
        assertEquals(80.0, scheduledSetpoint(main(boost = 3.0, day = 79.0), "Day", "heat")!!, 0.0) // B5
        assertEquals(68.0, scheduledSetpoint(main(boost = 3.0), "Day", "cool")!!, 0.0)          // B6
        assertEquals(64.5, scheduledSetpoint(main(boost = 3.0, dayCool = 66.0), "Day", "cool")!!, 0.0) // B6b
        assertEquals(69.0, scheduledSetpoint(main(boost = 3.0), "Day", "dry")!!, 0.0)           // B7
        assertEquals(70.0, scheduledSetpoint(main(override = "Night", boost = 3.0), "Day", "heat")!!, 0.0) // N5
        assertEquals(66.0, scheduledSetpoint(main(override = "Away"), "Day", "heat")!!, 0.0)    // Kids away
        assertEquals(69.0, scheduledSetpoint(main(), "Day", "heat")!!, 0.0)                     // unchanged
    }

    @Test
    fun `the shipped layout config parses and lists the same conditions as n8n`() {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
        val file = listOf("../layout_config.json", "layout_config.json").map { java.io.File(it) }.first { it.exists() }
        val cfg = moshi.adapter(HvacLayoutConfig::class.java).fromJson(file.readText())!!
        val hs = assertNotNullAndGet(cfg.hvacSettings)
        assertEquals(
            listOf("input_boolean.hvac_early_night", "input_boolean.hvac_kids_away", "timer.hvac_boost_main_level"),
            hs.conditions.map { it.entityId }
        )
        assertEquals(listOf(5, 10, 30, 60), hs.nightDelay!!.durations)
        assertEquals(listOf("Whole house", "Downstairs", "Heat pumps only"), hs.nightDelay!!.scopes.map { it.option })
        // The Living room only row's wording comes from the zone's headChoice.
        assertNotNull(cfg.zones.first { it.key == "main_level" }.headChoice?.description)
    }

    private fun <T> assertNotNullAndGet(v: T?): T { assertNotNull(v); return v!! }
}
