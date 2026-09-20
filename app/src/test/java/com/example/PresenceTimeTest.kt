package com.example

import com.example.ui.formatElapsed
import com.example.ui.parseHaTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home Assistant emits microsecond precision ("...:06.787957+00:00"). SimpleDateFormat reads
 * "SSS" greedily, so the naive parse turns 787957 into 787,957 milliseconds and reports an
 * arrival roughly 13 minutes in the future. These pin that down.
 */
class PresenceTimeTest {

    private fun utc(millis: Long) = millis

    @Test
    fun `microsecond precision does not drift`() {
        val withMicros = parseHaTimestamp("2026-09-19T22:40:06.787957+00:00")!!
        val withMillis = parseHaTimestamp("2026-09-19T22:40:06.787+00:00")!!
        // Same instant to the millisecond; a greedy parse would differ by ~787 seconds.
        assertEquals(withMillis, withMicros)
    }

    @Test
    fun `offset is honoured`() {
        val utcTime = parseHaTimestamp("2026-09-19T12:00:00+00:00")!!
        val minusFour = parseHaTimestamp("2026-09-19T08:00:00-04:00")!!
        assertEquals(utcTime, minusFour)
    }

    @Test
    fun `Z and offsetless forms parse as UTC`() {
        val z = parseHaTimestamp("2026-09-19T12:00:00Z")!!
        val bare = parseHaTimestamp("2026-09-19T12:00:00")!!
        val explicit = parseHaTimestamp("2026-09-19T12:00:00+00:00")!!
        assertEquals(explicit, z)
        assertEquals(explicit, bare)
    }

    @Test
    fun `offset without a colon still parses`() {
        assertEquals(
            parseHaTimestamp("2026-09-19T12:00:00+00:00"),
            parseHaTimestamp("2026-09-19T12:00:00+0000")
        )
    }

    @Test
    fun `garbage returns null rather than throwing`() {
        assertNull(parseHaTimestamp(null))
        assertNull(parseHaTimestamp(""))
        assertNull(parseHaTimestamp("unknown"))
        assertNull(parseHaTimestamp("not a timestamp"))
    }

    @Test
    fun `elapsed formatting covers the useful ranges`() {
        val now = 1_000_000_000_000L
        assertEquals("just now", formatElapsed(now - 30_000L, now))
        assertEquals("42m", formatElapsed(now - 42 * 60_000L, now))
        assertEquals("5h 12m", formatElapsed(now - (5 * 60 + 12) * 60_000L, now))
        assertEquals("2d 3h", formatElapsed(now - ((2 * 24 + 3) * 60) * 60_000L, now))
    }

    @Test
    fun `an arrival earlier today reports a sane duration`() {
        val now = System.currentTimeMillis()
        val arrived = parseHaTimestamp(
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'+00:00'", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .format(java.util.Date(now - 3 * 60 * 60 * 1000L))
        )!!
        assertTrue("arrival must be in the past", arrived <= now)
        assertEquals("3h 0m", formatElapsed(arrived, now))
    }
}
