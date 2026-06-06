package com.tradingview.lightweightcharts.api.series.models

import com.google.gson.*
import com.tradingview.lightweightcharts.help.isString
import com.tradingview.lightweightcharts.help.requireString
import java.lang.reflect.Type

data class PriceScaleId(val value: String) {
    companion object {
        val LEFT = PriceScaleId("left")
        val RIGHT = PriceScaleId("right")
    }

    class PriceScaleIdAdapter : JsonSerializer<PriceScaleId>, JsonDeserializer<PriceScaleId> {
        override fun serialize(
            src: PriceScaleId?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return JsonPrimitive(src?.value ?: "")
        }

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): PriceScaleId {
            return when {
                json.isString() -> when (val value = json.requireString()) {
                    LEFT.value -> LEFT
                    RIGHT.value -> RIGHT
                    else -> PriceScaleId(value)
                }
                else -> LEFT
            }
        }
    }
}
