package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

@Composable
fun MarketModeTabs(
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
    prominentLabels: Boolean = false,
    firstLabelRes: Int = R.string.search_mode_exchange,
    secondLabelRes: Int = R.string.search_mode_onchain,
    modifier: Modifier = Modifier,
    labelResources: List<Int> = listOf(firstLabelRes, secondLabelRes)
) {
    require(labelResources.isNotEmpty())
    val colors = CoinMonitorThemeTokens.colors
    val pageCount = labelResources.size
    val resolvedPage = selectedPage.coerceIn(0, pageCount - 1)

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val tabWidth = minOf(96.dp, maxWidth / pageCount)
        val indicatorWidth = minOf(32.dp, tabWidth)
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * resolvedPage + (tabWidth - indicatorWidth) / 2,
            animationSpec = tween(durationMillis = 180),
            label = "market-mode-indicator"
        )

        Box(
            modifier = Modifier
                .width(tabWidth * pageCount)
                .height(44.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                repeat(pageCount) { index ->
                    val selected = index == resolvedPage
                    val labelRes = labelResources[index]
                    Box(
                        modifier = Modifier
                            .width(tabWidth)
                            .height(44.dp)
                            .clickable { onSelectPage(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (selected) colors.accent else colors.secondaryText,
                            style = if (prominentLabels) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.labelLarge
                            },
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                    .width(indicatorWidth)
                    .height(3.dp)
                    .background(colors.accent, RoundedCornerShape(2.dp))
            )
        }
    }
}
