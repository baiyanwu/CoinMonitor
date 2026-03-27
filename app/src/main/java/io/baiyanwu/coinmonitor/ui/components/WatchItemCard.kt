package io.baiyanwu.coinmonitor.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.overlay.QuoteFormatter
import io.baiyanwu.coinmonitor.ui.resolveChangeColor
import io.baiyanwu.coinmonitor.ui.resolveLivePriceColor
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.R

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun WatchItemCard(
    item: WatchItem,
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
                gestureAnchor.cardSize = coordinates.size
            }
            .pointerInteropFilter { motionEvent ->
                if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN) {
                    gestureAnchor.lastTouchOffset = Offset(motionEvent.x, motionEvent.y)
                }
                false
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    val anchorX = if (gestureAnchor.lastTouchOffset != Offset.Zero) {
                        gestureAnchor.lastTouchOffset.x.roundToInt()
                    } else if (gestureAnchor.cardSize.width > 0) {
                        gestureAnchor.cardSize.width / 2
                    } else {
                        180
                    }
                    val anchorY = if (gestureAnchor.lastTouchOffset != Offset.Zero) {
                        gestureAnchor.lastTouchOffset.y.roundToInt()
                    } else if (gestureAnchor.cardSize.height > 0) {
                        gestureAnchor.cardSize.height / 2
                    } else {
                        24
                    }
                    onLongPress(
                        IntOffset(
                            x = gestureAnchor.cardRootOffset.x + anchorX,
                            y = gestureAnchor.cardRootOffset.y + anchorY
                        )
                    )
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
}

private class WatchItemGestureAnchor {
    var cardRootOffset: IntOffset = IntOffset.Zero
    var cardSize: IntSize = IntSize.Zero
    var lastTouchOffset: Offset = Offset.Zero
}

@Composable
private fun ExchangeBadge(source: ExchangeSource) {
    val colors = CoinMonitorThemeTokens.colors
    val (label, containerColor, contentColor) = when (source) {
        ExchangeSource.BINANCE -> Triple(
            stringResource(R.string.exchange_badge_binance),
            colors.heroBackground,
            colors.secondaryText
        )

        ExchangeSource.BINANCE_ALPHA -> Triple(
            stringResource(R.string.exchange_badge_binance_alpha),
            colors.accent.copy(alpha = 0.16f),
            colors.accent
        )

        ExchangeSource.OKX -> Triple(
            stringResource(R.string.exchange_badge_okx),
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
