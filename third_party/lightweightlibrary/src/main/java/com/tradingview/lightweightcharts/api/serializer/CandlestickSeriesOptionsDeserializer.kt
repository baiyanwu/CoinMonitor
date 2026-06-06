package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.tradingview.lightweightcharts.api.options.models.CandlestickSeriesOptions

class CandlestickSeriesOptionsDeserializer : Deserializer<CandlestickSeriesOptions>() {
    override fun deserialize(json: JsonElement): CandlestickSeriesOptions? {
        return try {
            gson.fromJson(
                json,
                CandlestickSeriesOptions::class.java
            )
        } catch (_: JsonSyntaxException) {
            null
        }
    }
}
