package io.baiyanwu.coinmonitor.data.repository

import androidx.datastore.preferences.core.mutablePreferencesOf
import io.baiyanwu.coinmonitor.domain.model.OverlayEdgeDisplayMode
import io.baiyanwu.coinmonitor.domain.model.OverlayLeadingDisplayMode
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayPreferencesMapperTest {
    @Test
    fun `empty preferences use overlay defaults`() {
        val settings = mutablePreferencesOf().toOverlaySettings()

        assertEquals(OverlaySettings(), settings)
    }

    @Test
    fun `all overlay settings are restored from preferences`() {
        val settings = mutablePreferencesOf(
            OverlayPreferenceKeys.enabled to true,
            OverlayPreferenceKeys.locked to true,
            OverlayPreferenceKeys.opacity to 0.5f,
            OverlayPreferenceKeys.maxItems to 8,
            OverlayPreferenceKeys.leadingDisplayMode to OverlayLeadingDisplayMode.PAIR_NAME.name,
            OverlayPreferenceKeys.fontScale to 1.2f,
            OverlayPreferenceKeys.snapToEdge to true,
            OverlayPreferenceKeys.edgeDisplayMode to OverlayEdgeDisplayMode.HIDDEN_TAB.name,
            OverlayPreferenceKeys.edgeTabOpacity to 0.65f,
            OverlayPreferenceKeys.edgeAutoCollapseSeconds to 4,
            OverlayPreferenceKeys.windowX to 120,
            OverlayPreferenceKeys.windowY to 360
        ).toOverlaySettings()

        assertEquals(true, settings.enabled)
        assertEquals(true, settings.locked)
        assertEquals(0.5f, settings.opacity)
        assertEquals(8, settings.maxItems)
        assertEquals(OverlayLeadingDisplayMode.PAIR_NAME, settings.leadingDisplayMode)
        assertEquals(1.2f, settings.fontScale)
        assertEquals(true, settings.snapToEdge)
        assertEquals(OverlayEdgeDisplayMode.HIDDEN_TAB, settings.edgeDisplayMode)
        assertEquals(0.65f, settings.edgeTabOpacity)
        assertEquals(4, settings.edgeAutoCollapseSeconds)
        assertEquals(120, settings.windowX)
        assertEquals(360, settings.windowY)
    }

    @Test
    fun `invalid preference values fall back or clamp safely`() {
        val settings = mutablePreferencesOf(
            OverlayPreferenceKeys.enabled to false,
            OverlayPreferenceKeys.opacity to -1f,
            OverlayPreferenceKeys.maxItems to 99,
            OverlayPreferenceKeys.leadingDisplayMode to "UNKNOWN",
            OverlayPreferenceKeys.fontScale to 10f,
            OverlayPreferenceKeys.edgeDisplayMode to "UNKNOWN",
            OverlayPreferenceKeys.edgeTabOpacity to 0f,
            OverlayPreferenceKeys.edgeAutoCollapseSeconds to 99
        ).toOverlaySettings()

        assertFalse(settings.enabled)
        assertEquals(OverlaySettings.MIN_OPACITY, settings.opacity)
        assertEquals(OverlaySettings.MAX_SELECTABLE_ITEMS, settings.maxItems)
        assertEquals(OverlayLeadingDisplayMode.ICON, settings.leadingDisplayMode)
        assertEquals(OverlaySettings.MAX_FONT_SCALE, settings.fontScale)
        assertEquals(OverlayEdgeDisplayMode.TICKER, settings.edgeDisplayMode)
        assertEquals(OverlaySettings.MIN_EDGE_TAB_OPACITY, settings.edgeTabOpacity)
        assertEquals(
            OverlaySettings.MAX_EDGE_AUTO_COLLAPSE_SECONDS,
            settings.edgeAutoCollapseSeconds
        )
        assertNull(settings.windowX)
        assertNull(settings.windowY)
    }
}
