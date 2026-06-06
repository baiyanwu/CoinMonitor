package com.tradingview.lightweightcharts.api.series.enums

import com.google.gson.*
import com.tradingview.lightweightcharts.help.isNumber
import com.tradingview.lightweightcharts.help.requireInt
import java.lang.reflect.Type

enum class LineStyle(val value: Int) {
    SOLID(0),
    DOTTED(1),
    DASHED(2),
    LARGE_DASHED(3),
    SPARSE_DOTTED(4);

    companion object {
        fun from(value: Int): LineStyle = values().first { it.value == value }
    }

    class LineStyleAdapter : JsonSerializer<LineStyle>, JsonDeserializer<LineStyle> {
        override fun serialize(
            src: LineStyle?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return JsonPrimitive(src?.value ?: 0)
        }

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): LineStyle {
            return when {
                json.isNumber() -> from(json.requireInt())
                else -> SOLID
            }
        }
    }
}
