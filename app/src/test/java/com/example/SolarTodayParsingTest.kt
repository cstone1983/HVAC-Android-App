package com.example

import com.example.api.SolarCalcClient
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
 * The Solar Calc contract, exercised against a real captured response.
 *
 * The fixture in `src/test/resources/solar_today_v1.json` is an actual reply from the live
 * endpoint, not a hand-written example. That matters: the hand-written sample in the spec and
 * what the server really sends already differed in small ways — a third line on the Today so far
 * tile, extra keys in `data`, a `configured` field nobody mentioned. Parsing has to survive all
 * of that, because the server will keep gaining fields and the panels will not be rebuilt each
 * time one appears.
 */
class SolarTodayParsingTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(SolarTodayResponse::class.java)

    private fun fixture(): String =
        javaClass.classLoader!!.getResourceAsStream("solar_today_v1.json")!!
            .bufferedReader().use { it.readText() }

    private fun parsed(): SolarTodayResponse = adapter.fromJson(fixture())!!

    @Test
    fun `parses the real response`() {
        val r = parsed()
        assertEquals(1, r.version)
        assertEquals(true, r.ready)
        assertEquals("America/New_York", r.tz)
        assertEquals("2026-10-15", r.day)
        assertEquals(false, r.stale)
        assertTrue(r.isSupported)
    }

    @Test
    fun `the three tiles arrive in the order the site shows them`() {
        val ids = parsed().tiles.orEmpty().map { it.id }
        assertEquals(listOf("right_now", "so_far", "by_midnight"), ids)
    }

    @Test
    fun `tile values are taken as strings, already formatted`() {
        // The whole point of the endpoint: the app renders what it is given and rounds nothing.
        val soFar = parsed().tiles.orEmpty().first { it.id == "so_far" }
        val made = soFar.items.orEmpty().first { it.name == "Made" }
        assertEquals("0.2", made.value)
        assertEquals("kWh", made.unit)
        assertEquals("solar", made.tone)
        assertEquals("solar", made.dot)
    }

    @Test
    fun `by midnight marks its values small, because there are three of them`() {
        val tile = parsed().tiles.orEmpty().first { it.id == "by_midnight" }
        assertEquals(3, tile.items.orEmpty().size)
        assertTrue(tile.items.orEmpty().all { it.small == true })
    }

    @Test
    fun `highlights name a substring that is actually in the text`() {
        // The renderer looks the highlight up with indexOf, so a highlight that is not present
        // would silently lose its emphasis.
        parsed().tiles.orEmpty().flatMap { it.lines.orEmpty() }.forEach { line ->
            val h = line.highlight
            if (!h.isNullOrEmpty()) {
                assertTrue(
                    "highlight '$h' is not in '${line.text}'",
                    line.text.orEmpty().contains(h)
                )
            }
        }
    }

    @Test
    fun `extra tile lines and unknown data keys do not break anything`() {
        // The live response carries a third line the spec's example did not, and extra keys in
        // `data`. Both must simply come through.
        val soFar = parsed().tiles.orEmpty().first { it.id == "so_far" }
        assertEquals(3, soFar.lines.orEmpty().size)
        val byMidnight = parsed().tiles.orEmpty().first { it.id == "by_midnight" }
        assertNotNull(byMidnight.data?.get("forecast_kwh"))
    }

    @Test
    fun `the chart arrays are as long as the day is`() {
        val c = parsed().chart!!
        assertEquals(288, c.slots)
        assertEquals(5, c.stepMinutes)
        assertEquals(288, c.generationKw.orEmpty().size)
        assertEquals(288, c.houseKw.orEmpty().size)
    }

    @Test
    fun `unmeasured slots are null rather than zero`() {
        // A null is "not measured yet"; a zero is "the array made nothing". Drawing the future as
        // a flat zero line would be a lie, and an easy one to introduce.
        val gen = parsed().chart!!.generationKw.orEmpty()
        assertTrue(gen.any { it == null })
        assertTrue(gen.filterNotNull().isNotEmpty())
    }

    @Test
    fun `the model covers the whole day while generation stops at the last sample`() {
        val c = parsed().chart!!
        assertEquals(24, c.model.orEmpty().size)
        val measured = c.generationKw.orEmpty().count { it != null }
        assertTrue("measured $measured should be short of the full day", measured < c.slots!!)
    }

    @Test
    fun `treeline spans parse`() {
        val spans = parsed().chart!!.behindTrees.orEmpty()
        assertTrue(spans.isNotEmpty())
        spans.forEach {
            assertNotNull(it.from)
            assertNotNull(it.to)
        }
    }

    // --- the shapes that are not in the fixture ------------------------------------------

    @Test
    fun `ready false parses and carries no tiles`() {
        val r = adapter.fromJson(
            """{"version":1,"ready":false,"tz":"America/New_York","day":"2026-10-16"}"""
        )!!
        assertEquals(false, r.ready)
        assertTrue(r.tiles.orEmpty().isEmpty())
        assertNull(r.chart)
        assertTrue(r.isSupported)
    }

    @Test
    fun `a clock-change day is not assumed to be 288 slots`() {
        val r = adapter.fromJson(
            """{"version":1,"ready":true,"chart":{"slots":300,"step_minutes":5,
               "start":"2026-11-01T04:00:00.000Z","generation_kw":[0.1,null],"house_kw":[]}}"""
        )!!
        assertEquals(300, r.chart!!.slots)
        assertEquals(2, r.chart!!.generationKw.orEmpty().size)
        assertTrue(r.chart!!.houseKw.orEmpty().isEmpty())
    }

    @Test
    fun `an empty response parses to nulls rather than throwing`() {
        val r = adapter.fromJson("{}")!!
        assertNull(r.version)
        assertNull(r.ready)
        assertTrue(r.tiles.orEmpty().isEmpty())
        // No version at all is treated as readable: the field was added with v1, and refusing a
        // response for lacking it would be stricter than the contract requires.
        assertTrue(r.isSupported)
    }

    @Test
    fun `a newer major version is refused`() {
        val r = adapter.fromJson("""{"version":2,"ready":true}""")!!
        assertFalse(r.isSupported)
    }

    @Test
    fun `a tile with no items or lines is still a tile`() {
        val r = adapter.fromJson(
            """{"version":1,"ready":true,"tiles":[{"id":"right_now","label":"Right now"}]}"""
        )!!
        val t = r.tiles!!.single()
        assertEquals("right_now", t.id)
        assertTrue(t.items.orEmpty().isEmpty())
        assertTrue(t.lines.orEmpty().isEmpty())
    }

    // --- URL building --------------------------------------------------------------------

    @Test
    fun `the endpoint url survives a base with or without a trailing slash`() {
        val expected = "https://solar.stoneyshome.com/api/app/v1/today"
        assertEquals(expected, SolarCalcClient.todayUrl("https://solar.stoneyshome.com/"))
        assertEquals(expected, SolarCalcClient.todayUrl("https://solar.stoneyshome.com"))
        assertEquals(expected, SolarCalcClient.todayUrl("  https://solar.stoneyshome.com/  "))
    }

    @Test
    fun `a blank base url falls back to the default rather than building a broken one`() {
        assertEquals(
            "https://solar.stoneyshome.com/api/app/v1/today",
            SolarCalcClient.todayUrl(null)
        )
        assertEquals(
            "https://solar.stoneyshome.com/api/app/v1/today",
            SolarCalcClient.todayUrl("")
        )
    }

    @Test
    fun `the LAN base url works too`() {
        assertEquals(
            "http://10.10.1.156:8080/api/app/v1/today",
            SolarCalcClient.todayUrl("http://10.10.1.156:8080")
        )
    }
}
