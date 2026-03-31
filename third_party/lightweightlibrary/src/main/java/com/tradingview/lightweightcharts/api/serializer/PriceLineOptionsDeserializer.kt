package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.tradingview.lightweightcharts.api.options.models.PriceLineOptions

class PriceLineOptionsDeserializer : Deserializer<PriceLineOptions>() {
    override fun deserialize(json: JsonElement): PriceLineOptions? {
        return try {
            gson.fromJson(json, PriceLineOptions::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }
}
