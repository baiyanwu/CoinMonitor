package com.tradingview.lightweightcharts.api.series.enums

import com.google.gson.*
import com.tradingview.lightweightcharts.help.isNumber
import com.tradingview.lightweightcharts.help.requireInt
import java.lang.reflect.Type

/**
 * This enum is used to specify the type of the last price animation for series such as area or line
 */
enum class LastPriceAnimationMode(val value: Int) {
    /**
     * Animation is always disabled
     */
    DISABLED(0),

    /**
     * Animation is always enabled
     */
    CONTINUOUS(1),

    /**
     * Animation is active some time after data update
     */
    ON_DATA_UPDATE(2);

    companion object {
        fun from(value: Int) = values().first { it.value == value }
    }

    class LastPriceAnimationModeAdapter :
        JsonSerializer<LastPriceAnimationMode>,
        JsonDeserializer<LastPriceAnimationMode> {

        override fun serialize(
            src: LastPriceAnimationMode?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return src?.let { JsonPrimitive(it.value) } ?: JsonNull.INSTANCE
        }

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): LastPriceAnimationMode {
            return when {
                json.isNumber() -> from(json.requireInt())
                else -> DISABLED
            }
        }
    }
}
