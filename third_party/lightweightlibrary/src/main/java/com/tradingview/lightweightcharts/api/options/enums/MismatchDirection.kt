package com.tradingview.lightweightcharts.api.options.enums

/**
 * Search direction if no data found at provided index
 */
enum class MismatchDirection(val value: Int) {

    /**
     * Search the nearest left item
     */
    NearestLeft(-1),

    /**
     * Do not search
     */
    None(0),

    /**
     * Search the nearest right item
     */
    NearestRight(1),
}
