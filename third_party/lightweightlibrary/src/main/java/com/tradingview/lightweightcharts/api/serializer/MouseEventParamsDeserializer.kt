package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.*
import com.tradingview.lightweightcharts.api.series.models.MouseEventParams

class MouseEventParamsDeserializer : Deserializer<MouseEventParams>() {

    override fun deserialize(json: JsonElement): MouseEventParams? {
        return try {
            gson.fromJson(json, MouseEventParams::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }
}
