package com.tradingview.lightweightcharts.api.series.enums

import com.google.gson.*
import com.tradingview.lightweightcharts.help.isNumber
import com.tradingview.lightweightcharts.help.requireInt
import java.lang.reflect.Type

enum class LineType(val value: Int) {
    SIMPLE(0),
    WITH_STEPS(1),
    CURVED(2);

    companion object {
        fun from(value: Int): LineType = values().first { it.value == value }
    }

    class LineTypeAdapter : JsonSerializer<LineType>, JsonDeserializer<LineType> {
        override fun serialize(
            src: LineType?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?,
        ): JsonElement {
            return JsonPrimitive(src?.value ?: 0)
        }

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?,
        ): LineType {
            return when {
                json.isNumber() -> from(json.requireInt())
                else -> SIMPLE
            }
        }
    }
}
