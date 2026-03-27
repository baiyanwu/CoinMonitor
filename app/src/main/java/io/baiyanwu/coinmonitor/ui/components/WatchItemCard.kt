package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.overlay.QuoteFormatter
import io.baiyanwu.coinmonitor.ui.resolveChangeColor
import io.baiyanwu.coinmonitor.ui.resolveLivePriceColor
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchItemCard(
    item: WatchItem,
    overlaySelected: Boolean,
    onLongPress: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            )
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoinSymbolIcon(
            symbol = item.baseSymbol,
            modifier = Modifier.size(24.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.symbol,
                style = MaterialTheme.typography.titleSmall,
                color = colors.primaryText,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExchangeBadge(source = item.exchangeSource)
                if (overlaySelected) {
                    MiniTag(
                        text = stringResource(R.string.home_overlay_tag),
                        containerColor = colors.heroBackground,
                        contentColor = colors.secondaryText
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = QuoteFormatter.formatPrice(item.lastPrice),
                style = MaterialTheme.typography.titleSmall,
                color = item.resolveLivePriceColor(
                    colors = colors,
                    defaultColor = colors.primaryText
                ),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = QuoteFormatter.formatChange(item.change24hPercent),
                color = item.resolveChangeColor(
                    colors = colors,
                    defaultColor = colors.secondaryText
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ExchangeBadge(source: ExchangeSource) {
    val colors = CoinMonitorThemeTokens.colors
    val (label, containerColor, contentColor) = when (source) {
        ExchangeSource.BINANCE -> Triple(
            "BINANCE",
            colors.heroBackground,
            colors.secondaryText
        )

        ExchangeSource.BINANCE_ALPHA -> Triple(
            "ALPHA",
            colors.accent.copy(alpha = 0.16f),
            colors.accent
        )

        ExchangeSource.OKX -> Triple(
            "OKX",
            colors.heroBackground,
            colors.secondaryText
        )
    }

    MiniTag(
        text = label,
        containerColor = containerColor,
        contentColor = contentColor
    )
}

@Composable
private fun MiniTag(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
