package com.tradingview.lightweightcharts.api.series.models

import com.tradingview.lightweightcharts.api.chart.models.color.IntColor

data class HistogramData(
    override val time: Time,
    override val value: Float,
    /**
     * Optional color value for certain data item. If missed, color from HistogramSeriesOptions is used
     */
    val color: IntColor? = null
) : SingleValueData
