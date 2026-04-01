package com.tradingview.lightweightcharts.api.serializer

import android.util.SizeF
import com.google.gson.JsonElement

class SizeDeserializer : Deserializer<SizeF>() {
    override fun deserialize(json: JsonElement): SizeF {
        val width = json.asJsonObject["width"].asFloat
        val height = json.asJsonObject["height"].asFloat
        return SizeF(width, height)
    }
}
