package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.tradingview.lightweightcharts.api.options.models.AreaSeriesOptions

class AreaSeriesOptionsDeserializer : Deserializer<AreaSeriesOptions>() {
    override fun deserialize(json: JsonElement): AreaSeriesOptions? {
        return try {
            gson.fromJson(json, AreaSeriesOptions::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }
}
