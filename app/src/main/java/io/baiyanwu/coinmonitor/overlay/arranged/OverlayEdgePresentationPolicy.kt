package io.baiyanwu.coinmonitor.overlay.arranged

import io.baiyanwu.coinmonitor.domain.model.ArrangedEdgeDisplayMode
import io.baiyanwu.coinmonitor.domain.model.ArrangedOverlaySettings

enum class OverlayEdgePresentation {
    STANDARD,
    DOCKED_CAROUSEL,
    HIDDEN_TAB
}

object OverlayEdgePresentationPolicy {
    fun resolve(
        settings: ArrangedOverlaySettings,
        hasItems: Boolean,
        hiddenTabExpanded: Boolean
    ): OverlayEdgePresentation {
        if (!settings.snapToEdge || !hasItems) {
            return OverlayEdgePresentation.STANDARD
        }
        return when (settings.edgeDisplayMode) {
            ArrangedEdgeDisplayMode.DOCKED -> OverlayEdgePresentation.DOCKED_CAROUSEL
            ArrangedEdgeDisplayMode.HIDDEN_TAB -> {
                if (hiddenTabExpanded) {
                    OverlayEdgePresentation.DOCKED_CAROUSEL
                } else {
                    OverlayEdgePresentation.HIDDEN_TAB
                }
            }
        }
    }
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

object OverlayEdgeAutoCollapsePolicy {
    fun normalizeSeconds(seconds: Int): Int {
        return seconds.coerceIn(
            minimumValue = ArrangedOverlaySettings.MIN_EDGE_AUTO_COLLAPSE_SECONDS,
            maximumValue = ArrangedOverlaySettings.MAX_EDGE_AUTO_COLLAPSE_SECONDS
        )
    }

    fun resolveDelayMillis(seconds: Int): Long {
        return normalizeSeconds(seconds) * 1_000L
    }
}
