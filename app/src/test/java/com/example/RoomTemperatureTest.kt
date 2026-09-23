package com.example

import com.example.model.usableRoomTemperature
import com.example.model.zoneDisplayTemperature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The zone cards prefer a room sensor over the head's own reading, because a head measures its
 * own return air and drifts far above the room whenever its fan is off — 92 °F reported in a
 * 71.9 °F room, measured 2026-09-23.
 *
 * Preferring the sensor is only safe if "I have no usable reading" is never confused with a
 * value. A dead sensor reporting 0 or `unknown` has to fall through to the head; if it replaced
 * it instead, the panel would show the house at freezing and the fix would be worse than the
 * fault it replaced.
 */
class RoomTemperatureTest {

    @Test
    fun `a normal reading is taken at face value`() {
        assertEquals(71.9, usableRoomTemperature("71.9")!!, 0.001)
        assertEquals(62.0, usableRoomTemperature("62")!!, 0.001)
        assertEquals(70.6, usableRoomTemperature(" 70.6 ")!!, 0.001)
    }

    @Test
    fun `a sensor with nothing to say falls through to the head`() {
        assertNull(usableRoomTemperature(null))
        assertNull(usableRoomTemperature(""))
        assertNull(usableRoomTemperature("   "))
        assertNull(usableRoomTemperature("unavailable"))
        assertNull(usableRoomTemperature("unknown"))
        assertNull(usableRoomTemperature("Unavailable"))
    }

    @Test
    fun `a non-numeric reading is not coerced into one`() {
        assertNull(usableRoomTemperature("warm"))
        assertNull(usableRoomTemperature("71.9F"))
        assertNull(usableRoomTemperature("--"))
        assertNull(usableRoomTemperature("NaN"))
        assertNull(usableRoomTemperature("Infinity"))
    }

    @Test
    fun `an absurd reading is rejected rather than displayed`() {
        // The failure that matters: a sensor that has died to a sentinel value. Showing -273 or
        // 999 on the wall is worse than showing the head's drifted-but-plausible number.
        assertNull(usableRoomTemperature("-273.15"))
        assertNull(usableRoomTemperature("999"))
        assertNull(usableRoomTemperature("1000000"))
    }

    @Test
    fun `the plausible range is wide enough for a real house`() {
        // An unheated house in a Maine January, and a closed-up room in August. Both real, both
        // must survive.
        assertEquals(-20.0, usableRoomTemperature("-20")!!, 0.001)
        assertEquals(110.0, usableRoomTemperature("110")!!, 0.001)
        assertEquals(0.0, usableRoomTemperature("0.0")!!, 0.001)
    }

    // ---- which reading wins -------------------------------------------------------------

    @Test
    fun `a running head is trusted over the room sensor`() {
        // Its thermistor is in moving return air, measuring the room it is conditioning, and it
        // reacts faster than a sensor reporting every few minutes.
        listOf("heat", "cool", "dry", "fan_only").forEach { mode ->
            assertEquals(74.0, zoneDisplayTemperature(mode, headReading = 74.0, roomSensorReading = 71.0)!!, 0.001)
        }
    }

    @Test
    fun `an idle head defers to the room sensor`() {
        // The case that started this: 92 reported in a 71.9 room after two days off.
        assertEquals(71.9, zoneDisplayTemperature("off", headReading = 92.0, roomSensorReading = 71.9)!!, 0.001)
    }

    @Test
    fun `a head that is not reporting defers to the room sensor`() {
        // "I cannot hear from the head" is not "the head says it is warm".
        assertEquals(71.9, zoneDisplayTemperature("unavailable", 92.0, 71.9)!!, 0.001)
        assertEquals(71.9, zoneDisplayTemperature("unknown", 92.0, 71.9)!!, 0.001)
        assertEquals(71.9, zoneDisplayTemperature(null, 92.0, 71.9)!!, 0.001)
    }

    @Test
    fun `a zone with no room sensor is left exactly as it was`() {
        // Anthony and Autumn have no sensor of their own. They must keep working, not blank out.
        assertEquals(79.0, zoneDisplayTemperature("off", headReading = 79.0, roomSensorReading = null)!!, 0.001)
        assertEquals(70.0, zoneDisplayTemperature("heat", headReading = 70.0, roomSensorReading = null)!!, 0.001)
    }

    @Test
    fun `a failed room sensor cannot blank a card or hide a running head`() {
        assertEquals(92.0, zoneDisplayTemperature("off", headReading = 92.0, roomSensorReading = null)!!, 0.001)
        assertEquals(71.9, zoneDisplayTemperature("heat", headReading = null, roomSensorReading = 71.9)!!, 0.001)
        assertNull(zoneDisplayTemperature("off", headReading = null, roomSensorReading = null))
    }
}
