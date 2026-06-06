package com.tradingview.lightweightcharts.api.options.common

import com.tradingview.lightweightcharts.api.chart.models.color.Colorable
import com.tradingview.lightweightcharts.api.series.enums.LastPriceAnimationMode
import com.tradingview.lightweightcharts.api.series.enums.LineStyle
import com.tradingview.lightweightcharts.api.series.enums.LineType
import com.tradingview.lightweightcharts.api.series.enums.LineWidth
import com.tradingview.lightweightcharts.api.series.models.BaseValueType

/**
 * Represents style options for a baseline series.
 */
interface BaselineStyleOptions {
    /**
     * Base value of the series.
     */
    val baseValue: BaseValueType?

    /**
     * The first color of the top area.
     */
    val topFillColor1: Colorable?

    /**
     * The second color of the top area.
     */
    val topFillColor2: Colorable?

    /**
     * The line color of the top area.
     */
    val topLineColor: Colorable?

    /**
     * The first color of the bottom area.
     */
    val bottomFillColor1: Colorable?

    /**
     * The second color of the bottom area.
     */
    val bottomFillColor2: Colorable?

    /**
     * The line color of the bottom area.
     */
    val bottomLineColor: Colorable?

    /**
     * Line width.
     */
    val lineWidth: LineWidth?

    /**
     * Line style.
     */
    val lineStyle: LineStyle?

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
     * Crosshair marker border color.
     * *NoColor* falls back to the the color of the series under the crosshair.
     * @see com.tradingview.lightweightcharts.api.chart.models.color.NoColor
     */
    val crosshairMarkerBorderColor: Colorable?

    /**
     * The crosshair marker background color.
     * *NoColor* falls back to the the color of the series under the crosshair.
     * @see com.tradingview.lightweightcharts.api.chart.models.color.NoColor
     */
    val crosshairMarkerBackgroundColor: Colorable?

    /**
     * Crosshair marker border width in pixels.
     */
    val crosshairMarkerBorderWidth: Float?

    /**
     * Last price animation mode.
     */
    val lastPriceAnimation: LastPriceAnimationMode?
}
