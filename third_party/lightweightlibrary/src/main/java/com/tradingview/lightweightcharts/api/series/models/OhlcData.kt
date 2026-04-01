package com.tradingview.lightweightcharts.api.series.models

import com.tradingview.lightweightcharts.api.series.common.SeriesData

/**
 * Represents a bar with a [Time] and open, high, low, and close prices.
 */
interface OhlcData : SeriesData {
    /**
     * The bar time.
     */
    override val time: Time

    /**
     * The open price.
     */
    val open: Float

    /**
     * The high price.
     */
    val high: Float

    /**
     * The low price.
     */
    val low: Float

    /**
     * The close price.
     */
    val close: Float
}
