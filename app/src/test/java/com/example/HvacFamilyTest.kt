package com.example

import com.example.model.HvacFamily
import com.example.model.hvacFamilyOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A multi-split serves one thermal family at a time. The grouping is not obvious from the mode
 * names — dry is refrigeration on these heads, so heat + dry is a genuine conflict — and getting
 * it wrong would either block legal combinations or permit ones the equipment cannot serve.
 */
class HvacFamilyTest {

    @Test
    fun `heat stands alone`() {
        assertEquals(HvacFamily.HEAT, hvacFamilyOf("heat"))
    }

    @Test
    fun `dry belongs with cool, not with heat`() {
        assertEquals(HvacFamily.COOL, hvacFamilyOf("cool"))
        assertEquals(HvacFamily.COOL, hvacFamilyOf("dry"))
    }

    @Test
    fun `off and fan only are neutral and never conflict`() {
        assertEquals(HvacFamily.NEUTRAL, hvacFamilyOf("off"))
        assertEquals(HvacFamily.NEUTRAL, hvacFamilyOf("fan_only"))
    }

    @Test
    fun `unknown, unavailable and null are neutral rather than guessed`() {
        assertEquals(HvacFamily.NEUTRAL, hvacFamilyOf(null))
        assertEquals(HvacFamily.NEUTRAL, hvacFamilyOf(""))
        assertEquals(HvacFamily.NEUTRAL, hvacFamilyOf("unavailable"))
        assertEquals(HvacFamily.NEUTRAL, hvacFamilyOf("unknown"))
        assertEquals(HvacFamily.NEUTRAL, hvacFamilyOf("auto"))
    }

    @Test
    fun `case does not matter`() {
        assertEquals(HvacFamily.HEAT, hvacFamilyOf("HEAT"))
        assertEquals(HvacFamily.COOL, hvacFamilyOf("Dry"))
    }

    @Test
    fun `conflicting pairs are exactly the cross-family ones`() {
        val heat = hvacFamilyOf("heat")
        val cool = hvacFamilyOf("cool")
        val dry = hvacFamilyOf("dry")
        val off = hvacFamilyOf("off")

        // Real conflicts
        assert(heat != cool)
        assert(heat != dry)

        // Not conflicts
        assertEquals(cool, dry)
        assert(off == HvacFamily.NEUTRAL)
    }
}
