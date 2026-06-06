package com.tradingview.lightweightcharts.api.series.models

import com.tradingview.lightweightcharts.api.chart.models.color.IntColor

data class AreaData(
    /**
     * Optional line color value for certain data item. If missed, color from options is used
     */
    val lineColor: IntColor? = null,

    /**
     * Optional top color value for certain data item. If missed, color from options is used
     */
    val topColor: IntColor? = null,

    /**
     * Optional bottom color value for certain data item. If missed, color from options is used
     */
    val bottomColor: IntColor? = null,

    /**
     * The time of the data.
     */
    override val time: Time,

    /**
     * Price value of the data.
     */
    override val value: Float,
) : SingleValueData
