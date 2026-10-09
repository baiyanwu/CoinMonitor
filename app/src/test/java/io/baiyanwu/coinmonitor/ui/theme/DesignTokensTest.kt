package io.baiyanwu.coinmonitor.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class DesignTokensTest {
    @Test
    fun `small interactive labels remain readable in both appearance modes`() {
        listOf(designTokens(false).colors, designTokens(true).colors).forEach { colors ->
            assertTrue("selected label", contrast(colors.selectedContent, colors.selectedContainer) >= 4.5)
            assertTrue("primary label", contrast(colors.primaryText, colors.surface) >= 4.5)
            assertTrue("unselected tab label", contrast(colors.secondaryText, colors.surface) >= 4.5)
            assertTrue("unselected option label", contrast(colors.secondaryText, colors.surfaceMuted) >= 4.5)
            assertTrue("accent label", contrast(colors.accent, colors.surface) >= 4.5)
            assertTrue("filled action", contrast(colors.onAccent, colors.accent) >= 4.5)
            assertTrue("error label", contrast(colors.error, colors.surface) >= 4.5)
        }
    }

    @Test
    fun `local semantic bridge preserves independent financial and selection colors`() {
        listOf(designTokens(false).colors, designTokens(true).colors).forEach { design ->
            val legacy = design.toSemanticColors()
            assertEquals(design.positive, legacy.positive)
            assertEquals(design.negative, legacy.negative)
            assertEquals(design.selectedContent, legacy.chipSelectedContent)
            assertEquals(design.selectedContainer, legacy.chipSelectedContainer)
            assertEquals(design.primaryText, legacy.primaryText)
            assertEquals(design.secondaryText, legacy.secondaryText)
            assertTrue(design.positive != design.accent)
            assertTrue(design.negative != design.error)
        }
    }

    private fun contrast(first: Color, second: Color): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (maxOf(firstLuminance, secondLuminance) + 0.05) /
            (minOf(firstLuminance, secondLuminance) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double = if (channel <= 0.04045f) {
            channel.toDouble() / 12.92
        } else {
            ((channel.toDouble() + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
}
