package com.tradingview.lightweightcharts.api.options.models

/**
 * Defines margins of the price scale
 */
data class PriceScaleMargins(
    /**
     * Top margin in float.
     * Must be greater or equal to 0f and less than 1f
     */
    var top: Float? = null,

    /**
     *  Bottom margin in float.
     *  Must be greater or equal to 0f and less than 1f
     */
    var bottom: Float? = null
)

inline fun priceScaleMargins(init: PriceScaleMargins.() -> Unit): PriceScaleMargins {
    return PriceScaleMargins().apply(init)
}
