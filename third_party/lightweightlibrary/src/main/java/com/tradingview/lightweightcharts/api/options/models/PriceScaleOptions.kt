package com.tradingview.lightweightcharts.api.options.models

import com.tradingview.lightweightcharts.api.chart.models.color.IntColor
import com.tradingview.lightweightcharts.api.options.enums.PriceAxisPosition
import com.tradingview.lightweightcharts.api.series.enums.PriceScaleMode

data class PriceScaleOptions(
    /**
     * True makes chart calculate the price range automatically based on the visible data range
     */
    var autoScale: Boolean? = null,

    /**
     * Mode of the price scale
     */
    var mode: PriceScaleMode? = null,

    /**
     * True inverts the scale. Makes larger values drawn lower.
     * Affects both the price scale and the data on the chart
     */
    var invertScale: Boolean? = null,

    /**
     * True value prevents labels on the price scale from overlapping
     * one another by aligning them one below others
     */
    var alignLabels: Boolean? = null,

    /**
     * Defines position of the price scale on the chart
     */
    @Deprecated("")
    var position: PriceAxisPosition? = null,

    /**
     * Defines price margins for the price scale
     */
    var scaleMargins: PriceScaleMargins? = null,

    /**
     * Set true to draw a border between the price scale and the chart area
     */
    var borderVisible: Boolean? = null,

    /**
     * Defines a color of the border between the price scale and the chart area.
     * It is ignored if borderVisible is false
     */
    var borderColor: IntColor? = null,

    /**
     * Indicates whether the price scale displays only full lines of text or partial lines.
     */
    var entireTextOnly: Boolean? = null,

    /**
     * Indicates if this price scale visible. Could not be applied to overlay price scale
     */
    var visible: Boolean? = null,

    /**
     * True value add a small horizontal ticks on price axis labels
     */
    var ticksVisible: Boolean? = null
)

inline fun priceScaleOptions(init: PriceScaleOptions.() -> Unit): PriceScaleOptions {
    return PriceScaleOptions().apply(init)
}
