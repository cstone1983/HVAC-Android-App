package com.example

import com.example.ui.formatScaleKw
import com.example.ui.kwScaleStep
import org.junit.Assert.assertEquals
import org.junit.Test

/** The today chart's kW scale down the left: round steps, at most four lines above zero. */
class SolarChartScaleTest {

    @Test
    fun stepsStayRoundAndFew() {
        assertEquals(0.25f, kwScaleStep(0.6f))
        assertEquals(1f, kwScaleStep(2.4f))
        assertEquals(1f, kwScaleStep(3.45f))
        assertEquals(2f, kwScaleStep(7.5f))
        assertEquals(5f, kwScaleStep(12f))
        // Fixed at 18: 0, 5, 10, 15, 20.
        assertEquals(5f, kwScaleStep(18f))
        assertEquals(10f, kwScaleStep(40f))
    }

    @Test
    fun labelsDropTrailingZeros() {
        assertEquals("0", formatScaleKw(0f))
        assertEquals("1", formatScaleKw(1f))
        assertEquals("2.5", formatScaleKw(2.5f))
        assertEquals("0.25", formatScaleKw(0.25f))
        assertEquals("10", formatScaleKw(10f))
    }
}
