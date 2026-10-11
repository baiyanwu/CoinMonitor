package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset

@Immutable
data class CoinMonitorSegmentedTab(val id: String, val label: String)

/** 紧凑胶囊标签。选中值由外部管理，允许字体放大后自然增加高度。 */
@Composable
fun CoinMonitorSegmentedTabs(
    tabs: List<CoinMonitorSegmentedTab>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    compactTrack: Boolean = false,
    indicatorPosition: (() -> Float)? = null
) {
    val design = CoinMonitorThemeTokens.design
    Surface(modifier, shape = design.shapes.pill, color = Color.Transparent) {
        BoxWithConstraints {
            Box(
                Modifier.matchParentSize()
                    .padding(vertical = if (compactTrack) design.spacing.small else 0.dp)
                    .background(design.colors.surfaceMuted, design.shapes.pill)
            )
            if (compactTrack && tabs.isNotEmpty()) {
                val selectedIndex = tabs.indexOfFirst { it.id == selectedId }
                val slotWidth = (maxWidth - design.spacing.tiny * 2).coerceAtLeast(0.dp) / tabs.size
                // 非分页调用保留独立动效；分页调用在布局阶段直接读取滚动位置。
                val indicatorOffset = if (indicatorPosition == null) animateDpAsState(
                    slotWidth * selectedIndex.coerceAtLeast(0),
                    tween(design.motion.pageSwitchDurationMillis, easing = FastOutSlowInEasing),
                    label = "segment-indicator-offset"
                ) else null
                if (selectedIndex >= 0) Box(
                    Modifier.matchParentSize().padding(horizontal = design.spacing.tiny, vertical = design.spacing.small)
                ) {
                    // 唯一的指示胶囊连续滑动；各标签保持原位，不绘制彼此独立的底色。
                    Box(
                        Modifier.fillMaxHeight().width(slotWidth)
                            .offset {
                                val position = indicatorPosition?.invoke()?.coerceIn(0f, tabs.lastIndex.toFloat())
                                val offset = if (position != null) slotWidth * position else indicatorOffset!!.value
                                IntOffset(offset.roundToPx(), 0)
                            }
                            .padding(horizontal = design.spacing.tiny)
                            .background(design.colors.surface, design.shapes.pill)
                    ) {
                        Box(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = design.spacing.inline)
                                .size(design.sizes.selectionDot).background(design.colors.accent, design.shapes.pill)
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().selectableGroup().padding(horizontal = design.spacing.tiny),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    key(tab.id) {
                        val selected = tab.id == selectedId
                        val background = if (compactTrack) {
                            Color.Transparent
                        } else {
                            val animated by animateColorAsState(
                                if (selected) design.colors.surface else Color.Transparent,
                                tween(design.motion.selectionDurationMillis), label = "segment-${tab.id}"
                            )
                            animated
                        }
                        val content: @Composable () -> Unit = {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = design.sizes.segmentedIndicatorMinimumHeight)
                                    .padding(horizontal = design.spacing.small, vertical = design.spacing.small),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    tab.label, style = design.typography.label,
                                    color = if (selected) design.colors.selectedContent else design.colors.secondaryText
                                )
                                Box(
                                    Modifier.padding(top = design.spacing.tiny).size(design.sizes.selectionDot)
                                        .background(if (selected && !compactTrack) design.colors.accent else Color.Transparent, design.shapes.pill)
                                )
                            }
                        }
                        val tabModifier = Modifier.weight(1f).padding(horizontal = design.spacing.tiny)
                        val contentColor = if (selected) design.colors.selectedContent else design.colors.secondaryText
                        if (compactTrack) {
                            // 移动胶囊是唯一的选中反馈；按压时不在目标标签上另画一块涟漪底色。
                            Surface(
                                modifier = tabModifier.minimumInteractiveComponentSize().selectable(
                                    selected = selected,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null, role = Role.Tab,
                                    onClick = { onSelect(tab.id) }
                                ),
                                shape = design.shapes.pill, color = Color.Transparent,
                                contentColor = contentColor, content = content
                            )
                        } else {
                            Surface(
                                selected = selected, onClick = { onSelect(tab.id) },
                                modifier = tabModifier.semantics { role = Role.Tab },
                                shape = design.shapes.pill, color = background,
                                contentColor = contentColor, content = content
                            )
                        }
                    }
                }
            }
        }
    }
}
