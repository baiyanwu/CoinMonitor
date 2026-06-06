package com.tradingview.lightweightcharts.api.series.enums

import com.google.gson.*
import com.tradingview.lightweightcharts.help.isNumber
import com.tradingview.lightweightcharts.help.requireInt
import java.lang.reflect.Type

enum class CrosshairMode(val value: Int) {
    NORMAL(0),
    MAGNET(1);

    companion object {
        fun fromValue(value: Int): CrosshairMode = values().first { it.value == value }
    }

    class CrosshairModeAdapter : JsonSerializer<CrosshairMode>, JsonDeserializer<CrosshairMode> {
        override fun serialize(
            src: CrosshairMode?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return src?.let { JsonPrimitive(it.value) } ?: JsonNull.INSTANCE
        }

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): CrosshairMode {
            return when {
                json.isNumber() -> fromValue(json.requireInt())
                else -> NORMAL
            }
        }
    }
}
