package com.example

import com.example.model.AlarmFeature
import com.example.model.AlarmPhase
import com.example.model.AlarmSensorKind
import com.example.model.AlarmState
import com.example.model.alarmArmOptions
import com.example.model.alarmDemandsKeypad
import com.example.model.alarmPhaseOf
import com.example.model.alarmSensorKind
import com.example.model.alarmSensorStatusLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The alarm was built against Alarmo's documented contract while the integration did not yet
 * exist in Home Assistant, so none of it could be exercised against a real panel. These cover the
 * decisions that would otherwise only be discovered the first time the alarm actually goes off,
 * which is the worst possible moment to find out.
 */
class AlarmStateTest {

    @Test
    fun `every armed variant reads as ARMED`() {
        listOf("armed_away", "armed_home", "armed_night", "armed_vacation", "armed_custom_bypass")
            .forEach { assertEquals(AlarmPhase.ARMED, alarmPhaseOf(it)) }
    }

    @Test
    fun `the phases that differ in behaviour are kept apart`() {
        assertEquals(AlarmPhase.DISARMED, alarmPhaseOf("disarmed"))
        assertEquals(AlarmPhase.ARMING, alarmPhaseOf("arming"))
        assertEquals(AlarmPhase.PENDING, alarmPhaseOf("pending"))
        assertEquals(AlarmPhase.TRIGGERED, alarmPhaseOf("triggered"))
    }

    @Test
    fun `an unknown or missing state is never mistaken for disarmed`() {
        // The whole point: "I cannot see the panel" and "the panel is off" must not look alike.
        assertEquals(AlarmPhase.UNAVAILABLE, alarmPhaseOf(null))
        assertEquals(AlarmPhase.UNAVAILABLE, alarmPhaseOf(""))
        assertEquals(AlarmPhase.UNAVAILABLE, alarmPhaseOf("unavailable"))
        assertEquals(AlarmPhase.UNAVAILABLE, alarmPhaseOf("something_new_in_a_future_release"))
    }

    @Test
    fun `the keypad is forced up only when someone needs it`() {
        // Entry delay and going-off: yes. Exit delay: no — you are walking out of the door and a
        // modal keypad would be in the way.
        assertTrue(alarmDemandsKeypad(AlarmPhase.PENDING))
        assertTrue(alarmDemandsKeypad(AlarmPhase.TRIGGERED))
        assertFalse(alarmDemandsKeypad(AlarmPhase.ARMING))
        assertFalse(alarmDemandsKeypad(AlarmPhase.ARMED))
        assertFalse(alarmDemandsKeypad(AlarmPhase.DISARMED))
        assertFalse(alarmDemandsKeypad(AlarmPhase.UNAVAILABLE))
    }

    @Test
    fun `arm buttons come from what the panel says it supports`() {
        val awayOnly = alarmArmOptions(AlarmFeature.ARM_AWAY)
        assertEquals(listOf("AWAY"), awayOnly.map { it.label })

        val awayAndHome = alarmArmOptions(AlarmFeature.ARM_AWAY or AlarmFeature.ARM_HOME)
        assertEquals(listOf("AWAY", "HOME"), awayAndHome.map { it.label })

        // A panel that reports nothing gets no buttons rather than four dead ones.
        assertTrue(alarmArmOptions(0).isEmpty())
    }

    @Test
    fun `arm options map to the right services`() {
        val all = alarmArmOptions(
            AlarmFeature.ARM_AWAY or AlarmFeature.ARM_HOME or
                AlarmFeature.ARM_NIGHT or AlarmFeature.ARM_VACATION
        )
        assertEquals("alarm_arm_away", all.first { it.label == "AWAY" }.service)
        assertEquals("alarm_arm_home", all.first { it.label == "HOME" }.service)
        assertEquals("alarm_arm_night", all.first { it.label == "NIGHT" }.service)
        assertEquals("alarm_arm_vacation", all.first { it.label == "VACATION" }.service)
    }

    // ---- sensors ------------------------------------------------------------------------

    @Test
    fun `a motion sensor is never described as closed`() {
        // "CLOSED" on a motion detector invites you to believe a door was measured shut when
        // nothing of the sort happened.
        val motion = alarmSensorKind(deviceClass = "motion")
        assertEquals(AlarmSensorKind.MOTION, motion)
        assertEquals("MOTION", alarmSensorStatusLabel(motion, "on"))
        assertEquals("NONE", alarmSensorStatusLabel(motion, "off"))
    }

