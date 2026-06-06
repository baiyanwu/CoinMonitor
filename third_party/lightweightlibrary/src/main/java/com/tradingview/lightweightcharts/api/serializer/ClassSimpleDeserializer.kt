package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.tradingview.lightweightcharts.api.series.common.SeriesData

class ClassSimpleDeserializer<T : SeriesData>(private val clazz: Class<T>) : Deserializer<T>() {

    override fun deserialize(json: JsonElement): T? {
        return try {
            gson.fromJson(json, clazz)
        } catch (_: JsonSyntaxException) {
            null
        }
    }
}
