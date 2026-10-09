package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

/** 只提供紧凑行布局；图标、文字、数值和可选中间区域均由调用方提供。 */
@Composable
fun CoinMonitorListRow(
    leading: @Composable () -> Unit,
    title: @Composable () -> Unit,
    supporting: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    identityModifier: Modifier = Modifier,
    middle: (@Composable () -> Unit)? = null
) {
    val design = CoinMonitorThemeTokens.design
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = design.sizes.listRowMinimumHeight)
            .padding(horizontal = design.spacing.cardPadding, vertical = design.spacing.tiny),
        horizontalArrangement = Arrangement.spacedBy(design.spacing.inline),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Column(
            Modifier.weight(1f).heightIn(min = design.sizes.minimumTouchTarget).then(identityModifier),
            verticalArrangement = Arrangement.spacedBy(design.spacing.tiny, Alignment.CenterVertically)
        ) {
            title()
            supporting()
        }
        middle?.let { Box(contentAlignment = Alignment.Center) { it() } }
        Box(contentAlignment = Alignment.CenterEnd) { trailing() }
    }
}
