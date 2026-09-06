package io.baiyanwu.coinmonitor.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketPageUrlResolverTest {
    @Test
    fun `binance spot opens the exact exchange pair`() {
        val item = watchItem(
            id = "binance:BTCUSDT",
            symbol = "BTC/USDT",
            exchangeSource = ExchangeSource.BINANCE
        )

        assertEquals(
            "https://www.binance.com/en/trade/BTC_USDT?type=spot",
            MarketPageUrlResolver.resolve(item)
        )
    }

    @Test
    fun `binance futures opens the exact perpetual contract`() {
        val item = watchItem(
            id = "binance-futures:ETHUSDT",
            symbol = "ETHUSDT",
            exchangeSource = ExchangeSource.BINANCE,
            marketType = MarketType.CEX_USDT_FUTURES
        )

        assertEquals(
            "https://www.binance.com/en/futures/ETHUSDT",
            MarketPageUrlResolver.resolve(item)
        )
    }

    @Test
    fun `okx spot opens the exact exchange pair`() {
        val item = watchItem(
            id = "okx:BTC-USDT",
            symbol = "BTC/USDT",
            exchangeSource = ExchangeSource.OKX
        )

        assertEquals(
            "https://www.okx.com/trade-spot/btc-usdt",
            MarketPageUrlResolver.resolve(item)
        )
    }

    @Test
    fun `okx futures opens the exact perpetual contract`() {
        val item = watchItem(
            id = "okx-futures:BTC-USDT-SWAP",
            symbol = "BTCUSDT",
            exchangeSource = ExchangeSource.OKX,
            marketType = MarketType.CEX_USDT_FUTURES
        )

        assertEquals(
            "https://www.okx.com/trade-swap/btc-usdt-swap",
            MarketPageUrlResolver.resolve(item)
        )
    }

    @Test
    fun `onchain item opens its selected dexscreener pool`() {
        val item = watchItem(
            id = "onchain:1:0xtoken",
            symbol = "TOKEN",
            exchangeSource = ExchangeSource.ONCHAIN,
            marketType = MarketType.ONCHAIN_TOKEN,
            chainFamily = ChainFamily.EVM,
            chainIndex = "1",
            tokenAddress = "0xtoken",
            poolAddress = "0xPool"
        )

        assertEquals(
            "https://dexscreener.com/ethereum/0xPool",
            MarketPageUrlResolver.resolve(item)
        )
    }

    @Test
    fun `dynamic chain keeps the dexscreener chain id`() {
        val item = watchItem(
            id = "onchain:robinhood:0xtoken",
            symbol = "TOKEN",
            exchangeSource = ExchangeSource.ONCHAIN,
            marketType = MarketType.ONCHAIN_TOKEN,
            chainFamily = ChainFamily.EVM,
            chainIndex = "robinhood",
            tokenAddress = "0xtoken",
            poolAddress = "0xPool"
        )

        assertEquals(
            "https://dexscreener.com/robinhood/0xPool",
            MarketPageUrlResolver.resolve(item)
        )
    }

    @Test
    fun `legacy onchain item without a pool falls back to token search`() {
        val item = watchItem(
            id = "okx-onchain:501:SoToken",
            symbol = "TOKEN",
            exchangeSource = ExchangeSource.ONCHAIN,
            marketType = MarketType.ONCHAIN_TOKEN,
            chainFamily = ChainFamily.SOL
        )

        assertEquals(
            "https://dexscreener.com/search?q=SoToken",
            MarketPageUrlResolver.resolve(item)
        )
    }

    @Test
    fun `binance alpha opens the official alpha market directory`() {
        val item = watchItem(
            id = "binance-alpha:ALPHA_123USDT",
            symbol = "TEST/USDT",
            exchangeSource = ExchangeSource.BINANCE_ALPHA
        )

        assertEquals(
            "https://www.binance.com/en/price/binance-alpha",
            MarketPageUrlResolver.resolve(item)
        )
    }

    private fun watchItem(
        id: String,
        symbol: String,
        exchangeSource: ExchangeSource,
        marketType: MarketType = MarketType.CEX_SPOT,
        chainFamily: ChainFamily? = null,
        chainIndex: String? = null,
        tokenAddress: String? = null,
        poolAddress: String? = null
    ) = WatchItem(
        id = id,
        symbol = symbol,
        name = symbol,
        exchangeSource = exchangeSource,
        marketType = marketType,
        chainFamily = chainFamily,
        chainIndex = chainIndex,
        tokenAddress = tokenAddress,
        poolAddress = poolAddress,
        addedAt = 1L
    )
}
