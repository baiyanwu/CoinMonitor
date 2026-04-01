package com.tradingview.lightweightcharts.api.series.enums

import com.google.gson.annotations.SerializedName

enum class SeriesMarkerPosition {
    @SerializedName("aboveBar")
    ABOVE_BAR,

    @SerializedName("belowBar")
    BELOW_BAR,

    @SerializedName("inBar")
    IN_BAR,
}
