package io.baiyanwu.coinmonitor.ui.components

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class CoinIconFailureCooldownTest {
    @Test fun `failed images do not retry on each list reentry but recover after cooldown`() {
        var now = 0L
        val policy = CoinIconFailureCooldown(clock = { now })
        assertTrue(policy.canRequest("image-a"))
        policy.recordFailure("image-a")
        repeat(100) { assertFalse(policy.canRequest("image-a")) }
        assertTrue(policy.canRequest("image-b"))
        now = 59_999
        assertFalse(policy.canRequest("image-a"))
        now = 60_000
        assertTrue(policy.canRequest("image-a"))
    }

    @Test fun `scroll cancellation does not poison an image or renew an existing failure`() {
        var now = 0L
        val policy = CoinIconFailureCooldown(clock = { now })
        assertFalse(policy.recordFailure("cancelled", CancellationException()))
        assertTrue(policy.canRequest("cancelled"))
        policy.recordFailure("failed")
        now = 59_999
        assertFalse(policy.recordFailure("failed", CancellationException()))
        now = 60_000
        assertTrue(policy.canRequest("failed"))
    }

    @Test fun `successful load clears its own failure without affecting other images`() {
        val policy = CoinIconFailureCooldown(clock = { 0L })
        policy.recordFailure("a"); policy.recordFailure("b")
        policy.clear("a")
        assertTrue(policy.canRequest("a"))
        assertFalse(policy.canRequest("b"))
    }

    @Test fun `failure cache stays bounded and retains recently failed images`() {
        val policy = CoinIconFailureCooldown(maximumEntries = 2, clock = { 0L })
        policy.recordFailure("a"); policy.recordFailure("b")
        policy.recordFailure("a")
        policy.recordFailure("c")
        assertTrue(policy.canRequest("b"))
        assertFalse(policy.canRequest("a"))
        assertFalse(policy.canRequest("c"))
    }
}
