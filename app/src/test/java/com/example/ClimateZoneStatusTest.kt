package com.example

import com.example.model.ClimateZone
import com.example.model.Presets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The zone card used to pulse and glow whenever the mode was heat or cool, which meant every
 * non-off card animated permanently and the glow told you nothing. These cover the derived
 * "is it actually working" state that replaced it.
 */
class ClimateZoneStatusTest {

    private fun zone(
        mode: String,
        current: Double?,
        target: Double?,
        action: String? = null
    ) = ClimateZone(
        key = "test",
        name = "Test",
        climateEntityId = "climate.test",
        autoEntityId = "input_boolean.test_auto",
        overrideEntityId = "input_boolean.test_override",
        tiltEntityId = "input_select.test_tilt",
        fanEntityId = "input_select.test_fan",
        presetsHeat = Presets("", "", ""),
        presetsCool = Presets("", "", ""),
        currentTemp = current,
        targetTemp = target,
        currentHvacMode = mode,
        hvacAction = action
    )

    @Test
    fun `hvac_action wins when the device reports it`() {
        // The thermostats expose hvac_action; trust it over any inference.
        assertTrue(zone("heat", 60.0, 70.0, action = "heating").isCalling)
        assertFalse(zone("heat", 60.0, 70.0, action = "idle").isCalling)
        assertEquals("RUNNING", zone("heat", 60.0, 70.0, action = "heating").statusLabel)
        assertEquals("AT TARGET", zone("heat", 60.0, 70.0, action = "idle").statusLabel)
    }

    @Test
    fun `heating is inferred when the room is below target`() {
        // The Fujitsu heads report no hvac_action at all.
        assertTrue(zone("heat", 67.0, 71.0).isCalling)
        assertEquals("RUNNING", zone("heat", 67.0, 71.0).statusLabel)
    }

    @Test
    fun `a room at or above its heat target is not calling`() {
        assertFalse(zone("heat", 71.0, 71.0).isCalling)
        assertFalse(zone("heat", 72.0, 71.0).isCalling)
        assertEquals("AT TARGET", zone("heat", 71.0, 71.0).statusLabel)
    }

    @Test
    fun `cooling and drying invert the comparison`() {
        assertTrue(zone("cool", 76.0, 71.0).isCalling)
        assertFalse(zone("cool", 68.0, 71.0).isCalling)
        // The status is deliberately mode-agnostic now; the card tint carries the mode.
        assertEquals("RUNNING", zone("cool", 76.0, 71.0).statusLabel)
        assertEquals("RUNNING", zone("dry", 76.0, 71.0).statusLabel)
    }

    @Test
    fun `an off zone never animates whatever the temperatures say`() {
        val off = zone("off", 60.0, 75.0)
        assertFalse(off.isCalling)
        assertEquals("OFF", off.statusLabel)
    }

    @Test
    fun `missing readings do not claim the zone is running`() {
        assertFalse(zone("heat", null, 71.0).isCalling)
        assertFalse(zone("heat", 67.0, null).isCalling)
        assertEquals("IDLE", zone("heat", null, 71.0).statusLabel)
    }

    @Test
    fun `a half degree of drift is not treated as calling`() {
        // Avoids the card flickering between states on sensor noise.
        assertFalse(zone("heat", 70.6, 71.0).isCalling)
        assertTrue(zone("heat", 70.4, 71.0).isCalling)
    }

    @Test
    fun `a head we cannot hear from is never reported as at target`() {
        // "unknown" used to fall through to the temperature comparison and report AT TARGET,
        // which reads as a verified reassurance about a room nobody has a reading for. A head
        // absent from the state map now arrives here as "unavailable" for the same reason.
        for (mode in listOf("unavailable", "unknown", "")) {
            val z = zone(mode, 71.0, 71.0)
            assertFalse("$mode should not be calling", z.isCalling)
            assertEquals("$mode should report unavailable", "UNAVAILABLE", z.statusLabel)
        }
    }

    @Test
    fun `off still reads as off rather than unavailable`() {
        // Deliberately switched off is a different fact from not reporting, and the card has to
        // keep telling them apart.
        assertEquals("OFF", zone("off", 71.0, 71.0).statusLabel)
    }
}
