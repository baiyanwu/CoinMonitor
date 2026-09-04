package io.baiyanwu.coinmonitor.domain.model

enum class MarqueeSpeed(val dpPerSecond: Int) {
    SLOW(24),
    NORMAL(40),
    FAST(56)
}

data class MarqueeOverlaySettings(
    val opacity: Float = DEFAULT_OPACITY,
    val maxItems: Int = DEFAULT_MAX_ITEMS,
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val speed: MarqueeSpeed = MarqueeSpeed.NORMAL,
    val windowY: Int? = null
) {
    companion object {
        const val DEFAULT_OPACITY: Float = 0.42f
        const val MIN_OPACITY: Float = 0f
        const val MAX_OPACITY: Float = 0.72f
        const val DEFAULT_MAX_ITEMS: Int = OverlaySettings.MAX_SELECTABLE_ITEMS
        const val DEFAULT_FONT_SCALE: Float = 1f
        const val MIN_FONT_SCALE: Float = 0.85f
        const val MAX_FONT_SCALE: Float = 1.35f
    }
}
