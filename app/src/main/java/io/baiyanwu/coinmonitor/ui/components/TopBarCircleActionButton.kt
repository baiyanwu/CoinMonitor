package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

/**
 * 顶部栏统一小圆按钮。
 *
 * 首页右上角和 K 线页右上角都走同一套尺寸与居中规则，
 * 避免页面切换时出现“看起来上下没对齐”的视觉偏移。
 */
@Composable
fun TopBarCircleActionButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = CoinMonitorThemeTokens.colors

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.fabContainer,
        tonalElevation = 0.dp,
        modifier = modifier.size(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = colors.fabContent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
