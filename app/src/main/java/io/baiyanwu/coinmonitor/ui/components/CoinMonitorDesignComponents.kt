package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorDesignTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

/** 基础卡片仅管理外观和内边距，业务状态与操作由内容提供。 */
@Composable
fun CoinMonitorRoundedCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val design = CoinMonitorThemeTokens.design
    Surface(
        modifier = modifier,
        shape = design.shapes.card,
        color = design.colors.surface,
        contentColor = design.colors.primaryText
    ) {
        Column(
            Modifier.padding(contentPadding ?: PaddingValues(design.spacing.cardPadding)),
            content = content
        )
    }
}

/** Material Surface 在同一可见底盘内绘制原生涟漪，并提供最小触摸区域。 */
@Composable
fun CoinMonitorCompactActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    containerColor: Color? = null,
    contentColor: Color? = null
) {
    val design = CoinMonitorThemeTokens.design
    val fill = if (enabled) containerColor ?: design.colors.selectedContainer else design.colors.disabledContainer
    val foreground = if (enabled) contentColor ?: design.colors.selectedContent else design.colors.disabledContent
    Surface(
        onClick = onClick, enabled = enabled,
        modifier = modifier.semantics { role = Role.Button },
        shape = design.shapes.pill, color = fill, contentColor = foreground
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = design.sizes.compactActionMinimumHeight)
                .padding(horizontal = design.spacing.cardPadding, vertical = design.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(design.spacing.small, Alignment.CenterHorizontally)
        ) {
            leadingIcon?.let { Icon(it, null, Modifier.size(design.sizes.actionIcon), tint = foreground) }
            Text(
                label, Modifier.weight(1f, fill = trailingIcon != null), style = design.typography.label,
                color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            trailingIcon?.let { Icon(it, null, Modifier.size(design.sizes.actionIcon), tint = foreground) }
        }
    }
}

/** 可点击紧凑行适配器；复用通用列表布局，数据和业务操作由调用方提供。 */
@Composable
fun CoinMonitorCompactListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: @Composable () -> Unit,
    headline: @Composable () -> Unit,
    supporting: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {}
) {
    val design = CoinMonitorThemeTokens.design
    CoinMonitorListRow(
        leading = leading,
        title = headline,
        supporting = supporting,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(design.spacing.inline)) {
                trailing()
            }
        },
        modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    )
}

/** 可用在模式、来源或筛选选项；调用方管理选中值与 selectableGroup。 */
@Composable
fun CoinMonitorPillOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    showSelectionMark: Boolean = false,
    textStyle: TextStyle? = null,
    role: Role = Role.RadioButton
) {
    val design = CoinMonitorThemeTokens.design
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> design.colors.disabledContainer
            selected -> design.colors.selectedContainer
            else -> design.colors.surfaceMuted
        },
        animationSpec = tween(design.motion.selectionDurationMillis),
        label = "pill-container"
    )
    val contentColor = when {
        !enabled -> design.colors.disabledContent
        selected -> design.colors.selectedContent
        else -> design.colors.secondaryText
    }
    val content: @Composable () -> Unit = {
        BoxWithConstraints(
            Modifier.heightIn(min = design.sizes.pillMinimumHeight)
                .padding(horizontal = design.spacing.inline, vertical = design.spacing.small),
            contentAlignment = Alignment.Center
        ) {
            val boundedWidth = constraints.hasBoundedWidth
            Row(
                horizontalArrangement = Arrangement.spacedBy(design.spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leadingIcon?.let { icon ->
                    Icon(icon, null, Modifier.size(design.sizes.actionIcon), tint = contentColor)
                }
                Text(
                    label,
                    // LazyRow 提供无界横向约束，不能给文字分配剩余权重。
                    modifier = if (boundedWidth) Modifier.weight(1f, fill = false) else Modifier,
                    color = contentColor,
                    style = textStyle ?: design.typography.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showSelectionMark && selected) {
                    Icon(CoinMonitorIcons.Check, null, Modifier.size(design.sizes.selectionMark), tint = contentColor)
                }
            }
        }
    }
    // 背景、形状、交互和涟漪由同一个 Material Surface 管理。
    if (role == Role.Checkbox) {
        Surface(
            checked = selected, onCheckedChange = { onClick() }, enabled = enabled,
            modifier = modifier.semantics { this.role = role }, shape = design.shapes.pill,
            color = containerColor, contentColor = contentColor, content = content
        )
    } else {
        Surface(
            selected = selected, onClick = onClick, enabled = enabled,
            modifier = modifier.semantics { this.role = role }, shape = design.shapes.pill,
            color = containerColor, contentColor = contentColor, content = content
        )
    }
}

