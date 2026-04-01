package com.tradingview.lightweightcharts.api.series.enums

import com.google.gson.annotations.SerializedName

enum class SeriesMarkerShape {
    @SerializedName("circle")
    CIRCLE,

    @SerializedName("square")
    SQUARE,

    @SerializedName("arrowUp")
    ARROW_UP,

    @SerializedName("arrowDown")
    ARROW_DOWN
}
