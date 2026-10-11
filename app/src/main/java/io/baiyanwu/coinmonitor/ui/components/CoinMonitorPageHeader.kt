package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/** 无操作按钮的主页面标题，复用四个主页面共用的品牌栏样式。 */
@Composable
fun CoinMonitorPageHeader(title: String, modifier: Modifier = Modifier) {
    CoinMonitorPageTopBar(
        title = title,
        modifier = modifier.semantics(mergeDescendants = true) { heading() }
    )
}
