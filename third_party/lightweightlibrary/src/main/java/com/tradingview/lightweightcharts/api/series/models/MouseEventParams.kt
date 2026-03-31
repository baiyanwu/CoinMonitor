package com.tradingview.lightweightcharts.api.series.models

data class MouseEventParams(
    /**
     * The X coordinate of the mouse pointer in local (DOM content) coordinates.
     */
    val time: Time? = null,
    /**
     * Logical index
     */
    val logical: Int? = null,

    /**
     * Location of the event in the chart.
     */
    val point: Point? = null,

    /**
     * Data of all series at the location of the event in the chart.
     */
    val seriesData: List<BarPrices>? = null,

    /**
     * The SeriesApi uuid at the point of the mouse event.
     */
    val hoveredSeries: String? = null,

    /**
     * The underlying source mouse or touch event data, if available
     */
    val sourceEvent: TouchMouseEventData? = null,

)
