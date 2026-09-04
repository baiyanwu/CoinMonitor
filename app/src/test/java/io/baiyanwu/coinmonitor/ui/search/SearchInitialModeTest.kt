package io.baiyanwu.coinmonitor.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchInitialModeTest {
    @Test
    fun `initial search mode resolves valid values`() {
        assertEquals(SearchMode.EXCHANGE, resolveInitialSearchMode(SearchMode.EXCHANGE.name))
        assertEquals(SearchMode.ONCHAIN, resolveInitialSearchMode(SearchMode.ONCHAIN.name))
    }

    @Test
    fun `initial search mode falls back to exchange`() {
        assertEquals(SearchMode.EXCHANGE, resolveInitialSearchMode(null))
        assertEquals(SearchMode.EXCHANGE, resolveInitialSearchMode("unsupported"))
    }
}
