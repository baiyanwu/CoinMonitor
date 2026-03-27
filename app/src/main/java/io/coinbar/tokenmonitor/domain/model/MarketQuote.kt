package io.coinbar.tokenmonitor.domain.model

data class MarketQuote(
    val id: String,
    val symbol: String,
    val name: String,
    val priceUsd: Double,
    val change24hPercent: Double
)

