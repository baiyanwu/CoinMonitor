package com.tradingview.lightweightcharts.api.options.models

import com.tradingview.lightweightcharts.api.series.enums.CrosshairMode

/**
 * Structure describing crosshair options
 */
data class CrosshairOptions(
    /**
     * Crosshair mode
     */
    var mode: CrosshairMode? = null,

    /**
     * Options of the crosshair vertical line
     */
    var vertLine: CrosshairLineOptions? = null,

    /**
     * Options of the crosshair horizontal line
     */
    var horzLine: CrosshairLineOptions? = null
)

inline fun crosshairOptions(init: CrosshairOptions.() -> Unit): CrosshairOptions {
    return CrosshairOptions().apply(init)
}