    @Test
    fun `a cover and a contact sensor both read open or closed`() {
        val contact = alarmSensorKind(deviceClass = "garage")
        assertEquals(AlarmSensorKind.CONTACT, contact)
        // Covers report open/closed, binary sensors report on/off; one list shows both.
        assertEquals("OPEN", alarmSensorStatusLabel(contact, "open"))
        assertEquals("CLOSED", alarmSensorStatusLabel(contact, "closed"))
        assertEquals("OPEN", alarmSensorStatusLabel(contact, "on"))
        assertEquals("CLOSED", alarmSensorStatusLabel(contact, "off"))
    }

    @Test
    fun `a door in motion counts as open`() {
        val contact = alarmSensorKind(deviceClass = "garage")
        assertEquals("OPEN", alarmSensorStatusLabel(contact, "opening"))
        assertEquals("CLOSED", alarmSensorStatusLabel(contact, "closing"))
    }

    @Test
    fun `an unreadable sensor says so rather than reassuring you`() {
        val contact = alarmSensorKind(deviceClass = "garage")
        assertEquals("UNKNOWN", alarmSensorStatusLabel(contact, null))
        assertEquals("UNKNOWN", alarmSensorStatusLabel(contact, "unavailable"))
        assertEquals("UNKNOWN", alarmSensorStatusLabel(AlarmSensorKind.MOTION, "unknown"))
    }

    @Test
    fun `config can override a missing device class`() {
        assertEquals(AlarmSensorKind.MOTION, alarmSensorKind(deviceClass = null, configuredType = "motion"))
        assertEquals(AlarmSensorKind.CONTACT, alarmSensorKind(deviceClass = null, configuredType = "contact"))
        // An explicit type wins over a device_class that disagrees with it.
        assertEquals(AlarmSensorKind.MOTION, alarmSensorKind(deviceClass = "door", configuredType = "motion"))
        // Nothing to go on falls back to contact, which is the commoner case.
        assertEquals(AlarmSensorKind.CONTACT, alarmSensorKind(deviceClass = null, configuredType = null))
    }

    // ---- countdown ----------------------------------------------------------------------

    @Test
    fun `the entry delay counts down and stops at zero`() {
        val start = 1_000_000L
        val pending = AlarmState(
            entityId = "alarm_control_panel.alarmo",
            phase = AlarmPhase.PENDING,
            delaySeconds = 30,
            phaseSinceEpochMs = start
        )
        assertEquals(30, pending.secondsRemaining(start))
        assertEquals(20, pending.secondsRemaining(start + 10_000))
        // Never negative: a delay that has run out reads zero, not -7.
        assertEquals(0, pending.secondsRemaining(start + 45_000))
    }

    @Test
    fun `no countdown is shown when there is nothing to count`() {
        val start = 1_000_000L
        // Armed and disarmed have no delay running.
        assertNull(
            AlarmState(phase = AlarmPhase.ARMED, delaySeconds = 30, phaseSinceEpochMs = start)
                .secondsRemaining(start)
        )
        // A panel that did not report a delay gets no invented one — null, not 0, so the popup
        // omits the timer instead of displaying a confident zero it never counted.
        assertNull(
            AlarmState(phase = AlarmPhase.PENDING, delaySeconds = null, phaseSinceEpochMs = start)
                .secondsRemaining(start)
        )
        assertNull(
            AlarmState(phase = AlarmPhase.PENDING, delaySeconds = 30, phaseSinceEpochMs = null)
                .secondsRemaining(start)
        )
    }

    @Test
    fun `an absent panel is not reported as present`() {
        assertFalse(AlarmState().isPresent)
        assertFalse(AlarmState(entityId = "alarm_control_panel.alarmo").isPresent)
        assertTrue(
            AlarmState(entityId = "alarm_control_panel.alarmo", phase = AlarmPhase.DISARMED).isPresent
        )
    }

    @Test
    fun `the headline names the armed mode rather than just saying armed`() {
        assertEquals(
            "ARMED — HOME",
            AlarmState(phase = AlarmPhase.ARMED, armMode = "armed_home").headline
        )
        assertEquals(
            "ARMED — NIGHT",
            AlarmState(phase = AlarmPhase.ARMED, armMode = "armed_night").headline
        )
        assertEquals("ALARM UNAVAILABLE", AlarmState().headline)
    }
}
