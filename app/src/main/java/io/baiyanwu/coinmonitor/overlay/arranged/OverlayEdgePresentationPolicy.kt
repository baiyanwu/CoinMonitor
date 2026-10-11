package io.baiyanwu.coinmonitor.overlay.arranged

import io.baiyanwu.coinmonitor.domain.model.ArrangedOverlaySettings

enum class OverlayEdgePresentation {
    STANDARD,
    SINGLE_ITEM_CAROUSEL,
    HIDDEN_TAB
}

object OverlayEdgePresentationPolicy {
    fun resolve(
        settings: ArrangedOverlaySettings,
        hasItems: Boolean,
        hiddenTabExpanded: Boolean
    ): OverlayEdgePresentation {
        if (!hasItems) {
            return OverlayEdgePresentation.STANDARD
        }
        return when {
            usesHiddenTab(settings) && !hiddenTabExpanded -> OverlayEdgePresentation.HIDDEN_TAB
            settings.singleItemScroll -> OverlayEdgePresentation.SINGLE_ITEM_CAROUSEL
            else -> OverlayEdgePresentation.STANDARD
        }
    }

    fun usesHiddenTab(settings: ArrangedOverlaySettings): Boolean =
        settings.snapToEdge && settings.edgeAutoCollapseSeconds > 0
}

object OverlayEdgeMotionPolicy {
    fun resolveTranslation(
        overlayWidth: Int,
        nearestLeftEdge: Boolean
    ): Float {
        if (overlayWidth <= 0) return 0f
        return if (nearestLeftEdge) {
            -overlayWidth.toFloat()
        } else {
            overlayWidth.toFloat()
        }
    }
}

object OverlayEdgePositionPolicy {
    fun resolveX(
        currentX: Int,
        overlayWidth: Int,
        screenWidth: Int,
        snapToEdge: Boolean
    ): Int {
        val maxX = (screenWidth - overlayWidth).coerceAtLeast(0)
        if (!snapToEdge) return currentX.coerceIn(0, maxX)
        return if (currentX + overlayWidth / 2 <= screenWidth / 2) 0 else maxX
    }
}

object OverlayEdgeAutoCollapsePolicy {
    fun normalizeSeconds(seconds: Int): Int {
        return ArrangedOverlaySettings.normalizeEdgeAutoCollapseSeconds(seconds)
    }

    fun resolveDelayMillis(seconds: Int): Long {
        return normalizeSeconds(seconds) * 1_000L
    }
}
