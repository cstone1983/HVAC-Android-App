package com.example

import com.example.model.headOutdoorUnit
import com.example.model.outdoorUnitsFor
import com.example.model.sharesOutdoorUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * There are two outdoor units, not one. The conflict guard used to treat all seven heads as a
 * single thermal family, which meant a legal combination — heat on one condenser while the other
 * cooled — was refused on the panel and switched off by the watchdog. These pin the topology.
 */
class OutdoorUnitTest {

    @Test
    fun `bedrooms and basement share unit 1 and can block each other`() {
        assertTrue(sharesOutdoorUnit("bedroom_1", "bedroom_2"))
        assertTrue(sharesOutdoorUnit("bedroom_1", "basement"))
    }

    @Test
    fun `anthony and autumn share unit 2 and can block each other`() {
        assertTrue(sharesOutdoorUnit("anthony", "autumn"))
    }

    @Test
    fun `the two units are independent`() {
        // The case that used to produce a false conflict, and a watchdog shutdown of heat.
        assertFalse(sharesOutdoorUnit("bedroom_2", "anthony"))
        assertFalse(sharesOutdoorUnit("basement", "autumn"))
        assertFalse(sharesOutdoorUnit("bedroom_1", "anthony"))
    }

    @Test
    fun `main level straddles both units so it conflicts with everything`() {
        assertEquals(setOf(1, 2), outdoorUnitsFor("main_level"))
        for (zone in listOf("anthony", "autumn", "bedroom_1", "bedroom_2", "basement")) {
            assertTrue("main_level should share a unit with $zone", sharesOutdoorUnit("main_level", zone))
        }
    }

    @Test
    fun `an unknown zone key fails safe rather than permitting anything`() {
        // A zone added to layout_config without being added here must warn, not silently allow.
        assertEquals(setOf(1, 2), outdoorUnitsFor("sunroom"))
        assertTrue(sharesOutdoorUnit("sunroom", "anthony"))
    }

    @Test
    fun `every head maps to a unit and the twin heads sit on different ones`() {
        assertEquals(7, headOutdoorUnit.size)
        // Main level is one open space served by two heads on two different condensers, which is
        // exactly why a split there cannot be seen as a same-unit conflict.
        assertEquals(1, headOutdoorUnit["climate.hp_living_room"])
        assertEquals(2, headOutdoorUnit["climate.hp_dining_room"])
    }

    @Test
    fun `the head map and the zone map agree`() {
        val zoneHeads = mapOf(
            "main_level" to listOf("climate.hp_living_room", "climate.hp_dining_room"),
            "anthony" to listOf("climate.hp_anthony"),
            "autumn" to listOf("climate.hp_autumn"),
            "bedroom_1" to listOf("climate.hp_bedroom"),
            "bedroom_2" to listOf("climate.hp_bedroom_2"),
            "basement" to listOf("climate.hp_basement")
        )
        for ((zone, heads) in zoneHeads) {
            assertEquals(
                "zone $zone",
                outdoorUnitsFor(zone),
                heads.mapNotNull { headOutdoorUnit[it] }.toSet()
            )
        }
    }
}
