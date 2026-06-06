package com.tradingview.lightweightcharts.api.options.models

import com.tradingview.lightweightcharts.api.chart.models.color.IntColor
import com.tradingview.lightweightcharts.api.series.enums.LineStyle
import com.tradingview.lightweightcharts.api.series.enums.LineWidth

data class PriceLineOptions(
    /**
     * The optional ID of this price line.
     */
    var id: String? = null,

    /**
     * Price line's value.
     */
    var price: Float? = null,

    /**
     * Price line's color.
     */
    var color: IntColor? = null,

    /**
     * Price line's width in pixels.
     */
    var lineWidth: LineWidth? = null,

    /**
     * Price line's style.
     */
    var lineStyle: LineStyle? = null,

    /**
     * Display line.
     */
    var lineVisible: Boolean? = null,

    /**
     * Display the current price value in on the price scale.
     */
    var axisLabelVisible: Boolean? = null,

    /**
     * Price line's on the chart pane.
     */
    var title: String? = null,
)

inline fun priceLineOptions(init: PriceLineOptions.() -> Unit): PriceLineOptions {
    return PriceLineOptions().apply(init)
}
