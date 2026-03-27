package io.coinbar.tokenmonitor.domain.model

enum class ExchangeSource(val title: String, val idPrefix: String) {
    BINANCE(title = "Binance", idPrefix = "binance:"),
    BINANCE_ALPHA(title = "Binance Alpha", idPrefix = "binance-alpha:"),
    OKX(title = "OKX", idPrefix = "okx:");

    companion object {
        val displayOrder = listOf(BINANCE, BINANCE_ALPHA, OKX)

        fun fromWatchItemId(id: String): ExchangeSource {
            return entries.firstOrNull { id.startsWith(it.idPrefix) } ?: BINANCE
        }

        fun sortRank(source: ExchangeSource): Int {
            return displayOrder.indexOf(source).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
    }
}
