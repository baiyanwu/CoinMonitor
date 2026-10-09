package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import androidx.compose.ui.semantics.Role

@Immutable
data class CoinMonitorSegmentedTab(val id: String, val label: String)

/** 紧凑胶囊标签。选中值由外部管理，允许字体放大后自然增加高度。 */
@Composable
fun CoinMonitorSegmentedTabs(
    tabs: List<CoinMonitorSegmentedTab>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val design = CoinMonitorThemeTokens.design
    Surface(modifier, shape = design.shapes.pill, color = design.colors.surfaceMuted) {
        Row(
            Modifier.fillMaxWidth().selectableGroup().padding(horizontal = design.spacing.tiny),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                key(tab.id) {
                    val selected = tab.id == selectedId
                    val background by animateColorAsState(
                        if (selected) design.colors.surface else Color.Transparent,
                        tween(design.motion.selectionDurationMillis), label = "segment-${tab.id}"
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = design.sizes.minimumTouchTarget)
                            .clip(design.shapes.pill)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.id) })
                            .padding(horizontal = design.spacing.tiny),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = design.sizes.segmentedIndicatorMinimumHeight)
                                .background(background, design.shapes.pill)
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
                                    .background(if (selected) design.colors.accent else Color.Transparent, design.shapes.pill)
                            )
                        }
                    }
                }
            }
        }
    }
}
