package com.tradingview.lightweightcharts.api.options.models

import com.tradingview.lightweightcharts.api.chart.models.color.IntColor
import com.tradingview.lightweightcharts.runtime.plugins.Plugin

data class TimeScaleOptions(
    /**
     * Sets the margin space in bars from the right side of the chart
     */
    var rightOffset: Float? = null,

    /**
     * Sets the space between bars in pixels
     */
    var barSpacing: Float? = null,

    /**
     * Sets the min space between bars in pixels
     */
    var minBarSpacing: Float? = null,

    /**
     * If true, prevents scrolling to the left of the first historical bar
     */
    var fixLeftEdge: Boolean? = null,

    /**
     * If true, prevents scrolling to the right of the most recent bar
     */
    var fixRightEdge: Boolean? = null,

    /**
     * If true, prevents changing visible time area during chart resizing
     */
    var lockVisibleTimeRangeOnResize: Boolean? = null,

    /**
     * If false, the hovered bar remains in the same place when scrolling
     */
    var rightBarStaysOnScroll: Boolean? = null,

    /**
     * If true, the time scale border is visible
     */
    var borderVisible: Boolean? = null,

    /**
     * The time scale border color
     */
    var borderColor: IntColor? = null,

    /**
     * If true, the time scale is shown on a chart
     */
    var visible: Boolean? = null,

    /**
     * If true, the time is shown on the time scale and in the vertical crosshair label
     */
    var timeVisible: Boolean? = null,

    /**
     * If true, seconds are shown on the label of the crosshair vertical line
     * in hh:mm:ss format on intraday intervals
     */
    var secondsVisible: Boolean? = null,

    /**
     * If true, the visible range is shifted by the number of new bars
     * when new bars are added (note that this only applies when the last bar is visible)
     */
    var shiftVisibleRangeOnNewBar: Boolean? = null,

    /**
     * Allows to override the tick marks formatter
     */
    var tickMarkFormatter: Plugin? = null,

    /**
     * Draw small vertical line on time axis labels.
     */
    var ticksVisible: Boolean? = null,
)

inline fun timeScaleOptions(init: TimeScaleOptions.() -> Unit): TimeScaleOptions {
    return TimeScaleOptions().apply(init)
}
