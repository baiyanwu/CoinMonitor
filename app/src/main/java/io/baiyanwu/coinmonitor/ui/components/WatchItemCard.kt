package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private enum class WatchItemDragVisualState { Idle, Armed, Dragging }

/** 首页行的手势容器；展示内容通过插槽传入，不订阅行情或处理业务操作。 */
@Composable
fun WatchItemCard(
    itemId: String,
    modifier: Modifier = Modifier,
    dragOffsetY: Float = 0f,
    clickLabel: String? = null,
    onClick: (() -> Unit)? = null,
    onLongPress: (anchorInRoot: IntOffset) -> Unit,
    onDragStart: () -> Unit = {},
    onDragBy: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val gestureAnchor = remember { WatchItemGestureAnchor() }
    val viewConfiguration = LocalViewConfiguration.current
    val dragLongPressTimeoutMillis = DRAG_LONG_PRESS_TIMEOUT_MILLIS
    val quickMenuLongPressTimeoutMillis = QUICK_MENU_LONG_PRESS_TIMEOUT_MILLIS
    val touchSlop = viewConfiguration.touchSlop
    var dragVisualState by remember(itemId) { mutableStateOf(WatchItemDragVisualState.Idle) }
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
            .testTag("watch-item-$itemId")
            .semantics(mergeDescendants = true) {
                if (onClick != null) {
                    role = Role.Button
                    onClick(label = clickLabel) { onClick(); true }
                }
            }
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
                itemId,
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
                                    !dragArmed && !quickMenuOpened && (latestPosition - downPosition).getDistance() <= touchSlop -> onClick?.invoke()
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
    ) { content() }
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

internal fun compactContractAddress(address: String): String {
    val trimmed = address.trim()
    if (trimmed.length <= CONTRACT_ADDRESS_VISIBLE_LENGTH) return trimmed
    return "${trimmed.take(CONTRACT_ADDRESS_HEAD_LENGTH)}…${trimmed.takeLast(CONTRACT_ADDRESS_TAIL_LENGTH)}"
}

private const val CONTRACT_ADDRESS_HEAD_LENGTH = 6
private const val CONTRACT_ADDRESS_TAIL_LENGTH = 4
private const val CONTRACT_ADDRESS_VISIBLE_LENGTH =
    CONTRACT_ADDRESS_HEAD_LENGTH + CONTRACT_ADDRESS_TAIL_LENGTH + 1
