package io.baiyanwu.coinmonitor.domain.model

data class MarketQuote(
    val id: String,
    val symbol: String,
    val name: String,
    val priceUsd: Double,
    val change24hPercent: Double?,
    val poolAddress: String? = null,
    val poolTokenSide: PoolTokenSide? = null,
    /** 发起报价请求时使用的池绑定，用于拒绝过期请求回写。 */
    val requestedPoolAddress: String? = null,
    val requestedPoolTokenSide: PoolTokenSide? = null,
    val resetTrend: Boolean = false
)
