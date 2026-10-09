package io.baiyanwu.coinmonitor.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeQuoteHistoryTest {
    @Test
    fun `invalid and stale samples never fabricate or reverse a trend`() {
        val history = HomeQuoteHistory()
        history.record("a", "pool-a", null, 1L)
        history.record("a", "pool-a", Double.NaN, 2L)
        history.record("a", "pool-a", Double.POSITIVE_INFINITY, 3L)
        history.record("a", "pool-a", -1.0, 4L)
        assertTrue(history.observe("a").value.isEmpty())
        history.record("a", "pool-a", 1.0, 20L)
        history.record("a", "pool-a", 2.0, 10L)
        assertEquals(listOf(1.0), history.observe("a").value.map { it.value })
    }

    @Test
    fun `hero and list subscriptions do not double count the same quote`() {
        val history = HomeQuoteHistory()
        history.record("a", "same", 1.0, 1L)
        history.record("a", "same", 1.0, 1L)
        history.record("a", "same", 1.2, 1L)
        assertEquals(1, history.observe("a").value.size)
        assertEquals(1.2, history.observe("a").value.single().value, 0.0)
    }

    @Test
    fun `samples are bounded and reset when the source pool changes`() {
        val history = HomeQuoteHistory(maximumSamples = 3)
        (1L..8L).forEach { history.record("a", "pool-a", it.toDouble(), it) }
        assertEquals(listOf(6L, 7L, 8L), history.observe("a").value.map { it.timestampMillis })
        history.record("a", "pool-b", 100.0, 9L)
        assertEquals(listOf(100.0), history.observe("a").value.map { it.value })
    }

    @Test
    fun `removed assets stop accumulating samples and other assets retain their history`() {
        val history = HomeQuoteHistory()
        val removed = history.observe("a")
        history.record("a", "a", 1.0, 1L)
        history.record("b", "b", 2.0, 2L)
        history.retainOnly(setOf("b"))
        history.record("a", "a", 3.0, 3L)
        assertTrue(removed.value.isEmpty())
        assertEquals(listOf(2.0), history.observe("b").value.map { it.value })
    }
}
