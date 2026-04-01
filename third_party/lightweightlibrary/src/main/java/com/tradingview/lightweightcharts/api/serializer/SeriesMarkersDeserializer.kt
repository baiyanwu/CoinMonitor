package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import com.tradingview.lightweightcharts.api.series.models.SeriesMarker

class SeriesMarkersDeserializer : Deserializer<List<SeriesMarker>>() {

    override fun deserialize(json: JsonElement): List<SeriesMarker>? {
        return try {
            json.asJsonArray.map {
                gson.fromJson(it, SeriesMarker::class.java)
            }
        } catch (_: JsonSyntaxException) {
            null
        }
    }
}
