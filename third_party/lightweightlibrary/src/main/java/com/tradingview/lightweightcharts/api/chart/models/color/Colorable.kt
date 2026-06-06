package com.tradingview.lightweightcharts.api.chart.models.color

import android.graphics.Color
import com.google.gson.*
import com.tradingview.lightweightcharts.api.series.exception.ColorParseException
import com.tradingview.lightweightcharts.help.isString
import com.tradingview.lightweightcharts.help.requireString
import java.lang.IllegalStateException
import java.lang.reflect.Type

interface Colorable {

    class ColorAdapter : JsonSerializer<Colorable>, JsonDeserializer<Colorable> {
        override fun serialize(
            src: Colorable?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return when (src) {
                is IntColor -> JsonPrimitive(src.value.toRgbaString())
                is NoColor -> JsonPrimitive("")
                else -> throw IllegalStateException("Unknown type of color: $typeOfSrc")
            }
        }

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): Colorable? {
            return when {
                json.isString() && json.requireString().isBlank() -> NoColor
                json.isString() -> json.requireString().toColor()?.let { IntColor(it) }
                else -> null
            }
        }
    }
}

internal fun Int.toHexString(): String {
    val color = toRgba()
    return "#${color.hexRed}${color.hexGreen}${color.hexBlue}${color.hexAlpha}"
}

internal fun Int.toRgbaString(): String {
    val color = toRgba()
    return "rgba(${color.red},${color.green},${color.blue},${color.alpha.toFloat() / 255f})"
}

internal fun String.toColor(): Int? {
    return when {
        isBlank() -> null
        get(0) == '#' -> parseHexColor()
        count() > 6 && subSequence(0, 4) == "rgba" -> parseRgbaColor()
        else -> throw ColorParseException("Unknown color")
    }
}

private fun String.parseHexColor(): Int {
    val argbAlphaMask = 0x00000000ff000000L
    val rgbaAlphaMask = 0x00000000000000ffL

    // Use a long to avoid rollovers on #ffXXXXXX
    val color: Long = substring(1).toLong(16)

    val argbColor = when (length) {
        7 -> color or argbAlphaMask
        9 -> {
            val sRGBColor = color shr 8 // remove alpha
            val alpha = (color and rgbaAlphaMask) shl 24
            sRGBColor or alpha
        }
        else -> throw ColorParseException("Unknown color")
    }
    return argbColor.toInt()
}

private fun String.parseRgbaColor(): Int {
    val regex = "rgba[(]\\s*(\\d+)\\s*[,]\\s*(\\d+)\\s*[,]\\s*(\\d+)\\s*[,]\\s*([\\d.]+)\\s*[)]".toRegex()
    return regex.matchEntire(this)?.groupValues?.let { groups ->
        val red = groups[1].toInt()
        val green = groups[2].toInt()
        val blue = groups[3].toInt()
        val alpha = (groups[4].toFloat() * 255).toInt()
        return@let Color.argb(alpha, red, green, blue)
    } ?: throw ColorParseException("Unknown color")
}

internal data class ArgbColor(
    val alpha: Int,
    val red: Int,
    val green: Int,
    val blue: Int
) {
    val hexAlpha: String
        get() = hexFormat(alpha)

    val hexRed: String
        get() = hexFormat(red)

    val hexGreen: String
        get() = hexFormat(green)

    val hexBlue: String
        get() = hexFormat(blue)

    private fun hexFormat(param: Int): String {
        return String.format("%02x", param)
    }
}

private fun Int.toRgba(): ArgbColor {
    return ArgbColor(
        alpha = Color.alpha(this),
        red = Color.red(this),
        green = Color.green(this),
        blue = Color.blue(this)
    )
}
