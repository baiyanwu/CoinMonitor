package io.baiyanwu.coinmonitor.ui.search

import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OnchainPoolOption
import io.baiyanwu.coinmonitor.domain.model.PoolTokenSide
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUiStateIsolationTest {
    @Test
    fun `only the latest pool selection generation may finish`() {
        val tracker = LatestPoolSelectionTracker()
        val first = tracker.next("token")
        val second = tracker.next("token")

        assertFalse(tracker.isCurrent("token", first))
        assertTrue(tracker.isCurrent("token", second))
    }

    @Test
    fun `new pool write waits for a cancelled older write before finishing`() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val first = createOrderedPoolSelectionJob(previousJob = null) {
            firstStarted.complete(Unit)
            withContext(NonCancellable) {
                releaseFirst.await()
                writes += "pool-a"
            }
        }
        first.start()
        firstStarted.await()
        first.cancel()

        val second = createOrderedPoolSelectionJob(previousJob = first) {
            writes += "pool-b"
        }
        second.start()
        assertFalse(second.isCompleted)

        releaseFirst.complete(Unit)
        second.join()

        assertEquals(listOf("pool-a", "pool-b"), writes)
    }

    @Test
    fun `switching mode exposes its own query without changing the other query`() {
        val exchangeState = SearchUiState().updatePageState(SearchMode.EXCHANGE) {
            it.copy(query = "BTC")
        }

        val onchainMode = exchangeState.copy(searchMode = SearchMode.ONCHAIN)

        assertEquals("", onchainMode.activePageState.query)
        assertEquals("BTC", onchainMode.pageState(SearchMode.EXCHANGE).query)

        val bothQueries = onchainMode.updatePageState(SearchMode.ONCHAIN) {
            it.copy(query = SOLANA_ADDRESS)
        }

        assertEquals("BTC", bothQueries.pageState(SearchMode.EXCHANGE).query)
        assertEquals(SOLANA_ADDRESS, bothQueries.pageState(SearchMode.ONCHAIN).query)
    }

    @Test
    fun `loading results and errors remain isolated by search mode`() {
        val exchangeResult = WatchItem(
            id = "binance:BTCUSDT",
            symbol = "BTC/USDT",
            name = "BTC",
            exchangeSource = ExchangeSource.BINANCE,
            marketType = MarketType.CEX_SPOT,
            addedAt = 1L
        )
        val state = SearchUiState()
            .updatePageState(SearchMode.EXCHANGE) {
                it.copy(
                    query = "BTC",
                    hasSearched = true,
                    results = listOf(exchangeResult)
                )
            }
            .updatePageState(SearchMode.ONCHAIN) {
                it.copy(
                    query = SOLANA_ADDRESS,
                    loading = true,
                    errorMessage = "temporary"
                )
            }

        val exchangePage = state.pageState(SearchMode.EXCHANGE)
        val onchainPage = state.pageState(SearchMode.ONCHAIN)

        assertEquals(listOf(exchangeResult), exchangePage.results)
        assertTrue(exchangePage.hasSearched)
        assertFalse(exchangePage.loading)
        assertEquals(null, exchangePage.errorMessage)
        assertTrue(onchainPage.results.isEmpty())
        assertTrue(onchainPage.loading)
        assertEquals("temporary", onchainPage.errorMessage)
    }

    @Test
    fun `receiving final results stops loading for exchange and onchain pages`() {
        val result = WatchItem(
            id = "binance:BTCUSDT",
            symbol = "BTC/USDT",
            name = "BTC",
            exchangeSource = ExchangeSource.BINANCE,
            marketType = MarketType.CEX_SPOT,
            addedAt = 1L
        )

        val initial = SearchUiState(
            exchangePage = SearchPageState(loading = true),
            onchainPage = SearchPageState(loading = true)
        )

        SearchMode.entries.forEach { mode ->
            val state = initial.updatePageState(mode) {
                it.withSearchResults(listOf(result))
            }
            val page = state.pageState(mode)
            assertFalse("$mode should stop loading after final results arrive", page.loading)
            assertTrue(page.hasSearched)
            assertEquals(listOf(result), page.results)
            assertEquals(null, page.errorMessage)

            val otherMode = if (mode == SearchMode.EXCHANGE) SearchMode.ONCHAIN else SearchMode.EXCHANGE
            assertTrue(state.pageState(otherMode).loading)
        }
    }

    @Test
    fun `same submitted query is ignored while active and after successful completion`() {
        assertTrue(
            shouldSkipDuplicateSearch(
                submittedQuery = "BTC",
                currentQuery = "BTC",
                pageState = SearchPageState(query = "btc", loading = true)
            )
        )
        assertTrue(
            shouldSkipDuplicateSearch(
                submittedQuery = "BTC",
                currentQuery = "BTC",
                pageState = SearchPageState(query = "btc", hasSearched = true)
            )
        )
        assertFalse(
            shouldSkipDuplicateSearch(
                submittedQuery = "BTC",
                currentQuery = "ETH",
                pageState = SearchPageState(query = "eth", hasSearched = true)
            )
        )
        assertFalse(
            shouldSkipDuplicateSearch(
                submittedQuery = "BTC",
                currentQuery = "BTC",
                pageState = SearchPageState(
                    query = "btc",
                    hasSearched = true,
                    errorMessage = "failed"
                )
            )
        )
    }

    @Test
    fun `selecting another pool updates only the matching onchain token`() {
        val first = onchainItem("onchain:1:$EVM_ADDRESS", EVM_ADDRESS, "pool-a")
        val secondAddress = "0x2222222222222222222222222222222222222222"
        val second = onchainItem("onchain:1:$secondAddress", secondAddress, "pool-c")
        val replacement = OnchainPoolOption(
            poolAddress = "pool-b",
            tokenSide = PoolTokenSide.QUOTE,
            pairLabel = "TGT / WETH",
            dexId = "uniswap",
            labels = listOf("v3"),
            liquidityUsd = 5_000.0,
            priceUsd = 3.0
        )

        val updated = SearchPageState(results = listOf(first, second))
            .withSelectedOnchainPool(first.semanticKey, replacement)

        assertEquals(2, updated.results.size)
        assertEquals("pool-b", updated.results.first().poolAddress)
        assertEquals(PoolTokenSide.QUOTE, updated.results.first().poolTokenSide)
        assertEquals("TGT / WETH", updated.results.first().selectedPool?.pairLabel)
        assertEquals("pool-c", updated.results.last().poolAddress)
    }

    private fun onchainItem(id: String, address: String, poolAddress: String) = WatchItem(
        id = id,
        symbol = "TGT",
        name = "Target",
        exchangeSource = ExchangeSource.ONCHAIN,
        marketType = MarketType.ONCHAIN_TOKEN,
        chainIndex = "1",
        tokenAddress = address,
        poolAddress = poolAddress,
        poolTokenSide = PoolTokenSide.BASE,
        addedAt = 1L
    )

    private companion object {
        const val SOLANA_ADDRESS = "So11111111111111111111111111111111111111112"
        const val EVM_ADDRESS = "0x1111111111111111111111111111111111111111"
    }
}
