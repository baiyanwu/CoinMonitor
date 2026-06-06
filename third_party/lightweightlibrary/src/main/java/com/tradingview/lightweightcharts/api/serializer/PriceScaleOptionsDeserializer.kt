package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.tradingview.lightweightcharts.api.options.models.PriceScaleOptions

class PriceScaleOptionsDeserializer : Deserializer<PriceScaleOptions>() {
    override fun deserialize(json: JsonElement): PriceScaleOptions? {
        return try {
            gson.fromJson(json, PriceScaleOptions::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }
}
