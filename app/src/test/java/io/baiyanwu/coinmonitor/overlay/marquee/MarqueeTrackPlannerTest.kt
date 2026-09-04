package io.baiyanwu.coinmonitor.overlay.marquee

import io.baiyanwu.coinmonitor.domain.model.MarqueeSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarqueeTrackPlannerTest {
    @Test
    fun `empty input does not create a moving track`() {
        assertNull(
            MarqueeTrackPlanner.plan(
                itemWidthsPx = emptyList(),
                viewportWidthPx = 1_080,
                density = 1f,
                speed = MarqueeSpeed.NORMAL,
                cycleSeparatorWidthPx = 2
            )
        )
    }

    @Test
    fun `short cycles repeat enough times to cover viewport and one seam`() {
        val oneItem = requireNotNull(
            MarqueeTrackPlanner.plan(listOf(86), 1_080, 1f, MarqueeSpeed.NORMAL, 8)
        )
        val fiveItems = requireNotNull(
            MarqueeTrackPlanner.plan(List(5) { 86 }, 1_080, 1f, MarqueeSpeed.NORMAL, 8)
        )
        val tenItems = requireNotNull(
            MarqueeTrackPlanner.plan(List(10) { 86 }, 1_080, 1f, MarqueeSpeed.NORMAL, 8)
        )

        assertEquals(13, oneItem.repetitionCount)
        assertEquals(4, fiveItems.repetitionCount)
        assertEquals(3, tenItems.repetitionCount)
        assertEquals(94, oneItem.cycleWidthPx)
        assertEquals(438, fiveItems.cycleWidthPx)
        assertEquals(868, tenItems.cycleWidthPx)
        listOf(oneItem, fiveItems, tenItems).forEach { plan ->
            assertTrue(plan.cycleWidthPx * (plan.repetitionCount - 1) >= 1_080)
        }
    }

    @Test
    fun `variable item widths contribute exactly to the repeated cycle`() {
        val plan = requireNotNull(
            MarqueeTrackPlanner.plan(
                itemWidthsPx = listOf(42, 55, 63),
                viewportWidthPx = 1_080,
                density = 1f,
                speed = MarqueeSpeed.NORMAL,
                cycleSeparatorWidthPx = 2
            )
        )

        assertEquals(162, plan.cycleWidthPx)
        assertEquals(8, plan.repetitionCount)
    }

    @Test
    fun `speed presets produce deterministic cycle durations`() {
        val slow = requireNotNull(
            MarqueeTrackPlanner.plan(listOf(240), 1_080, 1f, MarqueeSpeed.SLOW)
        )
        val normal = requireNotNull(
            MarqueeTrackPlanner.plan(listOf(240), 1_080, 1f, MarqueeSpeed.NORMAL)
        )
        val fast = requireNotNull(
            MarqueeTrackPlanner.plan(listOf(240), 1_080, 1f, MarqueeSpeed.FAST)
        )

        assertEquals(10_000L, slow.durationMillis)
        assertEquals(6_000L, normal.durationMillis)
        assertEquals(4_286L, fast.durationMillis)
    }

    @Test
    fun `quote-only updates preserve structure while structural changes rebuild it`() {
        val original = MarqueeTrackStructure.signature(
            itemIds = listOf("btc", "eth"),
            fontScale = 1f,
            itemWidthPx = 86,
            cycleSeparatorWidthPx = 8,
            viewportWidthPx = 1_080,
            speed = MarqueeSpeed.NORMAL
        )
        val quoteOnlyUpdate = MarqueeTrackStructure.signature(
            itemIds = listOf("btc", "eth"),
            fontScale = 1f,
            itemWidthPx = 86,
            cycleSeparatorWidthPx = 8,
            viewportWidthPx = 1_080,
            speed = MarqueeSpeed.NORMAL
        )

        assertEquals(original, quoteOnlyUpdate)
        assertTrue(
            original != MarqueeTrackStructure.signature(
                itemIds = listOf("eth", "btc"),
                fontScale = 1f,
                itemWidthPx = 86,
                cycleSeparatorWidthPx = 8,
                viewportWidthPx = 1_080,
                speed = MarqueeSpeed.NORMAL
            )
        )
        assertTrue(
            original != MarqueeTrackStructure.signature(
                itemIds = listOf("btc", "eth"),
                fontScale = 1.1f,
                itemWidthPx = 94,
                cycleSeparatorWidthPx = 8,
                viewportWidthPx = 1_080,
                speed = MarqueeSpeed.NORMAL
            )
        )
        assertTrue(
            original != MarqueeTrackStructure.signature(
                itemIds = listOf("btc", "eth"),
                fontScale = 1f,
                itemWidthPx = 86,
                cycleSeparatorWidthPx = 8,
                viewportWidthPx = 1_920,
                speed = MarqueeSpeed.NORMAL
            )
        )
        assertTrue(
            original != MarqueeTrackStructure.signature(
                itemIds = listOf("btc", "eth"),
                fontScale = 1f,
                itemWidthPx = 86,
                cycleSeparatorWidthPx = 8,
                viewportWidthPx = 1_080,
                speed = MarqueeSpeed.FAST
            )
        )
    }

    @Test
    fun `cycle divider has one normal visible gap on each side`() {
        val itemSpacing = 3
        val iconSize = 48
        val iconSlotWidth = MarqueeSpacingPolicy.iconSlotWidthPx(
            iconSizePx = iconSize,
            itemSpacingPx = itemSpacing
        )
        val separatorWidth = MarqueeSpacingPolicy.cycleSeparatorWidthPx(itemSpacing)
        val iconInset = (iconSlotWidth - iconSize) / 2
        val normalVisibleGap = itemSpacing + iconInset
        val dividerToLastItem = itemSpacing + separatorWidth / 2
        val dividerToFirstItem = separatorWidth / 2 + iconInset

        assertEquals(itemSpacing * 2, separatorWidth)
        assertEquals(normalVisibleGap, dividerToLastItem)
        assertEquals(normalVisibleGap, dividerToFirstItem)
        assertEquals(normalVisibleGap * 2, itemSpacing + separatorWidth + iconInset)
        assertEquals(0, MarqueeSpacingPolicy.cycleSeparatorWidthPx(itemSpacingPx = -1))
    }

    @Test
    fun `window x is fixed and y remains within visible screen bounds`() {
        assertEquals(0, MarqueeWindowPositionPolicy.resolveX())
        assertEquals(0, MarqueeWindowPositionPolicy.resolveY(-20, 96, 1_000))
        assertEquals(500, MarqueeWindowPositionPolicy.resolveY(500, 96, 1_000))
        assertEquals(904, MarqueeWindowPositionPolicy.resolveY(980, 96, 1_000))
    }
}
