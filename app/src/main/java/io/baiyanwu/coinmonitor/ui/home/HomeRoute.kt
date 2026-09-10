package io.baiyanwu.coinmonitor.ui.home

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.repository.QuoteRepository
import io.baiyanwu.coinmonitor.ui.components.MainTabTopBarHorizontalPadding
import io.baiyanwu.coinmonitor.ui.components.MarketModeTabs
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.ui.components.WatchItemCard
import io.baiyanwu.coinmonitor.ui.search.SearchMode
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private data class HomeQuickMenuState(
    val itemId: String,
    val anchorInRoot: IntOffset
)

private data class HomeDragState(
    val itemId: String,
    val homePinned: Boolean,
    val dragOffsetY: Float = 0f,
    val didReorder: Boolean = false
)

private val HOME_MODES = listOf(SearchMode.EXCHANGE, SearchMode.ONCHAIN)

internal fun filterHomeWatchItems(
    items: List<WatchItem>,
    mode: SearchMode
): List<WatchItem> = when (mode) {
    SearchMode.EXCHANGE -> items.filter { it.marketType != MarketType.ONCHAIN_TOKEN }
    SearchMode.ONCHAIN -> items.filter { it.marketType == MarketType.ONCHAIN_TOKEN }
}

@Composable
fun HomeRoute(
    container: AppContainer,
    contentTopInset: Dp = 0.dp,
    contentBottomInset: Dp = 0.dp,
    onNavigateSearch: (SearchMode) -> Unit,
    onNavigateOverlayItems: () -> Unit,
    onNavigateOverlaySettings: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.setScreenActive(true)
                }
                Lifecycle.Event.ON_PAUSE -> viewModel.setScreenActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setScreenActive(false)
        }
    }

    LaunchedEffect(state.noticeMessage) {
        val message = state.noticeMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeNotice()
    }

    HomeScreen(
        state = state,
        contentTopInset = contentTopInset,
        contentBottomInset = contentBottomInset,
        onNavigateSearch = onNavigateSearch,
        onNavigateOverlayItems = onNavigateOverlayItems,
        onNavigateOverlaySettings = onNavigateOverlaySettings,
        quoteRepository = container.quoteRepository,
        onRemoveWatchItem = viewModel::removeWatchItem,
        onToggleOverlay = viewModel::toggleOverlay,
        onSetHomePinned = viewModel::setHomePinned,
        onMoveHomeItem = viewModel::moveHomeItem,
        onMovePinnedHomeItem = viewModel::movePinnedHomeItem,
        onRefresh = viewModel::refreshNow
    )
}

