package com.example

import com.example.model.ClimateZone
import com.example.model.Presets
import com.example.model.scheduledSetpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Turning a sleeping head on has to leave it at the setpoint automation expects. These heads keep
 * one setpoint across modes, so a head powered up in heat holds whatever the cooling schedule
 * last left it at. Autumn's heat and cool day targets are ten degrees apart, which is enough for
 * the n8n watchdog to read the gap as a manual adjustment and suspend the zone.
 *
 * The rule has to match n8n's suffix logic exactly, including the parts that look wrong, or the
 * two systems correct each other in turn.
 */
class ScheduledSetpointTest {

    private fun zone(
        heat: Triple<Double, Double, Double>,
        cool: Triple<Double, Double, Double>
    ) = ClimateZone(
        key = "autumn",
        name = "Autumn",
        climateEntityId = "climate.hp_autumn",
        autoEntityId = "input_boolean.zone_enable_autumn",
        overrideEntityId = "input_boolean.override_autumn",
        tiltEntityId = "input_select.autumn_tilt_mode",
        fanEntityId = "input_select.autumn_fan_mode",
        presetsHeat = Presets("", "", "", heat.first, heat.second, heat.third),
        presetsCool = Presets("", "", "", cool.first, cool.second, cool.third),
        currentTemp = 70.0,
        targetTemp = 72.0,
        currentHvacMode = "off"
    )

    // Autumn's real values as configured.
    private val autumn = zone(
        heat = Triple(62.0, 62.0, 61.0),
        cool = Triple(72.0, 70.0, 75.0)
    )

    @Test
    fun `heat reads the heat helpers for the active schedule slot`() {
        assertEquals(62.0, scheduledSetpoint(autumn, "Day", "heat")!!, 0.001)
        assertEquals(62.0, scheduledSetpoint(autumn, "Night", "heat")!!, 0.001)
        assertEquals(61.0, scheduledSetpoint(autumn, "Away", "heat")!!, 0.001)
    }

    @Test
    fun `cool reads the cool helpers`() {
        assertEquals(72.0, scheduledSetpoint(autumn, "Day", "cool")!!, 0.001)
        assertEquals(70.0, scheduledSetpoint(autumn, "Night", "cool")!!, 0.001)
    }

    @Test
    fun `the schedule name is matched regardless of case`() {
        // HA reports "Day"; n8n lowercases before use. Both spellings must land the same.
        assertEquals(62.0, scheduledSetpoint(autumn, "day", "heat")!!, 0.001)
        assertEquals(61.0, scheduledSetpoint(autumn, "AWAY", "heat")!!, 0.001)
    }

    @Test
    fun `an unrecognised schedule falls back to day rather than to nothing`() {
        assertEquals(62.0, scheduledSetpoint(autumn, "", "heat")!!, 0.001)
        assertEquals(62.0, scheduledSetpoint(autumn, "unavailable", "heat")!!, 0.001)
    }

    @Test
    fun `dry reads the HEAT helper, matching n8n`() {
        // Deliberate, and odd twice over. n8n's suffix rule is `cool ? _cool : _temp`, so dry
        // takes the heat number in both systems; then the floor is applied for `cool || dry`.
        // Dry therefore lands on the heat setpoint, clamped up to 64.5.
        //
        // A zone whose heat target clears the floor shows the helper choice on its own.
        val mainLevel = zone(heat = Triple(70.0, 66.0, 66.0), cool = Triple(71.0, 75.0, 75.0))
        assertEquals(70.0, scheduledSetpoint(mainLevel, "Day", "dry")!!, 0.001)

        // Autumn's 62 heat target is below the floor, so dry comes out at 64.5 rather than 62.
        // Not a rounding artifact: it is the heat helper and the cooling floor compounding.
        assertEquals(64.5, scheduledSetpoint(autumn, "Day", "dry")!!, 0.001)
    }

    @Test
    fun `the cool floor applies to cool and dry but never to heat`() {
        val cold = zone(heat = Triple(60.0, 60.0, 60.0), cool = Triple(60.0, 60.0, 60.0))
        assertEquals(64.5, scheduledSetpoint(cold, "Day", "cool")!!, 0.001)
        assertEquals(64.5, scheduledSetpoint(cold, "Day", "dry")!!, 0.001)
        // A 60 degree heat target is legitimate and must not be clamped upward.
        assertEquals(60.0, scheduledSetpoint(cold, "Day", "heat")!!, 0.001)
    }

    @Test
    fun `modes with no meaningful target return null instead of guessing`() {
        assertNull(scheduledSetpoint(autumn, "Day", "off"))
        assertNull(scheduledSetpoint(autumn, "Day", "fan_only"))
        assertNull(scheduledSetpoint(autumn, "Day", "unavailable"))
    }

    @Test
    fun `a missing helper value returns null rather than a default`() {
        val blank = ClimateZone(
            key = "anthony",
            name = "Anthony",
            climateEntityId = "climate.hp_anthony",
            autoEntityId = "",
            overrideEntityId = "",
            tiltEntityId = "",
            fanEntityId = "",
            presetsHeat = Presets("", "", ""),
            presetsCool = Presets("", "", ""),
            currentTemp = null,
            targetTemp = null,
            currentHvacMode = "off"
        )
        assertNull(scheduledSetpoint(blank, "Day", "heat"))
    }

    @Test
    fun `the gap this exists to close is real`() {
        // Powered up in heat after last cooling, the head holds 72 against a 62 target: far
        // outside the watchdog's 0.6 tolerance, so the zone would be suspended within a minute.
        val heatTarget = scheduledSetpoint(autumn, "Day", "heat")!!
        val coolTarget = scheduledSetpoint(autumn, "Day", "cool")!!
        assertEquals(10.0, coolTarget - heatTarget, 0.001)
    }
}
