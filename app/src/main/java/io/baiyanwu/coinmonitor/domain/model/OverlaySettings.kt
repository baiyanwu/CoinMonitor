package io.baiyanwu.coinmonitor.domain.model

enum class OverlayDisplayType {
    ARRANGED,
    MARQUEE
}

data class OverlaySettings(
    val enabled: Boolean = false,
    val locked: Boolean = false,
    val displayType: OverlayDisplayType = OverlayDisplayType.ARRANGED,
    val arranged: ArrangedOverlaySettings = ArrangedOverlaySettings(),
    val marquee: MarqueeOverlaySettings = MarqueeOverlaySettings()
) {
    companion object {
        const val MAX_SELECTABLE_ITEMS: Int = 10
    }
}
