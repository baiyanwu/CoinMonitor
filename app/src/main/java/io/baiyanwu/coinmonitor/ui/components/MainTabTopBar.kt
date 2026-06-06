package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

val MainTabTopBarHeight = 38.dp
val MainTabTopBarHorizontalPadding = 14.dp
val MainTabTopBarVerticalPadding = 4.dp

/**
 * 主 Tab 页统一顶部栏容器。
 *
 * 外层 Box 控制水平/垂直内边距（14dp / 4dp），内部 Row 固定 38dp 高度，
 * 垂直居中对齐。内容由外部传入，保证首页和设置页标题视觉位置完全一致。
 */
@Composable
fun MainTabTopBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = MainTabTopBarHorizontalPadding,
                vertical = MainTabTopBarVerticalPadding
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MainTabTopBarHeight),
            verticalAlignment = Alignment.CenterVertically,
            content = { content() }
        )
    }
}

@Composable
fun SearchEntryButton(onClick: () -> Unit) {
    TopBarCircleActionButton(
        imageVector = Icons.Rounded.Add,
        contentDescription = "",
        onClick = onClick
    )
}
