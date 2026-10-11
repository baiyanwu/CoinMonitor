package io.baiyanwu.coinmonitor.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoinMonitorSparklineTest {
    @Test
    fun `extremely small and large finite prices normalize without overflow`() {
        listOf(listOf(1e-30, 2e-30, 3e-30), listOf(1e307, 1.4e308, 1.7e308)).forEach { values ->
            val points = normalizeSparklinePoints(values.mapIndexed { index, value -> CoinMonitorChartPoint(index + 1L, value) })
            assertEquals(3, points.size)
            assertTrue(points.all { it.x.isFinite() && it.y.isFinite() && it.y in 0f..1f })
            assertEquals(1f, points.first().y)
            assertEquals(0f, points.last().y)
        }
    }

    @Test
    fun `flat prices stay flat and irregular updates use actual elapsed time`() {
        val points = normalizeSparklinePoints(listOf(CoinMonitorChartPoint(10L, 5.0), CoinMonitorChartPoint(20L, 5.0), CoinMonitorChartPoint(110L, 5.0)))
        assertTrue(points.all { it.y == 0.5f })
        assertEquals(0.1f, points[1].x, 0.00001f)
        assertTrue(normalizeSparklinePoints(listOf(CoinMonitorChartPoint(1L, 1.0))).isEmpty())
    }
}
