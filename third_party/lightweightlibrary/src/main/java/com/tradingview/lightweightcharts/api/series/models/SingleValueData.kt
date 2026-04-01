package com.tradingview.lightweightcharts.api.series.models

import com.tradingview.lightweightcharts.api.series.common.SeriesData

interface SingleValueData : SeriesData {

    /**
     * The time of the data.
     */
    override val time: Time

    /**
     * Price value of data item
     */
    val value: Float
}
