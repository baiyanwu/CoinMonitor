package com.tradingview.lightweightcharts.api.options.models

/**
 * Structure describing grid options
 */
data class GridOptions(
    /**
     * Vertical grid line options
     */
    var vertLines: GridLineOptions? = null,

    /**
     * Horizontal grid line options
     */
    var horzLines: GridLineOptions? = null
)

inline fun gridOptions(init: GridOptions.() -> Unit): GridOptions {
    return GridOptions().apply(init)
}
