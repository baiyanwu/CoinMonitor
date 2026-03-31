package com.tradingview.lightweightcharts.api.chart.models.color

import com.google.gson.*
import com.tradingview.lightweightcharts.api.series.exception.ColorParseException
import com.tradingview.lightweightcharts.help.isString
import com.tradingview.lightweightcharts.help.requireString
import java.lang.reflect.Type

class IntColor(val value: Int) : Colorable

class IntColorAdapter : JsonSerializer<IntColor>, JsonDeserializer<IntColor> {
    override fun serialize(
        src: IntColor?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        return when (src) {
            is IntColor -> JsonPrimitive(src.value.toRgbaString())
            else -> JsonNull.INSTANCE
        }
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): IntColor? {
        return when {
            json.isString() -> json.requireString().toColor()?.let { IntColor(it) }
            else -> null
        }
    }
}

fun Int.toIntColor(): IntColor {
    return IntColor(this)
}

fun String.toIntColor(): IntColor {
    val color = this.toColor()
        ?: throw ColorParseException("Color is empty")
    return IntColor(color)
}
