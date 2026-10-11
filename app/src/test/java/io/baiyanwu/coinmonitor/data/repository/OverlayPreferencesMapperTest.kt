package io.baiyanwu.coinmonitor.data.repository

import androidx.datastore.preferences.core.mutablePreferencesOf
import io.baiyanwu.coinmonitor.domain.model.ArrangedOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.MarqueeOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.MarqueeSpeed
import io.baiyanwu.coinmonitor.domain.model.OverlayDisplayType
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
            OverlayPreferenceKeys.displayType to OverlayDisplayType.MARQUEE.name,
            OverlayPreferenceKeys.opacity to 0.5f,
            OverlayPreferenceKeys.maxItems to 8,
            OverlayPreferenceKeys.leadingDisplayMode to OverlayLeadingDisplayMode.PAIR_NAME.name,
            OverlayPreferenceKeys.fontScale to 1.2f,
            OverlayPreferenceKeys.snapToEdge to true,
            OverlayPreferenceKeys.singleItemScroll to false,
            OverlayPreferenceKeys.edgeTabOpacity to 0.65f,
            OverlayPreferenceKeys.edgeHideDelaySeconds to 5,
            OverlayPreferenceKeys.windowX to 120,
            OverlayPreferenceKeys.windowY to 360,
            OverlayPreferenceKeys.marqueeOpacity to 0.6f,
            OverlayPreferenceKeys.marqueeMaxItems to 9,
            OverlayPreferenceKeys.marqueeFontScale to 1.1f,
            OverlayPreferenceKeys.marqueeSpeed to MarqueeSpeed.FAST.name,
            OverlayPreferenceKeys.marqueeWindowY to 240
        ).toOverlaySettings()

        assertEquals(true, settings.enabled)
        assertEquals(true, settings.locked)
        assertEquals(OverlayDisplayType.MARQUEE, settings.displayType)
        assertEquals(0.5f, settings.arranged.opacity)
        assertEquals(8, settings.arranged.maxItems)
        assertEquals(OverlayLeadingDisplayMode.PAIR_NAME, settings.arranged.leadingDisplayMode)
        assertEquals(1.2f, settings.arranged.fontScale)
        assertEquals(true, settings.arranged.snapToEdge)
        assertFalse(settings.arranged.singleItemScroll)
        assertEquals(0.65f, settings.arranged.edgeTabOpacity)
        assertEquals(5, settings.arranged.edgeAutoCollapseSeconds)
        assertEquals(120, settings.arranged.windowX)
        assertEquals(360, settings.arranged.windowY)
        assertEquals(0.6f, settings.marquee.opacity)
        assertEquals(9, settings.marquee.maxItems)
        assertEquals(1.1f, settings.marquee.fontScale)
        assertEquals(MarqueeSpeed.FAST, settings.marquee.speed)
        assertEquals(240, settings.marquee.windowY)
    }

    @Test
    fun `invalid preference values fall back or clamp safely`() {
        val settings = mutablePreferencesOf(
            OverlayPreferenceKeys.enabled to false,
            OverlayPreferenceKeys.displayType to "UNKNOWN",
            OverlayPreferenceKeys.opacity to -1f,
            OverlayPreferenceKeys.maxItems to 99,
            OverlayPreferenceKeys.leadingDisplayMode to "UNKNOWN",
            OverlayPreferenceKeys.fontScale to 10f,
            OverlayPreferenceKeys.edgeDisplayMode to "UNKNOWN",
            OverlayPreferenceKeys.edgeTabOpacity to 0f,
            OverlayPreferenceKeys.edgeHideDelaySeconds to 99,
            OverlayPreferenceKeys.marqueeOpacity to 2f,
            OverlayPreferenceKeys.marqueeMaxItems to 0,
            OverlayPreferenceKeys.marqueeFontScale to -1f,
            OverlayPreferenceKeys.marqueeSpeed to "UNKNOWN"
        ).toOverlaySettings()

        assertFalse(settings.enabled)
        assertEquals(OverlayDisplayType.ARRANGED, settings.displayType)
        assertEquals(ArrangedOverlaySettings.MIN_OPACITY, settings.arranged.opacity)
        assertEquals(OverlaySettings.MAX_SELECTABLE_ITEMS, settings.arranged.maxItems)
        assertEquals(OverlayLeadingDisplayMode.ICON, settings.arranged.leadingDisplayMode)
        assertEquals(ArrangedOverlaySettings.MAX_FONT_SCALE, settings.arranged.fontScale)
        assertFalse(settings.arranged.singleItemScroll)
        assertEquals(ArrangedOverlaySettings.MIN_EDGE_TAB_OPACITY, settings.arranged.edgeTabOpacity)
        assertEquals(
            ArrangedOverlaySettings.MAX_EDGE_AUTO_COLLAPSE_SECONDS,
            settings.arranged.edgeAutoCollapseSeconds
        )
        assertNull(settings.arranged.windowX)
        assertNull(settings.arranged.windowY)
        assertEquals(MarqueeOverlaySettings.MAX_OPACITY, settings.marquee.opacity)
        assertEquals(1, settings.marquee.maxItems)
        assertEquals(MarqueeOverlaySettings.MIN_FONT_SCALE, settings.marquee.fontScale)
        assertEquals(MarqueeSpeed.NORMAL, settings.marquee.speed)
        assertNull(settings.marquee.windowY)
    }

    @Test
    fun `legacy edge ticker keeps carousel without hiding and seeds marquee y`() {
        val settings = mutablePreferencesOf(
            OverlayPreferenceKeys.edgeDisplayMode to "TICKER",
            OverlayPreferenceKeys.snapToEdge to true,
            OverlayPreferenceKeys.windowY to 420
        ).toOverlaySettings()

        assertEquals(true, settings.arranged.singleItemScroll)
        assertEquals(0, settings.arranged.edgeAutoCollapseSeconds)
        assertEquals(420, settings.arranged.windowY)
        assertEquals(420, settings.marquee.windowY)
    }

    @Test
    fun `legacy modes migrate their effective layout and hidden delay`() {
        for (snap in listOf(false, true)) {
            for (mode in listOf("DOCKED", "TICKER", "HIDDEN_TAB", "UNKNOWN")) {
                val settings = mutablePreferencesOf(
                    OverlayPreferenceKeys.snapToEdge to snap,
                    OverlayPreferenceKeys.edgeDisplayMode to mode,
                    OverlayPreferenceKeys.edgeAutoCollapseSeconds to 4
                ).toOverlaySettings()
                assertEquals(snap, settings.arranged.snapToEdge)
                assertEquals(snap, settings.arranged.singleItemScroll)
                assertEquals(
                    if (mode == "HIDDEN_TAB") 4 else 0,
                    settings.arranged.edgeAutoCollapseSeconds
                )
            }
        }
        val legacyHidden = mutablePreferencesOf(
            OverlayPreferenceKeys.edgeDisplayMode to "HIDDEN_TAB"
        ).toOverlaySettings()
        assertEquals(3, legacyHidden.arranged.edgeAutoCollapseSeconds)
    }

    @Test
    fun `explicit new choices override legacy hidden carousel values`() {
        val settings = mutablePreferencesOf(
            OverlayPreferenceKeys.snapToEdge to true,
            OverlayPreferenceKeys.edgeDisplayMode to "HIDDEN_TAB",
            OverlayPreferenceKeys.singleItemScroll to false,
            OverlayPreferenceKeys.edgeHideDelaySeconds to 0,
            OverlayPreferenceKeys.edgeAutoCollapseSeconds to 3
        ).toOverlaySettings()
        assertFalse(settings.arranged.singleItemScroll)
        assertEquals(0, settings.arranged.edgeAutoCollapseSeconds)
    }

    @Test
    fun `saving independent choices survives reload and later snap changes`() {
        for (snap in listOf(false, true)) {
            for (scroll in listOf(false, true)) {
                for (seconds in 0..10) {
                    val preferences = mutablePreferencesOf(
                        OverlayPreferenceKeys.edgeDisplayMode to "HIDDEN_TAB"
                    )
                    val settings = OverlaySettings(
                        arranged = ArrangedOverlaySettings(
                            snapToEdge = snap,
                            singleItemScroll = scroll,
                            edgeAutoCollapseSeconds = seconds
                        )
                    )
                    preferences.writeSettings(settings)
                    assertEquals(settings, preferences.toOverlaySettings())
                    assertNull(preferences[OverlayPreferenceKeys.edgeDisplayMode])
                    assertNull(preferences[OverlayPreferenceKeys.edgeAutoCollapseSeconds])

                    preferences[OverlayPreferenceKeys.snapToEdge] = !snap
                    val changed = preferences.toOverlaySettings().arranged
                    assertEquals(!snap, changed.snapToEdge)
                    assertEquals(scroll, changed.singleItemScroll)
                    assertEquals(seconds, changed.edgeAutoCollapseSeconds)
                }
            }
        }
    }

    @Test
    fun `legacy minimum opacity becomes true zero for both overlay types`() {
        val settings = mutablePreferencesOf(
            OverlayPreferenceKeys.opacity to 0.16f,
            OverlayPreferenceKeys.marqueeOpacity to 0.16f
        ).toOverlaySettings()

        assertEquals(0f, settings.arranged.opacity)
        assertEquals(0f, settings.marquee.opacity)
    }

    @Test
    fun `current opacity range preserves values between zero and previous minimum`() {
        val settings = mutablePreferencesOf(
            OverlayPreferenceKeys.opacityRangeVersion to 1,
            OverlayPreferenceKeys.opacity to 0.16f,
            OverlayPreferenceKeys.marqueeOpacity to 0.08f
        ).toOverlaySettings()

        assertEquals(0.16f, settings.arranged.opacity)
        assertEquals(0.08f, settings.marquee.opacity)
    }
}
