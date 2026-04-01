package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun WatchItemCard(
    item: WatchItem,
    quoteRepository: QuoteRepository,
    overlaySelected: Boolean,
    modifier: Modifier = Modifier,
    dragOffsetY: Float = 0f,
    onClick: () -> Unit = {},
    onLongPress: (anchorInRoot: IntOffset) -> Unit,
    onDragStart: () -> Unit = {},
    onDragBy: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {}
) {
    val colors = CoinMonitorThemeTokens.colors
    val gestureAnchor = remember { WatchItemGestureAnchor() }
    val viewConfiguration = LocalViewConfiguration.current
    val dragLongPressTimeoutMillis = DRAG_LONG_PRESS_TIMEOUT_MILLIS
    val quickMenuLongPressTimeoutMillis = QUICK_MENU_LONG_PRESS_TIMEOUT_MILLIS
    val touchSlop = viewConfiguration.touchSlop

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("watch-item-${item.id}")
            .graphicsLayer {
                translationY = dragOffsetY
            }
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                gestureAnchor.cardRootOffset = IntOffset(
                    x = position.x.roundToInt(),
                    y = position.y.roundToInt()
                )
            }
            .pointerInput(
                item.id,
                onClick,
                onLongPress,
                onDragStart,
                onDragBy,
                onDragEnd,
                onDragCancel,
                dragLongPressTimeoutMillis,
                quickMenuLongPressTimeoutMillis,
                touchSlop
            ) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val downPosition = down.position
                    var latestPosition = down.position
                    var elapsedMillis = 0L
                    var dragArmed = false
                    var dragActive = false
                    var quickMenuOpened = false
                    var dragReference = down.position

                    while (true) {
                        when {
                            !dragArmed && elapsedMillis >= dragLongPressTimeoutMillis -> {
                                dragArmed = true
                                dragReference = latestPosition
                                continue
                            }

                            !quickMenuOpened && !dragActive && elapsedMillis >= quickMenuLongPressTimeoutMillis -> {
                                quickMenuOpened = true
                                onLongPress(
                                    IntOffset(
                                        x = gestureAnchor.cardRootOffset.x + latestPosition.x.roundToInt(),
                                        y = gestureAnchor.cardRootOffset.y + latestPosition.y.roundToInt()
                                    )
                                )
                                break
                            }
                        }

                        val nextTimeoutMillis = when {
                            dragActive -> null
                            !dragArmed -> dragLongPressTimeoutMillis
                            else -> quickMenuLongPressTimeoutMillis
                        }
                        val event = if (nextTimeoutMillis == null) {
                            awaitPointerEvent()
                        } else {
                            val waitMillis = (nextTimeoutMillis - elapsedMillis).coerceAtLeast(1L)
                            withTimeoutOrNull(waitMillis) {
                                awaitPointerEvent()
                            }
                        }
                        if (event == null) {
                            elapsedMillis = nextTimeoutMillis ?: elapsedMillis
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        latestPosition = change.position
                        elapsedMillis = change.uptimeMillis - down.uptimeMillis

                        if (!dragActive && change.isConsumed) {
                            onDragCancel()
                            break
                        }

                        if (change.changedToUpIgnoreConsumed()) {
                            when {
                                dragActive -> onDragEnd()
                                !dragArmed && !quickMenuOpened && (latestPosition - downPosition).getDistance() <= touchSlop -> onClick()
                                else -> onDragCancel()
                            }
                            break
                        }

                        if (!dragActive && dragArmed) {
                            val dragDistance = (latestPosition - dragReference).getDistance()
                            if (dragDistance > touchSlop) {
                                dragActive = true
                                change.consume()
                                onDragStart()
                                val initialDelta = latestPosition - dragReference
                                if (initialDelta != Offset.Zero) {
                                    onDragBy(initialDelta.y)
                                }
                                dragReference = latestPosition
                                continue
                            }
                        }

                        if (dragActive) {
                            val delta = change.positionChangeIgnoreConsumed()
                            if (delta != Offset.Zero) {
                                change.consume()
                                onDragBy(delta.y)
                            }
                        }
                    }
                }
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
                    if (item.homePinned) {
                        MiniTag(
                            text = stringResource(R.string.home_pinned_tag),
                            containerColor = colors.accent.copy(alpha = 0.16f),
                            contentColor = colors.accent
                        )
                    }
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

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitFirstDown(): PointerInputChange {
    while (true) {
        val event = awaitPointerEvent()
        val down = event.changes.firstOrNull { it.pressed } ?: continue
        return down
    }
}

private const val DRAG_LONG_PRESS_TIMEOUT_MILLIS = 400L
private const val QUICK_MENU_LONG_PRESS_TIMEOUT_MILLIS = 650L

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
