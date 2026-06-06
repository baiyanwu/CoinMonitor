package com.tradingview.lightweightcharts.api.options.enums

import com.google.gson.*
import com.tradingview.lightweightcharts.help.isNumber
import com.tradingview.lightweightcharts.help.requireInt
import java.lang.reflect.Type

/**
 * Determine how to exit the tracking mode.
 *
 * By default, mobile users will long press to deactivate the scroll and have the ability to check values and dates.
 * Another press is required to activate the scroll, be able to move left/right, zoom, etc.
 */
enum class TrackingModeExitMode(val value: Int) {
    /**
     * Tracking Mode will be deactivated on touch end event.
     */
    ON_TOUCH_END(value = 0),

    /**
     * Tracking Mode will be deactivated on the next tap event.
     */
    ON_NEXT_TAP(value = 1);

    companion object {
        fun fromValue(value: Int): TrackingModeExitMode = values().first { it.value == value }
    }

    class TrackingModeExitModeAdapter :
        JsonSerializer<TrackingModeExitMode>,
        JsonDeserializer<TrackingModeExitMode> {

        override fun serialize(
            src: TrackingModeExitMode?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return src?.let { JsonPrimitive(it.value) } ?: JsonNull.INSTANCE
        }

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): TrackingModeExitMode {
            return when {
                json.isNumber() -> fromValue(json.requireInt())
                else -> ON_NEXT_TAP
            }
        }
    }
}
