package io.baiyanwu.coinmonitor.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.MarketPageUrlResolver
import io.baiyanwu.coinmonitor.domain.model.OnchainChainIconRegistry
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.model.withQuote
import io.baiyanwu.coinmonitor.domain.repository.QuoteRepository
import io.baiyanwu.coinmonitor.overlay.QuoteFormatter
import io.baiyanwu.coinmonitor.ui.resolveChangeColor
import io.baiyanwu.coinmonitor.ui.resolveLivePriceColor
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.R
import kotlinx.coroutines.withTimeoutOrNull

private enum class WatchItemDragVisualState {
    Idle,
    Armed,
    Dragging
}

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
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val marketPageUrl = remember(item) { MarketPageUrlResolver.resolve(item) }
    val openMarketPageLabel = stringResource(R.string.home_open_market_page, item.symbol)
    val tokenAddress = item.tokenAddress
        ?.trim()
        ?.takeIf { item.marketType == MarketType.ONCHAIN_TOKEN && it.isNotEmpty() }
    val compactTokenAddress = remember(tokenAddress) {
        tokenAddress?.let(::compactContractAddress)
    }
    val copyCaDescription = stringResource(R.string.home_copy_ca_description)
    val caCopiedMessage = stringResource(R.string.home_ca_copied)
    val gestureAnchor = remember { WatchItemGestureAnchor() }
    val viewConfiguration = LocalViewConfiguration.current
    val dragLongPressTimeoutMillis = DRAG_LONG_PRESS_TIMEOUT_MILLIS
    val quickMenuLongPressTimeoutMillis = QUICK_MENU_LONG_PRESS_TIMEOUT_MILLIS
    val touchSlop = viewConfiguration.touchSlop
    var dragVisualState by remember(item.id) { mutableStateOf(WatchItemDragVisualState.Idle) }
    val pressTintAlpha by animateFloatAsState(
        targetValue = when (dragVisualState) {
            WatchItemDragVisualState.Idle -> 0f
            WatchItemDragVisualState.Armed -> 0.08f
            WatchItemDragVisualState.Dragging -> 0.12f
        },
        animationSpec = tween(durationMillis = 120),
        label = "watch_item_press_tint"
    )

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
                    try {
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
                                    // 首页排序是两段式长按：先进入拖拽预备态，再通过位移确认真正拖动。
                                    dragArmed = true
                                    dragVisualState = WatchItemDragVisualState.Armed
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
                                    dragVisualState = WatchItemDragVisualState.Dragging
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
                    } finally {
                        dragVisualState = WatchItemDragVisualState.Idle
                    }
                }
            }
            .drawWithContent {
                drawContent()
                if (pressTintAlpha > 0f) {
                    // 只给已经绘制出来的内容做按压压暗，避免把整行透明区域也涂成一条底板。
                    drawRect(
                        color = colors.primaryText.copy(alpha = pressTintAlpha),
                        blendMode = BlendMode.SrcAtop
                    )
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoilCoinSymbolIcon(
                symbol = item.baseSymbol,
                iconUrl = item.iconUrl,
                fallbackIconUrls = OnchainChainIconRegistry.resolveIconUrls(item.chainIndex),
                size = 20.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = if (marketPageUrl != null) {
                        Modifier.clickable(
                            onClickLabel = openMarketPageLabel,
                            role = Role.Button,
                            onClick = { runCatching { uriHandler.openUri(marketPageUrl) } }
                        )
                    } else {
                        Modifier
                    },
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.symbol,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 17.sp
                        ),
                        color = colors.primaryText,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (marketPageUrl != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = colors.accent
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tokenAddress != null && compactTokenAddress != null) {
                        Text(
                            text = compactTokenAddress,
                            maxLines = 1,
                            color = colors.secondaryText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                lineHeight = 11.sp
                            )
                        )
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = copyCaDescription,
                            modifier = Modifier
                                .size(12.dp)
                                .clickable(
                                    onClickLabel = copyCaDescription,
                                    role = Role.Button
                                ) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Contract address", tokenAddress))
                                    Toast.makeText(context, caCopiedMessage, Toast.LENGTH_SHORT).show()
                                },
                            tint = colors.accent
                        )
                    }
                    ExchangeBadge(source = item.exchangeSource)
                    if (item.marketType == MarketType.CEX_USDT_FUTURES) {
                        MiniTag(
                            text = stringResource(R.string.market_tag_usdt_futures),
                            containerColor = colors.cardBackground,
                            contentColor = colors.secondaryText
                        )
                    }
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
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = 13.sp,
            lineHeight = 17.sp
        ),
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
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 10.sp,
            lineHeight = 13.sp
        ),
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

// 第一阶段用于进入拖拽预备态，给用户明确的按压反馈，但不会立刻打断继续拖动的操作节奏。
private const val DRAG_LONG_PRESS_TIMEOUT_MILLIS = 350L

// 第二阶段用于弹出快捷操作菜单，刻意比拖拽预备态更晚，避免想排序时太容易误触弹框。
private const val QUICK_MENU_LONG_PRESS_TIMEOUT_MILLIS = 900L

@Composable
private fun ExchangeBadge(source: ExchangeSource) {
    val colors = CoinMonitorThemeTokens.colors
    val label = when (source) {
        ExchangeSource.BINANCE -> stringResource(R.string.exchange_badge_binance)
        ExchangeSource.BINANCE_ALPHA -> stringResource(R.string.exchange_badge_binance_alpha)
        ExchangeSource.OKX -> stringResource(R.string.exchange_badge_okx)
        ExchangeSource.ONCHAIN -> stringResource(R.string.exchange_badge_onchain)
    }

    MiniTag(
        text = label,
        containerColor = colors.cardBackground,
        contentColor = colors.secondaryText
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
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    lineHeight = 11.sp
                ),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

internal fun compactContractAddress(address: String): String {
    val trimmed = address.trim()
    if (trimmed.length <= CONTRACT_ADDRESS_VISIBLE_LENGTH) return trimmed
    return "${trimmed.take(CONTRACT_ADDRESS_HEAD_LENGTH)}…${trimmed.takeLast(CONTRACT_ADDRESS_TAIL_LENGTH)}"
}

private const val CONTRACT_ADDRESS_HEAD_LENGTH = 6
private const val CONTRACT_ADDRESS_TAIL_LENGTH = 4
private const val CONTRACT_ADDRESS_VISIBLE_LENGTH =
    CONTRACT_ADDRESS_HEAD_LENGTH + CONTRACT_ADDRESS_TAIL_LENGTH + 1