/** 通用导航行只接收展示参数与点击回调；字体放大时允许内容撑高。 */
@Composable
fun CoinMonitorNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null
) {
    val design = CoinMonitorThemeTokens.design
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = design.sizes.settingsRowMinimumHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = design.spacing.cardPadding, vertical = design.spacing.rowVertical),
        horizontalArrangement = Arrangement.spacedBy(design.spacing.group),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingIcon?.let { icon ->
            Box(
                Modifier.size(design.sizes.actionContainer)
                    .background(design.colors.selectedContainer, design.shapes.pill),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(design.sizes.actionIcon), tint = design.colors.accent)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(design.spacing.tiny)) {
            Text(title, style = design.typography.rowTitle, color = design.colors.primaryText)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = design.typography.rowSubtitle, color = design.colors.secondaryText)
            }
        }
        Icon(
            CoinMonitorIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(design.sizes.actionIcon),
            tint = design.colors.tertiaryText
        )
    }
}

/** 可见底盘 32dp；独立保留至少 48dp 的可访问点击区域。 */
@Composable
fun CoinMonitorSmallIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color? = null
) {
    val design = CoinMonitorThemeTokens.design
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.minimumInteractiveComponentSize().size(design.sizes.actionContainer),
        shape = design.shapes.pill,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor ?: design.colors.selectedContainer,
            contentColor = design.colors.selectedContent,
            disabledContainerColor = design.colors.disabledContainer,
            disabledContentColor = design.colors.disabledContent
        )
    ) {
        Icon(icon, contentDescription, Modifier.size(design.sizes.actionIcon))
    }
}

@Preview(name = "通用控件 · 浅色", showBackground = true)
@Composable
private fun DesignComponentsLightPreview() = DesignComponentsPreview(darkTheme = false)

@Preview(name = "通用控件 · 深色", showBackground = true)
@Composable
private fun DesignComponentsDarkPreview() = DesignComponentsPreview(darkTheme = true)

@Composable
private fun DesignComponentsPreview(darkTheme: Boolean) {
    CoinMonitorTheme(darkTheme = darkTheme) {
        CoinMonitorDesignTheme {
            val design = CoinMonitorThemeTokens.design
            Column(
                Modifier.background(design.colors.pageBackground).padding(design.spacing.pageHorizontal),
                verticalArrangement = Arrangement.spacedBy(design.spacing.group)
            ) {
                CoinMonitorRoundedCard {
                    Text("ETH / USDT", style = design.typography.rowTitle)
                    Text("示例内容", style = design.typography.rowSubtitle, color = design.colors.secondaryText)
                }
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(design.spacing.inline)) {
                    CoinMonitorPillOption("交易所", selected = true, onClick = {})
                    CoinMonitorPillOption("链上", selected = false, onClick = {})
                    CoinMonitorPillOption("禁用", selected = false, enabled = false, onClick = {})
                }
                Row {
                    CoinMonitorSmallIconButton(CoinMonitorIcons.Search, "搜索", onClick = {})
                    CoinMonitorSmallIconButton(CoinMonitorIcons.More, "更多", onClick = {}, enabled = false)
                }
            }
        }
    }
}
