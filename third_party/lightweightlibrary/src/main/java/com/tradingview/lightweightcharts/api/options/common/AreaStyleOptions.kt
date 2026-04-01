package com.tradingview.lightweightcharts.api.options.common

import com.tradingview.lightweightcharts.api.chart.models.color.IntColor
import com.tradingview.lightweightcharts.api.series.enums.LastPriceAnimationMode
import com.tradingview.lightweightcharts.api.series.enums.LineStyle
import com.tradingview.lightweightcharts.api.series.enums.LineType
import com.tradingview.lightweightcharts.api.series.enums.LineWidth

interface AreaStyleOptions {
    /**
     * Color of the top part of the area.
     */
    val topColor: IntColor?

    /**
     * Color of the bottom part of the area.
     */
    val bottomColor: IntColor?

    /**
     * Invert the filled area. Fills the area above the line if set to true.
     */
    val invertFilledArea: Boolean?

    /**
     * Line color.
     */
    val lineColor: IntColor?

    /**
     * Line style.
     */
    val lineStyle: LineStyle?

    /**
     * Line width in pixels.
     */
    val lineWidth: LineWidth?

    /**
     * Line type.
     */
    val lineType: LineType?

    /**
     * Show the crosshair marker.
     */
    val crosshairMarkerVisible: Boolean?

    /**
     * Crosshair marker radius in pixels.
     */
    val crosshairMarkerRadius: Float?

    /**
     * Crosshair marker border color. An empty string falls back to the the color of the series under the crosshair.
     */
    val crosshairMarkerBorderColor: IntColor?

    /**
     * The crosshair marker background color. An empty string falls back to the the color of the series under the crosshair.
     */
    val crosshairMarkerBackgroundColor: IntColor?

    /**
     * Crosshair marker border width in pixels.
     */
    val crosshairMarkerBorderWidth: Float?

    /**
     * Last price animation mode.
     */
    val lastPriceAnimation: LastPriceAnimationMode?
}
