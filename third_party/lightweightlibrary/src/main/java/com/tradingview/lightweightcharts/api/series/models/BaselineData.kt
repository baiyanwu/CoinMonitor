package com.tradingview.lightweightcharts.api.series.models

import com.tradingview.lightweightcharts.api.chart.models.color.IntColor

data class BaselineData(
    /**
     * Optional top area top fill color value for certain data item. If missed, color from options is used
     */
    val topFillColor1: IntColor? = null,

    /**
     * Optional top area bottom fill color value for certain data item. If missed, color from options is used
     */
    val topFillColor2: IntColor? = null,

    /**
     * Optional top area line color value for certain data item. If missed, color from options is used
     */
    val topLineColor: IntColor? = null,

    /**
     * Optional bottom area top fill color value for certain data item. If missed, color from options is used
     */
    val bottomFillColor1: IntColor? = null,

    /**
     * Optional bottom area bottom fill color value for certain data item. If missed, color from options is used
     */
    val bottomFillColor2: IntColor? = null,

    /**
     * Optional bottom area line color value for certain data item. If missed, color from options is used
     */
    val bottomLineColor: IntColor? = null,

    /**
     * The time of the data.
     */
    override val time: Time,

    /**
     * Price value of the data.
     */
    override val value: Float,
) : SingleValueData
