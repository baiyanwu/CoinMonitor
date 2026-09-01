package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.PoolTokenSide
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnchainQuotePersistencePolicyTest {
    @Test
    fun `late quote for pool a cannot replace the current pool b selection`() {
        val current = item(poolAddress = "pool-b")
        val staleQuote = quote(
            poolAddress = "pool-a",
            requestedPoolAddress = "pool-a"
        )

        assertFalse(shouldApplyOnchainQuote(current, staleQuote))
    }

    @Test
    fun `intentional fallback can replace the pool that its request started from`() {
        val current = item(poolAddress = "pool-a")
        val fallbackQuote = quote(
            poolAddress = "pool-b",
            requestedPoolAddress = "pool-a"
        )

        assertTrue(shouldApplyOnchainQuote(current, fallbackQuote))
    }

    private fun item(poolAddress: String?) = WatchItem(
        id = ITEM_ID,
        symbol = "TGT",
        name = "Target",
        exchangeSource = ExchangeSource.ONCHAIN,
        marketType = MarketType.ONCHAIN_TOKEN,
        chainFamily = ChainFamily.EVM,
        chainIndex = "1",
        tokenAddress = TOKEN_ADDRESS,
        poolAddress = poolAddress,
        poolTokenSide = poolAddress?.let { PoolTokenSide.BASE },
        addedAt = 1L
    )

    private fun quote(poolAddress: String, requestedPoolAddress: String?) = MarketQuote(
        id = ITEM_ID,
        symbol = "TGT",
        name = "Target",
        priceUsd = 1.0,
        change24hPercent = 0.0,
        poolAddress = poolAddress,
        poolTokenSide = PoolTokenSide.BASE,
        requestedPoolAddress = requestedPoolAddress,
        requestedPoolTokenSide = requestedPoolAddress?.let { PoolTokenSide.BASE }
    )

    private companion object {
        const val TOKEN_ADDRESS = "0x1111111111111111111111111111111111111111"
        const val ITEM_ID = "onchain:1:$TOKEN_ADDRESS"
    }
}