@Composable
internal fun HomeScreen(
    state: HomeUiState,
    contentTopInset: Dp,
    contentBottomInset: Dp,
    onNavigateSearch: (SearchMode) -> Unit,
    onNavigateOverlayItems: () -> Unit,
    onNavigateOverlaySettings: () -> Unit,
    quoteRepository: QuoteRepository,
    onRemoveWatchItem: (String) -> Unit,
    onToggleOverlay: (String) -> Unit,
    onSetHomePinned: (String, Boolean) -> Unit,
    onMoveHomeItem: (String, String?) -> Unit,
    onMovePinnedHomeItem: (String, String?) -> Unit,
    onRefresh: () -> Unit
) {
    var quickMenuState by remember { mutableStateOf<HomeQuickMenuState?>(null) }
    var pairActionsExpanded by remember { mutableStateOf(false) }
    var showOverlayEnableDialog by remember { mutableStateOf(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val colors = CoinMonitorThemeTokens.colors
    val dismissInteractionSource = remember { MutableInteractionSource() }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = HOME_MODES::size
    )
    val exchangeListState = rememberLazyListState()
    val onchainListState = rememberLazyListState()
    val pagerScope = rememberCoroutineScope()
    val pageItems = remember(state.items) {
        HOME_MODES.map { mode -> filterHomeWatchItems(state.items, mode) }
    }
    val overlayEnableDialogTitle = stringResource(R.string.home_overlay_enable_dialog_title)
    val overlayEnableDialogMessage = stringResource(R.string.home_overlay_enable_dialog_message)
    val overlayEnableDialogDismiss = stringResource(R.string.common_cancel)
    val overlayEnableDialogConfirm = stringResource(R.string.home_overlay_enable_dialog_confirm)

    LaunchedEffect(pagerState.currentPage) {
        quickMenuState = null
        pairActionsExpanded = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .padding(top = contentTopInset, bottom = contentBottomInset)
            .onSizeChanged { rootSize = it }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MainTabTopBarHorizontalPadding, vertical = 4.dp)
                    .height(44.dp)
            ) {
                MarketModeTabs(
                    selectedPage = pagerState.currentPage,
                    onSelectPage = { page ->
                        pagerScope.launch {
                            pagerState.animateScrollToPage(page)
                        }
                    },
                    prominentLabels = true,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                val mode = HOME_MODES[it]
                HomeWatchlistPage(
                    mode = mode,
                    isLoaded = state.isLoaded,
                    isRefreshing = state.isRefreshing,
                    items = pageItems[it],
                    overlayIds = state.overlayIds,
                    listState = if (mode == SearchMode.EXCHANGE) {
                        exchangeListState
                    } else {
                        onchainListState
                    },
                    quoteRepository = quoteRepository,
                    showOnchainMarketCap = state.showOnchainMarketCap,
                    onNavigateSearch = { onNavigateSearch(mode) },
                    onDismissQuickMenu = { quickMenuState = null },
                    onLongPress = { item, anchorInRoot ->
                        quickMenuState = if (quickMenuState?.itemId == item.id) {
                            null
                        } else {
                            HomeQuickMenuState(itemId = item.id, anchorInRoot = anchorInRoot)
                        }
                    },
                    onMoveHomeItem = onMoveHomeItem,
                    onMovePinnedHomeItem = onMovePinnedHomeItem,
                    onRefresh = onRefresh
                )
            }
        }

        HomePairActionsMenu(
            expanded = pairActionsExpanded,
            onExpandedChange = { expanded ->
                quickMenuState = null
                pairActionsExpanded = expanded
            },
            onAddPair = {
                if (pairActionsExpanded) {
                    pairActionsExpanded = false
                    onNavigateSearch(HOME_MODES[pagerState.currentPage])
                }
            },
            onConfigureOverlayItems = {
                if (pairActionsExpanded) {
                    pairActionsExpanded = false
                    onNavigateOverlayItems()
                }
            },
            bottomInset = 0.dp,
            modifier = Modifier.matchParentSize()
        )

        quickMenuState?.let { menuState ->
            val menuItem = state.items.firstOrNull { it.id == menuState.itemId } ?: return@let
            HomeQuickActionsOverlay(
                quickMenuState = menuState,
                screenWidthPx = rootSize.width,
                overlaySelected = state.overlayIds.contains(menuState.itemId),
                homePinned = menuItem.homePinned,
                onDismiss = { quickMenuState = null },
                onToggleOverlay = {
                    val shouldAddToOverlay = !state.overlayIds.contains(menuState.itemId)
                    if (shouldAddToOverlay && !state.overlayEnabled) {
                        showOverlayEnableDialog = true
                    } else {
                        onToggleOverlay(menuState.itemId)
                    }
                    quickMenuState = null
                },
                onDelete = {
                    onRemoveWatchItem(menuState.itemId)
                    quickMenuState = null
                },
                onTogglePin = {
                    onSetHomePinned(menuState.itemId, !menuItem.homePinned)
                    quickMenuState = null
                },
                dismissInteractionSource = dismissInteractionSource
            )
        }

        if (showOverlayEnableDialog) {
            AlertDialog(
                onDismissRequest = { showOverlayEnableDialog = false },
                title = {
                    Text(text = overlayEnableDialogTitle)
                },
                text = {
                    Text(text = overlayEnableDialogMessage)
                },
                dismissButton = {
                    TextButton(onClick = { showOverlayEnableDialog = false }) {
                        Text(text = overlayEnableDialogDismiss)
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showOverlayEnableDialog = false
                            onNavigateOverlaySettings()
                        }
                    ) {
                        Text(text = overlayEnableDialogConfirm)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeWatchlistPage(
    mode: SearchMode,
    isLoaded: Boolean,
    isRefreshing: Boolean,
    items: List<WatchItem>,
    overlayIds: Set<String>,
    listState: LazyListState,
    quoteRepository: QuoteRepository,
    showOnchainMarketCap: Boolean = false,
    onNavigateSearch: () -> Unit,
    onDismissQuickMenu: () -> Unit,
    onLongPress: (WatchItem, IntOffset) -> Unit,
    onMoveHomeItem: (String, String?) -> Unit,
    onMovePinnedHomeItem: (String, String?) -> Unit,
    onRefresh: () -> Unit
) {
    var displayItems by remember { mutableStateOf(items) }
    var dragState by remember { mutableStateOf<HomeDragState?>(null) }
    val colors = CoinMonitorThemeTokens.colors
    val scope = rememberCoroutineScope()
    val emptyHint = stringResource(
        if (mode == SearchMode.EXCHANGE) {
            R.string.home_empty_exchange_hint
        } else {
            R.string.home_empty_onchain_hint
        }
    )

    LaunchedEffect(items) {
        if (dragState == null) {
            displayItems = items
        }
    }

    PullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        isRefreshing = isRefreshing,
        onRefresh = onRefresh
    ) {
        if (!isLoaded) {
            HomeLoadingState()
        } else if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = emptyHint,
                        textAlign = TextAlign.Center,
                        color = colors.secondaryText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(
                        onClick = onNavigateSearch,
                        colors = CoinMonitorComponentDefaults.primaryButtonColors()
                    ) {
                        Text(text = stringResource(R.string.home_add_pair))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    bottom = HomePairActionsContentPadding
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(displayItems, key = { it.id }) { item ->
                    val isDragging = dragState?.itemId == item.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                    ) {
                        WatchItemCard(
                            item = item,
                            modifier = Modifier.fillMaxWidth(),
                            quoteRepository = quoteRepository,
                            showOnchainMarketCap = showOnchainMarketCap,
                            overlaySelected = overlayIds.contains(item.id),
                            dragOffsetY = if (isDragging) {
                                dragState?.dragOffsetY ?: 0f
                            } else {
                                0f
                            },
                            onClick = onDismissQuickMenu,
                            onLongPress = { anchorInRoot ->
                                onLongPress(item, anchorInRoot)
                            },
                            onDragStart = {
                                onDismissQuickMenu()
                                dragState = HomeDragState(
                                    itemId = item.id,
                                    homePinned = item.homePinned
                                )
                            },
                            onDragBy = { dragAmount ->
                                val currentDragState = dragState
                                if (currentDragState != null && currentDragState.itemId == item.id) {
                                    val updatedDragState = currentDragState.copy(
                                        dragOffsetY = currentDragState.dragOffsetY + dragAmount
                                    )
                                    dragState = updatedDragState
                                    maybeMoveDraggedItem(
                                        items = displayItems,
                                        listState = listState,
                                        dragState = updatedDragState
                                    )?.let { result ->
                                        displayItems = result.items
                                        dragState = updatedDragState.copy(
                                            dragOffsetY = result.adjustedDragOffsetY,
                                            didReorder = true
                                        )
                                    }
                                    scrollDraggedItemIntoView(
                                        scope = scope,
                                        listState = listState,
                                        dragState = dragState
                                    )
                                }
                            },
                            onDragEnd = {
                                finalizeDrag(
                                    dragState = dragState,
                                    items = displayItems,
                                    onMoveHomeItem = onMoveHomeItem,
                                    onMovePinnedHomeItem = onMovePinnedHomeItem
                                )
                                dragState = null
                            },
                            onDragCancel = {
                                finalizeDrag(
                                    dragState = dragState,
                                    items = displayItems,
                                    onMoveHomeItem = onMoveHomeItem,
                                    onMovePinnedHomeItem = onMovePinnedHomeItem
                                )
                                dragState = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = CoinMonitorThemeTokens.colors.accent
        )
    }
}

@Composable
private fun HomeQuickActionsMenuContent(
    modifier: Modifier = Modifier,
    overlaySelected: Boolean,
    homePinned: Boolean,
    onToggleOverlay: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors

    Surface(
        modifier = modifier.testTag("home-quick-menu"),
        shape = RoundedCornerShape(14.dp),
        color = colors.cardBackground,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = 0.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeQuickActionItem(
                label = stringResource(R.string.delete),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                },
                onClick = onDelete,
                testTag = "home-quick-action-delete"
            )

            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 18.dp)
                    .background(colors.divider)
            )

            HomeQuickActionItem(
                label = stringResource(
                    if (overlaySelected) {
                        R.string.home_quick_overlay_remove
                    } else {
                        R.string.home_quick_overlay_add
                    }
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                },
                onClick = onToggleOverlay,
                testTag = "home-quick-action-overlay"
            )

            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 18.dp)
                    .background(colors.divider)
            )

            HomeQuickActionItem(
                label = stringResource(
                    if (homePinned) {
                        R.string.home_quick_pin_remove
                    } else {
                        R.string.home_quick_pin_add
                    }
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.VerticalAlignTop,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                },
                onClick = onTogglePin,
                testTag = "home-quick-action-pin"
            )
        }
    }
}

