package io.baiyanwu.coinmonitor.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorDesignTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.ui.trending.TrendingScreen
import io.baiyanwu.coinmonitor.ui.trending.TrendingUiState
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlinx.coroutines.launch

/** 用真实分页和渲染像素检查时序、原生反馈及裁剪，测试不调用网络或钱包服务。 */
class CoinMonitorInteractionTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var pager: PagerState
    private var accent = Color.Unspecified
    private var pageBackground = Color.Unspecified

    @Test fun indicatorFollowsUnfinishedDrag() = verifyDrag(LayoutDirection.Ltr)
    @Test fun indicatorFollowsUnfinishedDragInRtl() = verifyDrag(LayoutDirection.Rtl)

    private fun verifyDrag(direction: LayoutDirection) {
        setPagerContent(direction)
        val before = indicatorCenter()
        rule.onNodeWithTag("pager").performTouchInput {
            down(center)
            val sign = if (direction == LayoutDirection.Ltr) -1f else 1f
            moveTo(center + Offset(width * 0.3f * sign, 0f), delayMillis = 100)
        }
        rule.mainClock.advanceTimeByFrame()
        val position = rule.runOnIdle {
            assertTrue(pager.isScrollInProgress)
            assertEquals(0, pager.settledPage)
            pager.currentPage + pager.currentPageOffsetFraction
        }
        assertTrue("The drag must be part way through a page", position > 0.1f && position < 0.5f)
        assertIndicatorPosition(before, position, direction)
        rule.onNodeWithTag("pager").performTouchInput { up() }
        rule.mainClock.autoAdvance = true
    }

    @Test fun indicatorFollowsClickAnimationAndRapidRetargeting() {
        setPagerContent(LayoutDirection.Ltr)
        val origin = indicatorCenter()
        rule.onNodeWithTag("tabs").performTouchInput { click(Offset(width * 5f / 6f, centerY)) }
        rule.mainClock.advanceTimeBy(96)
        val firstPosition = rule.runOnIdle { pager.currentPage + pager.currentPageOffsetFraction }
        assertTrue(firstPosition > 0f && firstPosition < 2f)
        assertIndicatorPosition(origin, firstPosition, LayoutDirection.Ltr)
        rule.onNodeWithTag("tabs").performTouchInput { click(Offset(width / 6f, centerY)) }
        rule.mainClock.advanceTimeBy(96)
        val reversedPosition = rule.runOnIdle { pager.currentPage + pager.currentPageOffsetFraction }
        assertTrue(reversedPosition < firstPosition)
        assertIndicatorPosition(origin, reversedPosition, LayoutDirection.Ltr)
        rule.mainClock.advanceTimeBy(400)
        rule.runOnIdle { assertEquals(0, pager.settledPage) }
        assertEquals(origin, indicatorCenter(), 2f)
        rule.mainClock.autoAdvance = true
    }

    @Test fun compactTabsDoNotPaintAnotherCapsuleOnPressInLightTheme() = verifyCompactPress(false)
    @Test fun compactTabsDoNotPaintAnotherCapsuleOnPressInDarkTheme() = verifyCompactPress(true)

    private fun verifyCompactPress(dark: Boolean) {
        setPagerContent(LayoutDirection.Ltr, dark)
        val selectedBefore = rule.onNode(isSelected()).fetchSemanticsNode().boundsInRoot
        val before = rule.onNodeWithTag("tabs").captureToImage().toPixelMap()
        rule.onNodeWithTag("tabs").performTouchInput {
            down(Offset(width * 5f / 6f, centerY))
        }
        SystemClock.sleep(250)
        val pressed = rule.onNodeWithTag("tabs").captureToImage().toPixelMap()
        for (y in 0 until before.height) for (x in 0 until before.width) {
            assertTrue("Press must not paint a second capsule ahead of the moving indicator",
                distance(before[x, y], pressed[x, y]) < 0.005f)
        }
        rule.onNodeWithTag("tabs").performTouchInput { up() }
        rule.mainClock.advanceTimeByFrame()
        assertEquals("Selected label must not jump to the destination before the pager moves",
            selectedBefore, rule.onNode(isSelected()).fetchSemanticsNode().boundsInRoot)
        rule.mainClock.advanceTimeBy(400)
        rule.runOnIdle { assertEquals(2, pager.settledPage) }
        val selectedAfter = rule.onNode(isSelected()).fetchSemanticsNode().boundsInRoot
        assertTrue(selectedAfter.left > selectedBefore.right)
        rule.mainClock.autoAdvance = true
    }

    private fun setPagerContent(direction: LayoutDirection, dark: Boolean = false) {
        rule.setContent {
            CoinMonitorTheme(darkTheme = dark) {
                CoinMonitorDesignTheme {
                    val state = rememberPagerState(pageCount = { 3 })
                    val scope = rememberCoroutineScope()
                    val indicatorColor = CoinMonitorThemeTokens.design.colors.accent
                    SideEffect { pager = state; accent = indicatorColor }
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        Column(Modifier.width(300.dp).height(240.dp)) {
                            CoinMonitorSegmentedTabs(
                                tabs = List(3) { CoinMonitorSegmentedTab(it.toString(), "") },
                                selectedId = state.currentPage.toString(), onSelect = { id ->
                                    scope.launch { state.animateScrollToPage(id.toInt(), animationSpec = tween(280)) }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("tabs"), compactTrack = true,
                                indicatorPosition = { state.currentPage + state.currentPageOffsetFraction }
                            )
                            HorizontalPager(state, Modifier.weight(1f).testTag("pager")) { Text("Page $it") }
                        }
                    }
                }
            }
        }
        rule.mainClock.autoAdvance = false
    }

    private fun indicatorCenter(): Float {
        val pixels = rule.onNodeWithTag("tabs").captureToImage().toPixelMap()
        var sum = 0f
        var count = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            if (distance(pixels[x, y], accent) < 0.02f) { sum += x; count++ }
        }
        assertTrue("The indicator dot must be visible", count > 0)
        return sum / count
    }

    private fun assertIndicatorPosition(origin: Float, position: Float, direction: LayoutDirection) {
        val width = rule.onNodeWithTag("tabs").fetchSemanticsNode().boundsInRoot.width
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val slot = (width - 4f * density) / 3f
        val sign = if (direction == LayoutDirection.Ltr) 1f else -1f
        assertEquals("Indicator must share the pager position in the same frame", origin + sign * slot * position, indicatorCenter(), 3f)
    }

    @Test fun appearancePillRippleStaysInsideItsPaintedSurfaceInLightTheme() = verifyPill(false)
    @Test fun appearancePillRippleStaysInsideItsPaintedSurfaceInDarkTheme() = verifyPill(true)

    private fun verifyPill(dark: Boolean) {
        rule.setContent {
            CoinMonitorTheme(darkTheme = dark) {
                CoinMonitorDesignTheme {
                    val colors = CoinMonitorThemeTokens.design.colors
                    SideEffect { pageBackground = colors.pageBackground }
                    Box(Modifier.background(colors.pageBackground).padding(16.dp).testTag("scene")) {
                        CoinMonitorPillOption("Light", false, {}, Modifier.width(180.dp).testTag("control"))
                    }
                }
            }
        }
        assertRippleClippedToPaintedSurface()
    }

    @Test fun smallIconRippleStaysInsideTheCompactCircle() {
        rule.setContent {
            CoinMonitorTheme(darkTheme = false) {
                CoinMonitorDesignTheme {
                    val colors = CoinMonitorThemeTokens.design.colors
                    SideEffect { pageBackground = colors.pageBackground }
                    Box(Modifier.background(colors.pageBackground).padding(16.dp).testTag("scene")) {
                        CoinMonitorSmallIconButton(CoinMonitorIcons.Filters, "Filters", {}, Modifier.testTag("control"))
                    }
                }
            }
        }
        assertRippleClippedToPaintedSurface()
    }

    private fun assertRippleClippedToPaintedSurface() {
        val before = rule.onNodeWithTag("scene").captureToImage().toPixelMap()
        rule.onNodeWithTag("control").performTouchInput { down(center) }
        SystemClock.sleep(250) // Android 原生涟漪的渲染时钟独立于 Compose 测试时钟。
        val pressed = rule.onNodeWithTag("scene").captureToImage().toPixelMap()
        var changed = 0
        var outside = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            if (distance(before[x, y], pressed[x, y]) > 0.02f) {
                changed++
                if (distance(before[x, y], pageBackground) < 0.005f) outside++
            }
        }
        assertTrue("A press must show native ripple feedback", changed > 20)
        assertEquals("Ripple must not paint a second outer shape", 0, outside)
        rule.onNodeWithTag("control").performTouchInput { up() }
    }

    @Test fun bottomNavigationHasRippleAndRetainsSelectedSemantics() {
        var selection by mutableStateOf("home")
        rule.setContent {
            CoinMonitorTheme(darkTheme = false) {
                CoinMonitorBottomBar(
                    listOf(CoinMonitorBottomBarItem("home", "Market", CoinMonitorIcons.Market),
                        CoinMonitorBottomBarItem("wallet", "Wallet", CoinMonitorIcons.Wallet)),
                    selection, { selection = it }, Modifier.width(300.dp), WindowInsets(0)
                )
            }
        }
        val node = rule.onNodeWithText("Wallet")
        node.assertIsNotSelected()
        val before = node.captureToImage().toPixelMap()
        node.performTouchInput { down(Offset(centerX, height * 0.25f)) }
        SystemClock.sleep(250)
        val pressed = node.captureToImage().toPixelMap()
        var changed = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            if (distance(before[x, y], pressed[x, y]) > 0.02f) changed++
        }
        assertTrue("Bottom navigation must render a ripple", changed > 50)
        node.performTouchInput { up() }
        node.assertIsSelected()
        rule.runOnIdle { assertEquals("wallet", selection) }
    }

    @Test fun trendingFiltersRemainEnabledWhileDraggingTheBoard() {
        rule.setContent {
            CoinMonitorTheme(darkTheme = false) {
                TrendingScreen(TrendingUiState(), 0.dp, 0.dp, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {})
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filters = rule.onNode(
            hasContentDescription(context.getString(R.string.trending_filters)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        )
        filters.assertIsEnabled()
        val before = filters.captureToImage().toPixelMap()
        rule.onRoot().performTouchInput {
            down(Offset(width * 0.8f, height * 0.6f))
            moveTo(Offset(width * 0.5f, height * 0.6f), delayMillis = 100)
        }
        filters.assertIsEnabled()
        val during = filters.captureToImage().toPixelMap()
        for (y in 0 until before.height) for (x in 0 until before.width) {
            assertTrue("Filters must not flash a disabled color while dragging", distance(before[x, y], during[x, y]) < 0.01f)
        }
        rule.onRoot().performTouchInput { up() }
    }

    private fun distance(a: Color, b: Color): Float =
        maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue), abs(a.alpha - b.alpha))
}
