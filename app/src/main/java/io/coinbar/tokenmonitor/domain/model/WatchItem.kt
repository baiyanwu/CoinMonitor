package io.coinbar.tokenmonitor.domain.model

data class WatchItem(
    val id: String,
    val symbol: String,
    val name: String,
    val exchangeSource: ExchangeSource,
    val overlaySelected: Boolean = false,
    val addedAt: Long,
    val lastPrice: Double? = null,
    val previousPrice: Double? = null,
    val liveTrend: LivePriceTrend = LivePriceTrend.NEUTRAL,
    val change24hPercent: Double? = null,
    val lastUpdatedAt: Long? = null
) {
    val baseSymbol: String
        get() = symbol.substringBefore("/").uppercase()
}
