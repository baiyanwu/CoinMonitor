package io.baiyanwu.coinmonitor.overlay

import io.baiyanwu.coinmonitor.domain.model.OverlayEdgeDisplayMode
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings

enum class OverlayEdgePresentation {
    STANDARD,
    TICKER,
    HIDDEN_TAB
}

object OverlayEdgePresentationPolicy {
    fun resolve(
        settings: OverlaySettings,
        hasItems: Boolean,
        hiddenTabExpanded: Boolean
    ): OverlayEdgePresentation {
        if (!settings.snapToEdge || !hasItems) {
            return OverlayEdgePresentation.STANDARD
        }
        return when (settings.edgeDisplayMode) {
            OverlayEdgeDisplayMode.TICKER -> OverlayEdgePresentation.TICKER
            OverlayEdgeDisplayMode.HIDDEN_TAB -> {
                if (hiddenTabExpanded) {
                    OverlayEdgePresentation.TICKER
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
            minimumValue = OverlaySettings.MIN_EDGE_AUTO_COLLAPSE_SECONDS,
            maximumValue = OverlaySettings.MAX_EDGE_AUTO_COLLAPSE_SECONDS
        )
    }

    fun resolveDelayMillis(seconds: Int): Long {
        return normalizeSeconds(seconds) * 1_000L
    }
}
