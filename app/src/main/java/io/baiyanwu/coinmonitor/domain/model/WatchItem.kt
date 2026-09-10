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
    val poolAddress: String? = null,
    val poolTokenSide: PoolTokenSide? = null,
    val iconUrl: String? = null,
    val overlaySelected: Boolean = false,
    val overlayOrder: Long? = null,
    val addedAt: Long,
    val homePinned: Boolean = false,
    val homeOrder: Long = addedAt,
    val homePinnedOrder: Long? = null,
    val lastPrice: Double? = null,
    val previousPrice: Double? = null,
    val liveTrend: LivePriceTrend = LivePriceTrend.NEUTRAL,
    val change24hPercent: Double? = null,
    val marketCap: Double? = null,
    val lastUpdatedAt: Long? = null,
    val selectedPool: OnchainPoolOption? = null,
    val poolOptions: List<OnchainPoolOption> = emptyList()
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

    val semanticKey: String
        get() = if (marketType == MarketType.ONCHAIN_TOKEN) {
            val chain = chainIndex.orEmpty()
            val address = normalizeOnchainAddress(chainFamily, tokenAddress.orEmpty())
            if (chain.isNotBlank() && address.isNotBlank()) "onchain:$chain:$address" else id
        } else {
            id
        }

    companion object {
        private const val USDT_QUOTE_ASSET = "USDT"
    }
}
