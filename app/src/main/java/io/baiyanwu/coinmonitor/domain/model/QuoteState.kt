package io.baiyanwu.coinmonitor.domain.model

data class QuoteState(
    val lastPrice: Double,
    val previousPrice: Double? = null,
    val liveTrend: LivePriceTrend = LivePriceTrend.NEUTRAL,
    val change24hPercent: Double? = null,
    val lastUpdatedAt: Long? = null
)

fun WatchItem.toQuoteStateOrNull(): QuoteState? {
    val resolvedLastPrice = lastPrice ?: return null
    return QuoteState(
        lastPrice = resolvedLastPrice,
        previousPrice = previousPrice,
        liveTrend = liveTrend,
        change24hPercent = change24hPercent,
        lastUpdatedAt = lastUpdatedAt
    )
}

fun WatchItem.withQuote(quoteState: QuoteState?): WatchItem {
    if (quoteState == null) return this
    return copy(
        lastPrice = quoteState.lastPrice,
        previousPrice = quoteState.previousPrice,
        liveTrend = quoteState.liveTrend,
        change24hPercent = quoteState.change24hPercent,
        lastUpdatedAt = quoteState.lastUpdatedAt
    )
}
