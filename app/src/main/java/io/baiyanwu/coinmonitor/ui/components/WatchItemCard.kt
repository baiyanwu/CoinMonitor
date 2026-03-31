package io.baiyanwu.coinmonitor.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.model.withQuote
import io.baiyanwu.coinmonitor.domain.repository.QuoteRepository
import io.baiyanwu.coinmonitor.overlay.QuoteFormatter
import io.baiyanwu.coinmonitor.ui.resolveChangeColor
import io.baiyanwu.coinmonitor.ui.resolveLivePriceColor
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.R

@Composable
fun WatchItemCard(
    item: WatchItem,
    quoteRepository: QuoteRepository,
    overlaySelected: Boolean,
    onClick: () -> Unit = {},
    onLongPress: (anchorInRoot: IntOffset) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val gestureAnchor = remember { WatchItemGestureAnchor() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("watch-item-${item.id}")
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                gestureAnchor.cardRootOffset = IntOffset(
                    x = position.x.roundToInt(),
                    y = position.y.roundToInt()
                )
            }
            .pointerInput(item.id, onClick, onLongPress) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { offset ->
                        onLongPress(
                            IntOffset(
                                x = gestureAnchor.cardRootOffset.x + offset.x.roundToInt(),
                                y = gestureAnchor.cardRootOffset.y + offset.y.roundToInt()
                            )
                        )
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoinSymbolIcon(item = item, modifier = Modifier.size(24.dp))

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
                WatchItemLiveQuote(
                    item = item,
                    quoteRepository = quoteRepository
                )
            }
        }
    }
}

@Composable
private fun WatchItemLiveQuote(
    item: WatchItem,
    quoteRepository: QuoteRepository
) {
    val colors = CoinMonitorThemeTokens.colors
    val quoteFlow = remember(item.id, quoteRepository) {
        quoteRepository.observeQuote(item.id)
    }
    val quoteState = quoteFlow
        .collectAsStateWithLifecycle(initialValue = quoteRepository.getQuote(item.id))
        .value
    val resolvedItem = item.withQuote(quoteState)

    Text(
        text = QuoteFormatter.formatPrice(resolvedItem.lastPrice),
        style = MaterialTheme.typography.titleSmall,
        color = resolvedItem.resolveLivePriceColor(
            colors = colors,
            defaultColor = colors.primaryText
        ),
        fontWeight = FontWeight.SemiBold
    )
    Text(
        text = QuoteFormatter.formatChange(resolvedItem.change24hPercent),
        color = resolvedItem.resolveChangeColor(
            colors = colors,
            defaultColor = colors.secondaryText
        ),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium
    )
}

private class WatchItemGestureAnchor {
    var cardRootOffset: IntOffset = IntOffset.Zero
}

@Composable
private fun ExchangeBadge(source: ExchangeSource) {
    val colors = CoinMonitorThemeTokens.colors
    val label = when (source) {
        ExchangeSource.BINANCE -> stringResource(R.string.exchange_badge_binance)
        ExchangeSource.BINANCE_ALPHA -> stringResource(R.string.exchange_badge_binance_alpha)
        ExchangeSource.OKX -> stringResource(R.string.exchange_badge_okx)
    }

    MiniTag(
        text = label,
        containerColor = colors.accent.copy(alpha = 0.16f),
        contentColor = colors.accent
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
