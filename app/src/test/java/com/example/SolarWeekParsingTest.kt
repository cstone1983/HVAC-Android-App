package com.example

import com.example.api.SolarTodayResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The week either side of today.
 *
 * `week` arrived after the rest of the contract shipped, which makes "a reply without it" a case
 * worth testing rather than a hypothetical: every panel ran against such a reply until the day
 * the field appeared, and will again if the server is ever rolled back.
 */
class SolarWeekParsingTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(SolarTodayResponse::class.java)

    private fun parsed(): SolarTodayResponse = adapter.fromJson(
        javaClass.classLoader!!.getResourceAsStream("solar_today_v1.json")!!
            .bufferedReader().use { it.readText() }
    )!!

    @Test
    fun `the week parses from the live reply`() {
        val w = parsed().week!!
        assertEquals("The week ahead", w.title)
        assertNotNull(w.tag)
        assertEquals(14, w.days.orEmpty().size)
    }

    @Test
    fun `days run oldest first, past then today then future`() {
        val kinds = parsed().week!!.days.orEmpty().map { it.kind }
        assertEquals("past", kinds.first())
        assertEquals(1, kinds.count { it == "today" })
        assertEquals("future", kinds.last())
        // Today sits between the two runs, never among them.
        val todayAt = kinds.indexOf("today")
        assertTrue(kinds.take(todayAt).all { it == "past" })
        assertTrue(kinds.drop(todayAt + 1).all { it == "future" })
    }

    @Test
    fun `finished days carry an actual and no so far`() {
        val past = parsed().week!!.days.orEmpty().filter { it.kind == "past" }
        assertTrue(past.isNotEmpty())
        past.forEach {
            assertNotNull("a finished day should have an actual", it.actualKwh)
            assertNull(it.soFarKwh)
        }
    }

    @Test
    fun `today carries so far and no actual`() {
        val today = parsed().week!!.days.orEmpty().single { it.isToday }
        assertNull("today is not finished, so it has no actual", today.actualKwh)
        assertNotNull(today.soFarKwh)
        assertNotNull(today.soFarLabel)
    }

    @Test
    fun `future days carry a forecast and nothing measured`() {
        val future = parsed().week!!.days.orEmpty().filter { it.isFuture }
        assertTrue(future.isNotEmpty())
        future.forEach {
            assertNull(it.actualKwh)
            assertNull(it.soFarKwh)
            assertNotNull(it.forecastKwh)
        }
    }

    @Test
    fun `every day has a readout`() {
        parsed().week!!.days.orEmpty().forEach {
            assertTrue("${it.date} has no readout", it.readout.orEmpty().isNotEmpty())
        }
    }

    @Test
    fun `the cost table parses with its tones`() {
        val c = parsed().week!!.costs!!
        assertEquals("What the week costs you", c.title)
        assertEquals(5, c.columns.orEmpty().size)
        assertEquals(7, c.rows.orEmpty().size)
        assertEquals("today", c.rows!!.first().day)
        assertTrue(c.rows!!.all { it.toBankTone in listOf("good", "warn", "plain") })
        assertNotNull(c.summary)
    }

    @Test
    fun `the minus sign in the table is a real minus, not a hyphen`() {
        // The server promises U+2212. A hyphen would render noticeably shorter and higher, and
        // the difference is invisible in a diff.
        val negatives = parsed().week!!.costs!!.rows.orEmpty()
            .map { it.toBank.orEmpty() }
            .filter { it.startsWith("−") || it.startsWith("-") }
        assertTrue("expected at least one negative row", negatives.isNotEmpty())
        negatives.forEach { assertTrue("'$it' should start with U+2212", it.startsWith("−")) }
    }

    // --- the shapes the live reply does not happen to contain -----------------------------

    @Test
    fun `a day with no forecast leaves a gap rather than a zero`() {
        // A null has to stay null all the way through. Defaulting it to 0.0 anywhere would draw
        // a line down to the axis and back, inventing a forecast of nothing.
        val r = adapter.fromJson(
            """{"version":1,"ready":true,"week":{"days":[
               {"day":"2026-10-14","weekday":"Wed","date":"Oct 14","kind":"past","actual_kwh":41.3},
               {"day":"2026-10-15","weekday":"Thu","date":"Oct 15","kind":"today","so_far_kwh":5.3}
            ]}}"""
        )!!
        val days = r.week!!.days!!
        assertNull(days[0].forecastKwh)
        assertNull(days[0].p10Kwh)
        assertNull(days[0].p90Kwh)
        assertFalse("no band without both ends", days[0].hasBand)
    }

    @Test
    fun `a band needs both ends to be drawable`() {
        val r = adapter.fromJson(
            """{"version":1,"week":{"days":[
               {"kind":"future","p10_kwh":10.0},
               {"kind":"future","p90_kwh":20.0},
               {"kind":"future","p10_kwh":10.0,"p90_kwh":20.0}
            ]}}"""
        )!!
        val days = r.week!!.days!!
        assertFalse(days[0].hasBand)
        assertFalse(days[1].hasBand)
        assertTrue(days[2].hasBand)
    }

    @Test
    fun `today with no so far yet still parses`() {
        // The small hours: today exists as a column but has nothing measured, so there is no
        // ring to draw and no label to put above it.
        val r = adapter.fromJson(
            """{"version":1,"ready":true,"week":{"days":[
               {"day":"2026-10-16","weekday":"Fri","date":"Oct 16","kind":"today",
                "forecast_kwh":90.0,"p10_kwh":70.0,"p90_kwh":105.0}]}}"""
        )!!
        val today = r.week!!.days!!.single()
        assertTrue(today.isToday)
        assertNull(today.soFarKwh)
        assertNull(today.soFarLabel)
        assertTrue(today.hasBand)
    }

    @Test
    fun `a reply with no week at all parses and hides the cards`() {
        val r = adapter.fromJson("""{"version":1,"ready":true,"tiles":[],"chart":null}""")!!
        assertNull(r.week)
        assertTrue("an older server is still a supported one", r.isSupported)
    }

    @Test
    fun `an empty week is treated as nothing to show`() {
        val r = adapter.fromJson("""{"version":1,"week":{"title":"The week ahead"}}""")!!
        assertNotNull(r.week)
        assertTrue(r.week!!.days.orEmpty().isEmpty())
        assertNull(r.week!!.costs)
    }

    @Test
    fun `an unknown weather kind is carried through rather than rejected`() {
        // The server may add kinds. Parsing must not care; the icon map decides what to draw.
        val r = adapter.fromJson(
            """{"version":1,"week":{"days":[{"kind":"future",
               "weather":{"kind":"hail","label":"hail","detail":"new"}}]}}"""
        )!!
        assertEquals("hail", r.week!!.days!!.single().weather!!.kind)
    }
}
