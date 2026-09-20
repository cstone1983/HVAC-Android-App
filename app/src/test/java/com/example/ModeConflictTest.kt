package com.example

import com.example.model.ZoneModeSnapshot
import com.example.model.findModeConflict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard that refuses a head the equipment cannot serve. It is the most safety-relevant
 * decision in the app and it shipped untested, which is how it came to both refuse legal
 * combinations and refuse the one correction that would fix an illegal one.
 *
 * Two facts it has to respect. There are two condensers, so heat on one while the other cools is
 * legal. And Main Level is one open space served by a head on each condenser, so it can conflict
 * with anything.
 */
class ModeConflictTest {

    private fun zones(vararg pairs: Pair<String, String>) = pairs.map { (key, mode) ->
        ZoneModeSnapshot(key, key.replaceFirstChar { it.uppercase() }, mode)
    }

    private val allOff = zones(
        "main_level" to "off", "anthony" to "off", "autumn" to "off",
        "bedroom_1" to "off", "bedroom_2" to "off", "basement" to "off"
    )

    // ---- House mode is in force -------------------------------------------------------------

    @Test
    fun `a request matching the house mode is never blocked`() {
        // The corrective case. bedroom_2 is stuck cooling while the house heats; setting
        // main_level to heat is the fix and must not be vetoed by the stuck zone.
        val z = zones("main_level" to "off", "bedroom_2" to "cool")
        assertNull(findModeConflict(z, "heat", "main_level", "heat"))
    }

    @Test
    fun `a request matching the house mode is not blocked even by the twin zone`() {
        val z = zones("main_level" to "cool", "anthony" to "off")
        assertNull(findModeConflict(z, "heat", "anthony", "heat"))
    }

    @Test
    fun `a request against the house mode is blocked by the house, not by a zone`() {
        val z = zones("main_level" to "heat", "anthony" to "off")
        val c = findModeConflict(z, "heat", "anthony", "cool")
        assertNotNull(c)
        assertEquals("The house mode", c!!.blockedBy)
        assertEquals("heat", c.blockingMode)
    }

    @Test
    fun `dry counts as cooling against a heating house`() {
        val c = findModeConflict(allOff, "heat", "anthony", "dry")
        assertNotNull(c)
        assertEquals("The house mode", c!!.blockedBy)
    }

    @Test
    fun `cool and dry do not conflict with each other`() {
        val z = zones("main_level" to "dry", "anthony" to "off")
        assertNull(findModeConflict(z, "cool", "anthony", "cool"))
    }

    @Test
    fun `affected zones list only what an override would actually move`() {
        // bedroom_1 is already heating, so switching the house to heat would not move it.
        val z = zones(
            "main_level" to "cool", "bedroom_1" to "heat",
            "anthony" to "cool", "autumn" to "off"
        )
        val c = findModeConflict(z, "cool", "autumn", "heat")
        assertNotNull(c)
        assertTrue("main level should be listed", c!!.affectedZones.contains("Main_level"))
        assertTrue("anthony should be listed", c.affectedZones.contains("Anthony"))
        assertTrue(
            "a zone already in the requested family must not be listed",
            !c.affectedZones.contains("Bedroom_1")
        )
        assertTrue("the target zone should be listed", c.affectedZones.contains("Autumn"))
    }

    // ---- House mode off, manual per-zone control ---------------------------------------------

    @Test
    fun `the two condensers are independent when the house mode is off`() {
        // The combination that used to be refused by the app and switched off by the watchdog.
        // Basement is unit 1, Anthony is unit 2.
        val z = zones("basement" to "heat", "anthony" to "off")
        assertNull(findModeConflict(z, "off", "anthony", "cool"))
    }

    @Test
    fun `heads on the same condenser still conflict when the house mode is off`() {
        // bedroom_2 and basement are both unit 1.
        val z = zones("basement" to "heat", "bedroom_2" to "off")
        val c = findModeConflict(z, "off", "bedroom_2", "cool")
        assertNotNull(c)
        assertEquals("Basement", c!!.blockedBy)
        assertEquals("heat", c.blockingMode)
    }

    @Test
    fun `anthony and autumn share unit two and block each other`() {
        val z = zones("anthony" to "cool", "autumn" to "off")
        assertNotNull(findModeConflict(z, "off", "autumn", "heat"))
    }

    @Test
    fun `main level conflicts with either condenser because it straddles both`() {
        for (zone in listOf("anthony", "autumn", "bedroom_1", "bedroom_2", "basement")) {
            val z = zones("main_level" to "heat", zone to "off")
            assertNotNull(
                "main_level heating should block cool in $zone",
                findModeConflict(z, "off", zone, "cool")
            )
        }
    }

    @Test
    fun `main level is named as the blocker ahead of other zones on the same unit`() {
        // Both are unit 1 and both conflict; the open living space is the more useful answer.
        val z = zones("basement" to "heat", "main_level" to "heat", "bedroom_2" to "off")
        val c = findModeConflict(z, "off", "bedroom_2", "cool")
        assertEquals("Main_level", c!!.blockedBy)
    }

    @Test
    fun `an off or unavailable zone never blocks anything`() {
        val z = zones(
            "main_level" to "off", "basement" to "unavailable",
            "bedroom_2" to "unknown", "bedroom_1" to "off"
        )
        assertNull(findModeConflict(z, "off", "bedroom_1", "heat"))
        assertNull(findModeConflict(z, "off", "bedroom_1", "cool"))
    }

    @Test
    fun `fan_only neither blocks nor is blocked`() {
        val z = zones("basement" to "fan_only", "bedroom_2" to "off")
        assertNull(findModeConflict(z, "off", "bedroom_2", "heat"))
    }

    // ---- Requests that carry no thermal demand ------------------------------------------------

    @Test
    fun `turning a zone off is never a conflict`() {
        val z = zones("main_level" to "heat", "anthony" to "cool")
        assertNull(findModeConflict(z, "heat", "anthony", "off"))
        assertNull(findModeConflict(z, "heat", "anthony", "fan_only"))
    }

    // ---- Degenerate input ---------------------------------------------------------------------

    @Test
    fun `an unknown target zone key fails safe rather than permitting anything`() {
        // outdoorUnitsFor returns both units for an unknown key, so a zone added to config
        // without being added to the topology map warns instead of silently allowing.
        val z = zones("basement" to "heat")
        assertNotNull(findModeConflict(z, "off", "sunroom", "cool"))
    }

    @Test
    fun `an empty zone list produces no conflict`() {
        assertNull(findModeConflict(emptyList(), "off", "anthony", "heat"))
    }

    @Test
    fun `an unavailable house mode falls through to the per-unit check`() {
        // "unavailable" is neutral, so it must not be read as a house mode in force.
        val z = zones("basement" to "heat", "bedroom_2" to "off")
        val c = findModeConflict(z, "unavailable", "bedroom_2", "cool")
        assertNotNull(c)
        assertEquals("Basement", c!!.blockedBy)
    }

    @Test
    fun `case is ignored on both the house mode and the zone modes`() {
        val z = zones("basement" to "HEAT", "bedroom_2" to "off")
        assertNotNull(findModeConflict(z, "OFF", "bedroom_2", "Cool"))
        assertNull(findModeConflict(z, "HEAT", "bedroom_2", "heat"))
    }

    @Test
    fun `a zone does not block itself`() {
        // Switching a cooling zone to heat must not report that zone as its own blocker.
        val z = zones("anthony" to "cool", "autumn" to "off")
        assertNull(findModeConflict(z, "off", "anthony", "heat"))
    }
}
