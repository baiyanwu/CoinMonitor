package com.tradingview.lightweightcharts.api.series.enums

import com.google.gson.*
import com.tradingview.lightweightcharts.help.isNumber
import com.tradingview.lightweightcharts.help.requireInt
import java.lang.reflect.Type

enum class PriceLineSource(val value: Int) {
    LAST_BAR(0),
    LAST_VISIBLE(1);

    companion object {
        fun from(value: Int): PriceLineSource = values().first { it.value == value }
    }

    class PriceLineSourceAdapter : JsonSerializer<PriceLineSource>, JsonDeserializer<PriceLineSource> {
        override fun serialize(
            src: PriceLineSource?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return JsonPrimitive(src?.value ?: 0)
        }

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): PriceLineSource {
            return when {
                json.isNumber() -> from(json.requireInt())
                else -> LAST_BAR
            }
        }
    }
}
