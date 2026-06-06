package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.tradingview.lightweightcharts.api.series.models.Time
import com.tradingview.lightweightcharts.api.series.models.TimeRange
import com.tradingview.lightweightcharts.help.isNumber
import com.tradingview.lightweightcharts.help.isString

class TimeRangeDeserializer : Deserializer<TimeRange>() {

    override fun deserialize(json: JsonElement): TimeRange? {
        if (!json.isJsonObject) {
            return null
        }
        val jsonObject = json.asJsonObject
        val from = jsonObject.get("from")
        val to = jsonObject.get("to")

        return when {
            from.isNumber() -> {
                TimeRange(Time.Utc(from.asLong), Time.Utc(to.asLong))
            }
            from.isString() -> {
                TimeRange(Time.StringTime(from.asString), Time.StringTime(to.asString))
            }
            from.isJsonObject -> {
                fun parseBusinessDay(date: JsonObject): Time.BusinessDay {
                    return Time.BusinessDay(
                        date.get("year").asInt,
                        date.get("month").asInt,
                        date.get("day").asInt
                    )
                }

                val fromDate = runCatching { from.asJsonObject.let(::parseBusinessDay) }.getOrNull()
                    ?: Time.Utc(to.asLong)

                val toDate = runCatching { to.asJsonObject.let(::parseBusinessDay) }.getOrNull()
                    ?: Time.Utc(to.asLong)

                TimeRange(fromDate, toDate)
            }
            else -> null
        }
    }
}
