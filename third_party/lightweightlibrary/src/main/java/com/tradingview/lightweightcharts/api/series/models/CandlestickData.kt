package com.tradingview.lightweightcharts.api.series.models

import com.tradingview.lightweightcharts.api.chart.models.color.IntColor

/**
 * Structure describing a single item of data for candlestick series
 */
data class CandlestickData(
    override val time: Time,
    override val open: Float,
    override val high: Float,
    override val low: Float,
    override val close: Float,
    /**
     * Optional color value for certain data item. If missed, color from options is used
     */
    val color: IntColor? = null,
    /**
     * Optional border color value for certain data item. If missed, color from options is used
     */
    val borderColor: IntColor? = null,
    /**
     * Optional wick color value for certain data item. If missed, color from options is used
     */
    val wickColor: IntColor? = null,
) : OhlcData
