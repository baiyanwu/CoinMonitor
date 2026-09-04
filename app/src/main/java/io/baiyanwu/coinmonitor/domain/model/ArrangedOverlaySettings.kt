package io.baiyanwu.coinmonitor.domain.model

enum class OverlayLeadingDisplayMode {
    ICON,
    PAIR_NAME
}

enum class ArrangedEdgeDisplayMode {
    DOCKED,
    HIDDEN_TAB
}

data class ArrangedOverlaySettings(
    val opacity: Float = DEFAULT_OPACITY,
    val maxItems: Int = DEFAULT_MAX_ITEMS,
    val leadingDisplayMode: OverlayLeadingDisplayMode = OverlayLeadingDisplayMode.ICON,
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val snapToEdge: Boolean = false,
    val edgeDisplayMode: ArrangedEdgeDisplayMode = ArrangedEdgeDisplayMode.DOCKED,
    val edgeTabOpacity: Float = DEFAULT_EDGE_TAB_OPACITY,
    val edgeAutoCollapseSeconds: Int = DEFAULT_EDGE_AUTO_COLLAPSE_SECONDS,
    val windowX: Int? = null,
    val windowY: Int? = null
) {
    companion object {
        const val DEFAULT_OPACITY: Float = 0.42f
        const val MIN_OPACITY: Float = 0.16f
        const val MAX_OPACITY: Float = 0.72f
        const val DEFAULT_MAX_ITEMS: Int = 5
        const val DEFAULT_FONT_SCALE: Float = 1f
        const val MIN_FONT_SCALE: Float = 0.85f
        const val MAX_FONT_SCALE: Float = 1.35f
        const val DEFAULT_EDGE_TAB_OPACITY: Float = 0.45f
        const val MIN_EDGE_TAB_OPACITY: Float = 0.15f
        const val MAX_EDGE_TAB_OPACITY: Float = 1f
        const val DEFAULT_EDGE_AUTO_COLLAPSE_SECONDS: Int = 3
        const val MIN_EDGE_AUTO_COLLAPSE_SECONDS: Int = 1
        const val MAX_EDGE_AUTO_COLLAPSE_SECONDS: Int = 5
    }
}
