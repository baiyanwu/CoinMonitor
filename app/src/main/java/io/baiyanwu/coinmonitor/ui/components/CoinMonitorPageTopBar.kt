package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

/** 小字号品牌/页面标题与操作插槽；不处理导航或系统安全区。 */
@Composable
fun CoinMonitorPageTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val design = CoinMonitorThemeTokens.design
    Row(
        modifier.fillMaxWidth()
            .padding(horizontal = design.spacing.pageHorizontal, vertical = design.spacing.small)
            .heightIn(min = design.sizes.minimumTouchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(design.spacing.inline)
        ) {
            Box(Modifier.size(design.sizes.coinIcon), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(design.sizes.brandMark).rotate(45f)
                        .background(Brush.linearGradient(listOf(design.colors.brand, design.colors.accent)), design.shapes.brandMark),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(design.sizes.brandCutout).background(design.colors.surface))
                }
            }
            Text(
                stringResource(R.string.app_name), Modifier.weight(1f, fill = false),
                style = design.typography.label, color = design.colors.accent,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            VerticalDivider(Modifier.height(design.sizes.actionIcon), color = design.colors.divider)
            Text(title, style = design.typography.rowTitle, color = design.colors.primaryText, maxLines = 1)
        }
        actions()
    }
}
