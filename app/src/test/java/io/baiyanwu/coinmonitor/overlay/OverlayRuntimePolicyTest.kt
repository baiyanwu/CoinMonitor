package io.baiyanwu.coinmonitor.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRuntimePolicyTest {
    @Test
    fun `persist enabled should require overlay permission`() {
        assertFalse(
            OverlayRuntimePolicy.shouldPersistEnabled(
                requestedEnabled = true,
                canDrawOverlays = false
            )
        )
        assertTrue(
            OverlayRuntimePolicy.shouldPersistEnabled(
                requestedEnabled = true,
                canDrawOverlays = true
            )
        )
    }

    @Test
    fun `run overlay should require enabled state and overlay permission`() {
        assertFalse(
            OverlayRuntimePolicy.shouldRunOverlay(
                settingsEnabled = false,
                canDrawOverlays = true
            )
        )
        assertFalse(
            OverlayRuntimePolicy.shouldRunOverlay(
                settingsEnabled = true,
                canDrawOverlays = false
            )
        )
        assertTrue(
            OverlayRuntimePolicy.shouldRunOverlay(
                settingsEnabled = true,
                canDrawOverlays = true
            )
        )
    }
}
