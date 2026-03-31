package com.tradingview.lightweightcharts.api.serializer

import com.tradingview.lightweightcharts.api.series.models.LogicalRange

class LogicalRangeDeserializer : Deserializer<LogicalRange>() {

    override fun deserialize(json: com.google.gson.JsonElement): LogicalRange? {
        if (!json.isJsonObject) return null
        val jsonObject = json.asJsonObject
        if (jsonObject.size() == 0) return null
        val from = jsonObject.get("from")?.asFloat ?: return null
        val to = jsonObject.get("to")?.asFloat ?: return null
        return LogicalRange(from = from, to = to)
    }
}
