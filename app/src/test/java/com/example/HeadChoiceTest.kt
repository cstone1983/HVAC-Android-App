package com.example

import com.example.model.ClimateZone
import com.example.model.HeadChoiceConfig
import com.example.model.Presets
import com.example.model.ZoneModeSnapshot
import com.example.model.findModeConflict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Living room only" on Main Level (2026-11-03): input_select.main_level_heads pauses the dining
 * head, which n8n then holds off. The card must say PAUSED rather than SPLIT, setpoints and modes
 * go to the living room only, and the clash guard must stop counting the dining head's unit.
 */
class HeadChoiceTest {

    private val choice = HeadChoiceConfig(
        entityId = "input_select.main_level_heads",
        allOption = "Both",
        switchOption = "Living room only",
        pausedHeads = mapOf(
            "Living room only" to listOf("climate.hp_dining_room"),
            "Dining room only" to listOf("climate.hp_living_room")
        ),
        switchLabel = "LR ONLY",
        pausedText = "PAUSED"
    )

    private fun main(value: String?, living: String = "heat", dining: String = "off") = ClimateZone(
        key = "main_level",
        name = "Main Level",
        climateEntityId = "climate.hp_living_room",
        autoEntityId = "input_boolean.zone_enable_main_level",
        overrideEntityId = "input_boolean.override_main_level",
        tiltEntityId = "input_select.main_level_tilt_mode",
        fanEntityId = "input_select.main_level_fan_mode",
        presetsHeat = Presets("", "", ""),
        presetsCool = Presets("", "", ""),
        currentTemp = 69.0,
        targetTemp = 69.0,
        currentHvacMode = living,
        secondaryClimateEntityId = "climate.hp_dining_room",
        secondaryHvacMode = dining,
        secondaryTargetTemp = 69.0,
        secondaryCurrentTemp = 69.0,
        primaryHeadLabel = "L",
        secondaryHeadLabel = "DR",
        headChoice = choice,
        headChoiceValue = value
    )

    @Test
    fun `living room only pauses the dining head`() {
        val z = main("Living room only")
        assertEquals(listOf("climate.hp_dining_room"), z.pausedHeads)
        assertEquals(listOf("climate.hp_living_room"), z.headsInUse)
        assertTrue(z.headChoiceSwitchOn)
    }

    @Test
    fun `both runs both heads and the switch is off`() {
        val z = main("Both", dining = "heat")
        assertTrue(z.pausedHeads.isEmpty())
        assertEquals(listOf("climate.hp_living_room", "climate.hp_dining_room"), z.headsInUse)
        assertFalse(z.headChoiceSwitchOn)
    }

    @Test
    fun `an unknown or missing choice behaves as both, like n8n`() {
        assertEquals(2, main(null, dining = "heat").headsInUse.size)
        assertEquals(2, main("unavailable", dining = "heat").headsInUse.size)
    }

    @Test
    fun `option names match ignoring case, like n8n`() {
        assertEquals(listOf("climate.hp_living_room"), main("LIVING ROOM ONLY").headsInUse)
    }

    @Test
    fun `a paused dining head is not a split and its line says PAUSED`() {
        val z = main("Living room only")
        assertFalse(z.isSplit)
        assertEquals("SPLIT".equals(z.statusLabel), false)
        val lines = z.headStatuses!!
        assertEquals("AT TARGET", lines[0].text)
        assertEquals("PAUSED", lines[1].text)
        assertFalse(lines[1].calling)
    }

    @Test
    fun `with both heads in use a head that is off is still a split`() {
        assertTrue(main("Both", dining = "off").isSplit)
    }

    @Test
    fun `dining room only pauses the living room head`() {
        val z = main("Dining room only", living = "off", dining = "heat")
        assertEquals(listOf("climate.hp_dining_room"), z.headsInUse)
        assertEquals("PAUSED", z.headStatuses!![0].text)
        assertFalse(z.headChoiceSwitchOn)
    }

    @Test
    fun `living room only lets Anthony cool while the living room heats`() {
        // House off, so the per-unit rule applies. The living room is on unit 1, Anthony on unit 2.
        val zones = listOf(
            ZoneModeSnapshot("main_level", "Main Level", "heat", setOf(1)),
            ZoneModeSnapshot("main_level", "Main Level", "off", setOf(2)),
            ZoneModeSnapshot("anthony", "Anthony", "off", setOf(2))
        )
        assertNull(findModeConflict(zones, "off", "anthony", "cool"))
    }

    @Test
    fun `with both heads heating Anthony still cannot cool`() {
        val zones = listOf(
            ZoneModeSnapshot("main_level", "Main Level", "heat", setOf(1)),
            ZoneModeSnapshot("main_level", "Main Level", "heat", setOf(2)),
            ZoneModeSnapshot("anthony", "Anthony", "off", setOf(2))
        )
        assertNotNull(findModeConflict(zones, "off", "anthony", "cool"))
    }

    @Test
    fun `living room only Main Level cooling is not blocked by Anthony heating`() {
        val zones = listOf(
            ZoneModeSnapshot("main_level", "Main Level", "off", setOf(1)),
            ZoneModeSnapshot("main_level", "Main Level", "off", setOf(2)),
            ZoneModeSnapshot("anthony", "Anthony", "heat", setOf(2))
        )
        assertNull(findModeConflict(zones, "off", "main_level", "cool", targetUnits = setOf(1)))
        assertNotNull(findModeConflict(zones, "off", "main_level", "cool"))
    }
}
