package io.baiyanwu.coinmonitor.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class CoinIconServiceTest {
    @Test
    fun `preferred token icon is fetched before cached chain fallback is used`() {
        val events = mutableListOf<String>()

        val result = firstAvailableIconInOrder(
            candidates = listOf("token", "chain"),
            cached = { candidate ->
                events += "cache:$candidate"
                if (candidate == "chain") "cached-chain" else null
            },
            fetch = { candidate ->
                events += "fetch:$candidate"
                if (candidate == "token") "fetched-token" else null
            }
        )

        assertEquals("fetched-token", result)
        assertEquals(listOf("cache:token", "fetch:token"), events)
    }

    @Test
    fun `chain fallback is used only after preferred token icon fails`() {
        val events = mutableListOf<String>()

        val result = firstAvailableIconInOrder(
            candidates = listOf("token", "chain"),
            cached = { candidate ->
                events += "cache:$candidate"
                if (candidate == "chain") "cached-chain" else null
            },
            fetch = { candidate ->
                events += "fetch:$candidate"
                null
            }
        )

        assertEquals("cached-chain", result)
        assertEquals(
            listOf("cache:token", "fetch:token", "cache:chain"),
            events
        )
    }
}
