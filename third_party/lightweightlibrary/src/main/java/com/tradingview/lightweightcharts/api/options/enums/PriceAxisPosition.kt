package com.tradingview.lightweightcharts.api.options.enums

import com.google.gson.annotations.SerializedName

enum class PriceAxisPosition {
    @SerializedName("left")
    LEFT,

    @SerializedName("right")
    RIGHT,

    @SerializedName("none")
    NONE
}
