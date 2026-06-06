package io.baiyanwu.coinmonitor.domain.model

data class WatchItem(
    val id: String,
    val symbol: String,
    val name: String,
    val exchangeSource: ExchangeSource,
    val marketType: MarketType = MarketType.CEX_SPOT,
    val chainFamily: ChainFamily? = null,
    val chainIndex: String? = null,
    val tokenAddress: String? = null,
    val iconUrl: String? = null,
    val overlaySelected: Boolean = false,
    val addedAt: Long,
    val homePinned: Boolean = false,
    val homeOrder: Long = addedAt,
    val homePinnedOrder: Long? = null,
    val lastPrice: Double? = null,
    val previousPrice: Double? = null,
    val liveTrend: LivePriceTrend = LivePriceTrend.NEUTRAL,
    val change24hPercent: Double? = null,
    val lastUpdatedAt: Long? = null
) {
    val baseSymbol: String
        get() {
            val normalizedSymbol = symbol.uppercase()
            return when {
                marketType == MarketType.CEX_USDT_FUTURES &&
                    normalizedSymbol.endsWith(USDT_QUOTE_ASSET) &&
                    normalizedSymbol.length > USDT_QUOTE_ASSET.length -> {
                    normalizedSymbol.dropLast(USDT_QUOTE_ASSET.length)
                }

                normalizedSymbol.contains("/") -> normalizedSymbol.substringBefore("/")
                else -> normalizedSymbol
            }
        }

    companion object {
        private const val USDT_QUOTE_ASSET = "USDT"
    }
}
