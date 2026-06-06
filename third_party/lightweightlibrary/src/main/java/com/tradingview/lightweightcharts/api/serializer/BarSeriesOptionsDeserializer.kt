package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.tradingview.lightweightcharts.api.options.models.BarSeriesOptions

class BarSeriesOptionsDeserializer : Deserializer<BarSeriesOptions>() {
    override fun deserialize(json: JsonElement): BarSeriesOptions? {
        return try {
            gson.fromJson(json, BarSeriesOptions::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }
}
