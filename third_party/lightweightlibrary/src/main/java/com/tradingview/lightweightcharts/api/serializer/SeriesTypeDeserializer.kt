package com.tradingview.lightweightcharts.api.serializer

import com.google.gson.JsonElement
import com.tradingview.lightweightcharts.api.series.enums.SeriesType
import com.tradingview.lightweightcharts.help.isString
import java.util.*

class SeriesTypeDeserializer : Deserializer<SeriesType>() {

    override fun deserialize(json: JsonElement): SeriesType? {
        if (json.isString()) {
            return SeriesType.valueOf(json.asString.uppercase(Locale.ENGLISH))
        }
        return null
    }
}
