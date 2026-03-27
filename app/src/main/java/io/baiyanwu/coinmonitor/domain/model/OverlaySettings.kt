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
    val leadingDisplayMode: OverlayLeadingDisplayMode = OverlayLeadingDisplayMode.PAIR_NAME,
    val windowX: Int? = null,
    val windowY: Int? = null
)
