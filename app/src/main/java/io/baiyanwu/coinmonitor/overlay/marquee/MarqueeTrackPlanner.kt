package io.baiyanwu.coinmonitor.overlay.marquee

import io.baiyanwu.coinmonitor.domain.model.MarqueeSpeed
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong

data class MarqueeTrackPlan(
    val cycleWidthPx: Int,
    val repetitionCount: Int,
    val durationMillis: Long
)

object MarqueeTrackPlanner {
    fun plan(
        itemWidthsPx: List<Int>,
        viewportWidthPx: Int,
        density: Float,
        speed: MarqueeSpeed,
        cycleSeparatorWidthPx: Int = 0
    ): MarqueeTrackPlan? {
        if (
            itemWidthsPx.isEmpty() ||
            itemWidthsPx.any { it <= 0 } ||
            viewportWidthPx <= 0 ||
            density <= 0f ||
            cycleSeparatorWidthPx < 0
        ) {
            return null
        }
        val cycleWidthPx = itemWidthsPx.sum() + cycleSeparatorWidthPx
        val repetitions = max(
            2,
            ceil(viewportWidthPx.toDouble() / cycleWidthPx.toDouble()).toInt() + 1
        )
        val pixelsPerSecond = speed.dpPerSecond * density
        val durationMillis = (cycleWidthPx / pixelsPerSecond * 1_000f)
            .roundToLong()
            .coerceAtLeast(1L)
        return MarqueeTrackPlan(
            cycleWidthPx = cycleWidthPx,
            repetitionCount = repetitions,
            durationMillis = durationMillis
        )
    }
}

object MarqueeTrackStructure {
    fun signature(
        itemIds: List<String>,
        fontScale: Float,
        itemWidthPx: Int,
        cycleSeparatorWidthPx: Int,
        viewportWidthPx: Int,
        speed: MarqueeSpeed
    ): String = buildString {
        append(itemIds.joinToString(separator = ","))
        append('|').append(fontScale)
        append('|').append(itemWidthPx)
        append('|').append(cycleSeparatorWidthPx)
        append('|').append(viewportWidthPx)
        append('|').append(speed.name)
    }
}

object MarqueeSpacingPolicy {
    fun iconSlotWidthPx(iconSizePx: Int, itemSpacingPx: Int): Int {
        return iconSizePx.coerceAtLeast(0) + itemSpacingPx.coerceAtLeast(0) * 2
    }

    fun cycleSeparatorWidthPx(itemSpacingPx: Int): Int {
        return itemSpacingPx.coerceAtLeast(0) * 2
    }
}

object MarqueeWindowPositionPolicy {
    fun resolveX(): Int = 0

    fun resolveY(requestedY: Int, overlayHeightPx: Int, screenHeightPx: Int): Int {
        val maxY = max(0, screenHeightPx - overlayHeightPx)
        return requestedY.coerceIn(0, maxY)
    }
}
