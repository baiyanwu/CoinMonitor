package com.tradingview.lightweightcharts.api.options.enums

import com.google.gson.annotations.SerializedName

enum class PriceScaleMode {
    @SerializedName("0")
    NORMAL,

    @SerializedName("1")
    LOGARITHMIC,

    @SerializedName("2")
    PERCENTAGE,

    @SerializedName("3")
    INDEXED_TO_100
}
