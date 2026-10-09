package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorDesignTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

@Immutable
data class CoinMonitorBottomBarItem(val id: String, val label: String, val icon: ImageVector)

/** 无导航依赖；selectedId 与 onSelect 由主导航提供，安全区只在此处计算一次。 */
@Composable
fun CoinMonitorBottomBar(
    items: List<CoinMonitorBottomBarItem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets
) {
    CoinMonitorDesignTheme {
        val design = CoinMonitorThemeTokens.design
        Box(
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .padding(horizontal = design.spacing.pageHorizontal, vertical = design.spacing.inline)
        ) {
            Surface(
                shape = design.shapes.bottomBar,
                color = design.colors.surface,
                contentColor = design.colors.secondaryText,
                border = BorderStroke(0.5.dp, design.colors.divider)
            ) {
                Row(
                    Modifier.fillMaxWidth().selectableGroup().padding(design.spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { item ->
                        val selected = item.id == selectedId
                        val indicatorColor by animateColorAsState(
                            if (selected) design.colors.selectedContainer else Color.Transparent,
                            tween(design.motion.selectionDurationMillis),
                            label = "tab-indicator-${item.id}"
                        )
                        val contentColor by animateColorAsState(
                            if (selected) design.colors.selectedContent else design.colors.secondaryText,
                            tween(design.motion.selectionDurationMillis),
                            label = "tab-content-${item.id}"
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .widthIn(min = design.sizes.minimumTouchTarget)
                                .heightIn(min = design.sizes.bottomTabMinimumHeight)
                                .clip(design.shapes.pill)
                                .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item.id) })
                                .padding(vertical = design.spacing.tiny),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                Modifier
                                    .size(design.sizes.tabIndicatorWidth, design.sizes.tabIndicatorHeight)
                                    .background(indicatorColor, design.shapes.pill),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(item.icon, contentDescription = null, Modifier.size(design.sizes.tabIcon), tint = contentColor)
                            }
                            Spacer(Modifier.height(design.spacing.tiny))
                            Text(item.label, style = design.typography.tabLabel, color = contentColor)
                            Spacer(Modifier.height(design.spacing.tiny))
                            Box(
                                Modifier.size(design.sizes.selectionDot).background(
                                    if (selected) design.colors.accent else Color.Transparent,
                                    design.shapes.pill
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "底部 Tab · 浅色", widthDp = 360, showBackground = true)
@Composable
private fun BottomBarLightPreview() = BottomBarPreview(darkTheme = false)

@Preview(name = "底部 Tab · 深色", widthDp = 360, showBackground = true)
@Composable
private fun BottomBarDarkPreview() = BottomBarPreview(darkTheme = true)

@Preview(name = "底部 Tab · 大字体", widthDp = 320, fontScale = 1.5f, showBackground = true)
@Composable
private fun BottomBarLargeFontPreview() = BottomBarPreview(darkTheme = false)

@Composable
private fun BottomBarPreview(darkTheme: Boolean) {
    CoinMonitorTheme(darkTheme = darkTheme) {
        Box(Modifier.background(CoinMonitorThemeTokens.design.colors.pageBackground)) {
            CoinMonitorBottomBar(
                items = listOf(
                    CoinMonitorBottomBarItem("home", "行情", CoinMonitorIcons.Market),
                    CoinMonitorBottomBarItem("trending", "热门", CoinMonitorIcons.Trending),
                    CoinMonitorBottomBarItem("wallet", "钱包", CoinMonitorIcons.Wallet),
                    CoinMonitorBottomBarItem("settings", "设置", CoinMonitorIcons.Settings)
                ),
                selectedId = "home",
                onSelect = {},
                windowInsets = WindowInsets(0)
            )
        }
    }
}
