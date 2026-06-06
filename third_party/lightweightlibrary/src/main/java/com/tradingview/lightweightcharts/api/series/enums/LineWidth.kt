package com.tradingview.lightweightcharts.api.series.enums

import com.google.gson.*
import com.tradingview.lightweightcharts.help.isNumber
import com.tradingview.lightweightcharts.help.requireInt
import java.lang.reflect.Type

enum class LineWidth(val value: Int) {
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4);

    companion object {
        fun from(value: Int): LineWidth = values().first { it.value == value }
    }

    class LineWidthAdapter : JsonSerializer<LineWidth>, JsonDeserializer<LineWidth> {
        override fun serialize(
            src: LineWidth?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return JsonPrimitive(src?.value ?: 1)
        }

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): LineWidth {
            return when {
                json.isNumber() -> from(json.requireInt())
                else -> ONE
            }
        }
    }
}
