package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.tradingview.lightweightcharts.api.series.models.Time

class TimeDeserializer : Deserializer<Time>() {
    override fun deserialize(json: JsonElement): Time? {
        return try {
            gson.fromJson(json, Time::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }
}
