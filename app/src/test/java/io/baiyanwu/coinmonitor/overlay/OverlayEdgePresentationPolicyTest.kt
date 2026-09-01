package io.baiyanwu.coinmonitor.overlay

import io.baiyanwu.coinmonitor.domain.model.OverlayEdgeDisplayMode
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayEdgePresentationPolicyTest {
    @Test
    fun `existing snap mode keeps ticker presentation by default`() {
        val settings = OverlaySettings(snapToEdge = true)

        assertEquals(
            OverlayEdgePresentation.TICKER,
            OverlayEdgePresentationPolicy.resolve(
                settings = settings,
                hasItems = true,
                hiddenTabExpanded = false
            )
        )
    }

    @Test
    fun `hidden mode switches between edge tab and ticker`() {
        val settings = OverlaySettings(
            snapToEdge = true,
            edgeDisplayMode = OverlayEdgeDisplayMode.HIDDEN_TAB
        )

        assertEquals(
            OverlayEdgePresentation.HIDDEN_TAB,
            OverlayEdgePresentationPolicy.resolve(
                settings = settings,
                hasItems = true,
                hiddenTabExpanded = false
            )
        )
        assertEquals(
            OverlayEdgePresentation.TICKER,
            OverlayEdgePresentationPolicy.resolve(
                settings = settings,
                hasItems = true,
                hiddenTabExpanded = true
            )
        )
    }

    @Test
    fun `edge presentation is disabled without snap or items`() {
        val hiddenSettings = OverlaySettings(
            snapToEdge = true,
            edgeDisplayMode = OverlayEdgeDisplayMode.HIDDEN_TAB
        )

        assertEquals(
            OverlayEdgePresentation.STANDARD,
            OverlayEdgePresentationPolicy.resolve(
                settings = hiddenSettings.copy(snapToEdge = false),
                hasItems = true,
                hiddenTabExpanded = false
            )
        )
        assertEquals(
            OverlayEdgePresentation.STANDARD,
            OverlayEdgePresentationPolicy.resolve(
                settings = hiddenSettings,
                hasItems = false,
                hiddenTabExpanded = false
            )
        )
    }

    @Test
    fun `edge motion mirrors toward the nearest screen edge`() {
        assertEquals(
            -120f,
            OverlayEdgeMotionPolicy.resolveTranslation(
                overlayWidth = 120,
                nearestLeftEdge = true
            ),
            0f
        )
        assertEquals(
            120f,
            OverlayEdgeMotionPolicy.resolveTranslation(
                overlayWidth = 120,
                nearestLeftEdge = false
            ),
            0f
        )
        assertEquals(
            0f,
            OverlayEdgeMotionPolicy.resolveTranslation(
                overlayWidth = 0,
                nearestLeftEdge = false
            ),
            0f
        )
    }

    @Test
    fun `auto collapse delay is limited to one through five seconds`() {
        assertEquals(1_000L, OverlayEdgeAutoCollapsePolicy.resolveDelayMillis(0))
        assertEquals(3_000L, OverlayEdgeAutoCollapsePolicy.resolveDelayMillis(3))
        assertEquals(5_000L, OverlayEdgeAutoCollapsePolicy.resolveDelayMillis(6))
    }

}