@Composable
private fun HomeQuickActionItem(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    testTag: String? = null
) {
    val colors = CoinMonitorThemeTokens.colors

    Row(
        modifier = Modifier
            .then(
                if (testTag == null) {
                    Modifier
                } else {
                    Modifier.testTag(testTag)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.alpha(0.88f)) {
            icon()
        }
        Text(
            text = label,
            color = colors.primaryText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun HomeQuickActionsOverlay(
    quickMenuState: HomeQuickMenuState,
    screenWidthPx: Int,
    overlaySelected: Boolean,
    homePinned: Boolean,
    onDismiss: () -> Unit,
    onToggleOverlay: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    dismissInteractionSource: MutableInteractionSource
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var menuSize by remember(quickMenuState.itemId, overlaySelected, homePinned) { mutableStateOf(IntSize.Zero) }
    var animateIn by remember(quickMenuState.itemId, quickMenuState.anchorInRoot) { mutableStateOf(false) }
    val menuReady = menuSize.width > 0 && menuSize.height > 0
    val menuWidthPx = menuSize.width
    val horizontalPaddingPx = with(density) { 12.dp.roundToPx() }
    val verticalLiftPx = with(density) { 56.dp.roundToPx() }
    val hiddenTranslationYPx = with(density) { 10.dp.toPx() }
    val resolvedX = if (menuReady) {
        (quickMenuState.anchorInRoot.x - (menuWidthPx / 2)).coerceIn(
            horizontalPaddingPx,
            (screenWidthPx - menuWidthPx - horizontalPaddingPx).coerceAtLeast(horizontalPaddingPx)
        )
    } else {
        0
    }
    val resolvedY = if (menuReady) {
        (quickMenuState.anchorInRoot.y - verticalLiftPx).coerceAtLeast(horizontalPaddingPx)
    } else {
        0
    }
    val menuVisible = menuReady && animateIn
    val menuAlpha by animateFloatAsState(
        targetValue = if (menuVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "homeQuickMenuAlpha"
    )
    val menuScale by animateFloatAsState(
        targetValue = if (menuVisible) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 720f),
        label = "homeQuickMenuScale"
    )
    val menuTranslationY by animateFloatAsState(
        targetValue = if (menuVisible) 0f else hiddenTranslationYPx,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "homeQuickMenuTranslationY"
    )

    LaunchedEffect(menuReady) {
        if (menuReady) {
            animateIn = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = dismissInteractionSource,
                indication = null,
                onClick = onDismiss
            )
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(resolvedX, resolvedY) }
                .graphicsLayer {
                    alpha = menuAlpha
                    scaleX = menuScale
                    scaleY = menuScale
                    translationY = menuTranslationY
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
        ) {
            // 作用：菜单先完成测量，再从手指附近做一次轻量弹出，避免定位和动画互相干扰。
            HomeQuickActionsMenuContent(
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    menuSize = coordinates.size
                },
                overlaySelected = overlaySelected,
                homePinned = homePinned,
                onToggleOverlay = onToggleOverlay,
                onDelete = onDelete,
                onTogglePin = onTogglePin
            )
        }
    }
}


private data class HomeDragReorderResult(
    val items: List<WatchItem>,
    val adjustedDragOffsetY: Float
)

private fun maybeMoveDraggedItem(
    items: List<WatchItem>,
    listState: LazyListState,
    dragState: HomeDragState
): HomeDragReorderResult? {
    val draggedInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == dragState.itemId }
        ?: return null
    val draggedIndex = items.indexOfFirst { it.id == dragState.itemId }
    if (draggedIndex < 0) return null
    val draggedMidY = draggedInfo.offset + dragState.dragOffsetY + (draggedInfo.size / 2f)
    val targetInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { itemInfo ->
        if (itemInfo.key == dragState.itemId) {
            return@firstOrNull false
        }
        val candidate = items.firstOrNull { it.id == itemInfo.key } ?: return@firstOrNull false
        candidate.homePinned == dragState.homePinned &&
            draggedMidY in itemInfo.offset.toFloat()..(itemInfo.offset + itemInfo.size).toFloat()
    } ?: return null
    val targetIndex = items.indexOfFirst { it.id == targetInfo.key }
    if (targetIndex < 0 || targetIndex == draggedIndex) return null
    val reorderedItems = items.toMutableList().apply {
        add(targetIndex, removeAt(draggedIndex))
    }
    return HomeDragReorderResult(
        items = reorderedItems,
        adjustedDragOffsetY = dragState.dragOffsetY - (targetInfo.offset - draggedInfo.offset).toFloat()
    )
}

private fun finalizeDrag(
    dragState: HomeDragState?,
    items: List<WatchItem>,
    onMoveHomeItem: (String, String?) -> Unit,
    onMovePinnedHomeItem: (String, String?) -> Unit
) {
    val currentDragState = dragState ?: return
    if (!currentDragState.didReorder) return
    val currentIndex = items.indexOfFirst { it.id == currentDragState.itemId }
    if (currentIndex < 0) return
    val targetBeforeId = items
        .drop(currentIndex + 1)
        .firstOrNull { it.homePinned == currentDragState.homePinned }
        ?.id
    if (currentDragState.homePinned) {
        onMovePinnedHomeItem(currentDragState.itemId, targetBeforeId)
    } else {
        onMoveHomeItem(currentDragState.itemId, targetBeforeId)
    }
}

private fun scrollDraggedItemIntoView(
    scope: CoroutineScope,
    listState: LazyListState,
    dragState: HomeDragState?
) {
    val currentDragState = dragState ?: return
    val draggedInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == currentDragState.itemId }
        ?: return
    val viewportStart = listState.layoutInfo.viewportStartOffset.toFloat()
    val viewportEnd = listState.layoutInfo.viewportEndOffset.toFloat()
    val edgeThreshold = 88f
    val top = draggedInfo.offset + currentDragState.dragOffsetY
    val bottom = top + draggedInfo.size
    val scrollBy = when {
        bottom > viewportEnd - edgeThreshold -> (bottom - (viewportEnd - edgeThreshold)).coerceAtMost(52f)
        top < viewportStart + edgeThreshold -> (top - (viewportStart + edgeThreshold)).coerceAtLeast(-52f)
        else -> 0f
    }
    if (scrollBy == 0f) return
    scope.launch {
        listState.scrollBy(scrollBy)
    }
}
