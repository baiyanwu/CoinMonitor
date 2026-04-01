package com.tradingview.lightweightcharts.api.series.models

import com.tradingview.lightweightcharts.api.series.common.SeriesData

/**
 * Represents a whitespace data item, which is a data point without a value
 */
data class WhitespaceData(
    /**
     * The time of the data.
     */
    override val time: Time
) : SeriesData
