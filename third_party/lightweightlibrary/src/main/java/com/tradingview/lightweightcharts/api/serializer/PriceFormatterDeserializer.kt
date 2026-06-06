package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.tradingview.lightweightcharts.api.series.common.PriceFormatter

class PriceFormatterDeserializer : Deserializer<PriceFormatter>() {
    override fun deserialize(json: JsonElement): PriceFormatter? {
        return try {
            gson.fromJson(json, PriceFormatter::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }
}
