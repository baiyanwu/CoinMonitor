package io.baiyanwu.coinmonitor.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CoinSymbolIconTest {
    @Test
    fun `coil icon candidates preserve preferred fallback and symbol order without duplicates`() {
        assertEquals(
            listOf("token", "chain-1", "chain-2", "symbol"),
            coilIconCandidateUrls(
                preferredUrl = "token",
                fallbackUrls = listOf("chain-1", "", "chain-2", "token"),
                resolvedSymbolUrl = "symbol"
            )
        )
    }
}
