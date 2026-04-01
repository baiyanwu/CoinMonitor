package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.tradingview.lightweightcharts.api.options.models.HistogramSeriesOptions

class HistogramSeriesOptionsDeserializer : Deserializer<HistogramSeriesOptions>() {
    override fun deserialize(json: JsonElement): HistogramSeriesOptions? {
        return try {
            gson.fromJson(json, HistogramSeriesOptions::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }
}
