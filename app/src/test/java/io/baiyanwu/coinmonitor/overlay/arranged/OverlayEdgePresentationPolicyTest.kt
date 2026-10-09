package io.baiyanwu.coinmonitor.overlay.arranged

import io.baiyanwu.coinmonitor.domain.model.ArrangedOverlaySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayEdgePresentationPolicyTest {
    @Test
    fun `snapping and scrolling remain independent with every hide delay`() {
        for (snap in listOf(false, true)) {
            for (scroll in listOf(false, true)) {
                for (seconds in 0..10) {
                    for (expanded in listOf(false, true)) {
                        val settings = ArrangedOverlaySettings(
                            snapToEdge = snap,
                            singleItemScroll = scroll,
                            edgeAutoCollapseSeconds = seconds
                        )
                        val expected = when {
                            snap && seconds > 0 && !expanded -> OverlayEdgePresentation.HIDDEN_TAB
                            scroll -> OverlayEdgePresentation.SINGLE_ITEM_CAROUSEL
                            else -> OverlayEdgePresentation.STANDARD
                        }
                        assertEquals(
                            "snap=$snap scroll=$scroll delay=$seconds expanded=$expanded",
                            expected,
                            OverlayEdgePresentationPolicy.resolve(settings, true, expanded)
                        )
                        assertEquals(
                            snap && seconds > 0,
                            OverlayEdgePresentationPolicy.usesHiddenTab(settings)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `empty overlay stays visible instead of collapsing to a tab`() {
        val settings = ArrangedOverlaySettings(
            snapToEdge = true,
            singleItemScroll = true,
            edgeAutoCollapseSeconds = 3
        )
        assertEquals(
            OverlayEdgePresentation.STANDARD,
            OverlayEdgePresentationPolicy.resolve(settings, false, false)
        )
    }

    @Test
    fun `edge positioning snaps to the nearest side or preserves free placement`() {
        assertEquals(0, OverlayEdgePositionPolicy.resolveX(200, 120, 1000, true))
        assertEquals(880, OverlayEdgePositionPolicy.resolveX(700, 120, 1000, true))
        assertEquals(200, OverlayEdgePositionPolicy.resolveX(200, 120, 1000, false))
        assertEquals(700, OverlayEdgePositionPolicy.resolveX(700, 120, 1000, false))
        assertEquals(0, OverlayEdgePositionPolicy.resolveX(-50, 120, 1000, false))
        assertEquals(880, OverlayEdgePositionPolicy.resolveX(950, 120, 1000, false))
        assertEquals(0, OverlayEdgePositionPolicy.resolveX(200, 1200, 1000, true))
    }

    @Test
    fun `edge motion mirrors toward the nearest screen edge`() {
        assertEquals(-120f, OverlayEdgeMotionPolicy.resolveTranslation(120, true), 0f)
        assertEquals(120f, OverlayEdgeMotionPolicy.resolveTranslation(120, false), 0f)
        assertEquals(0f, OverlayEdgeMotionPolicy.resolveTranslation(0, false), 0f)
    }

    @Test
    fun `hide delay preserves every whole second from zero to ten`() {
        for (seconds in 0..10) {
            assertEquals(seconds * 1_000L, OverlayEdgeAutoCollapsePolicy.resolveDelayMillis(seconds))
        }
        assertEquals(0L, OverlayEdgeAutoCollapsePolicy.resolveDelayMillis(-1))
        assertEquals(10_000L, OverlayEdgeAutoCollapsePolicy.resolveDelayMillis(11))
    }
}
