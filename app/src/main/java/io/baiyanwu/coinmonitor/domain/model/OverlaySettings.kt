package io.baiyanwu.coinmonitor.domain.model

enum class OverlayLeadingDisplayMode {
    ICON,
    PAIR_NAME
}

data class OverlaySettings(
    val enabled: Boolean = false,
    val locked: Boolean = false,
    val opacity: Float = 0.42f,
    val maxItems: Int = 5,
    val leadingDisplayMode: OverlayLeadingDisplayMode = OverlayLeadingDisplayMode.ICON,
    val fontScale: Float = 1f,
    val snapToEdge: Boolean = false,
    val windowX: Int? = null,
    val windowY: Int? = null
) {
    companion object {
        const val MAX_SELECTABLE_ITEMS: Int = 10
        const val MIN_OPACITY: Float = 0.16f
        const val MAX_OPACITY: Float = 0.72f
        const val MIN_FONT_SCALE: Float = 0.85f
        const val MAX_FONT_SCALE: Float = 1.35f
    }
}
