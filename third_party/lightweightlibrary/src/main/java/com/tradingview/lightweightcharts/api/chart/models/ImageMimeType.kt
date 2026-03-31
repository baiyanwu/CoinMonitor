package com.tradingview.lightweightcharts.api.chart.models

import com.google.gson.annotations.SerializedName

enum class ImageMimeType {
    @SerializedName("image/jpeg")
    JPEG,

    @SerializedName("image/png")
    PNG,

    @SerializedName("image/webp")
    WEBP
}
