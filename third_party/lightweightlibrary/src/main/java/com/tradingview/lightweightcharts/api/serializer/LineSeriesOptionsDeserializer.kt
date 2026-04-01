package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.tradingview.lightweightcharts.api.options.models.LineSeriesOptions

class LineSeriesOptionsDeserializer : Deserializer<LineSeriesOptions>() {
    override fun deserialize(json: JsonElement): LineSeriesOptions? {
        return try {
            gson.fromJson(json, LineSeriesOptions::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }
}
