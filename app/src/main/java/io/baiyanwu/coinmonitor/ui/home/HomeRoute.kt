package io.baiyanwu.coinmonitor.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.ui.components.WatchItemCard
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.R

private data class HomeQuickMenuState(
    val itemId: String,
    val anchorInRoot: IntOffset
)

@Composable
fun HomeRoute(
    container: AppContainer,
    contentBottomInset: Dp = 0.dp,
    onNavigateSearch: () -> Unit,
    onNavigateOverlaySettings: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        contentBottomInset = contentBottomInset,
        onNavigateSearch = onNavigateSearch,
        onNavigateOverlaySettings = onNavigateOverlaySettings,
        onRemoveWatchItem = viewModel::removeWatchItem,
        onToggleOverlay = viewModel::toggleOverlay,
        onRefresh = viewModel::refreshNow
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    contentBottomInset: Dp,
    onNavigateSearch: () -> Unit,
    onNavigateOverlaySettings: () -> Unit,
    onRemoveWatchItem: (String) -> Unit,
    onToggleOverlay: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var quickMenuState by remember { mutableStateOf<HomeQuickMenuState?>(null) }
    var showOverlayEnableDialog by remember { mutableStateOf(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val colors = CoinMonitorThemeTokens.colors
    val dismissInteractionSource = remember { MutableInteractionSource() }
    val overlayEnableDialogTitle = stringResource(R.string.home_overlay_enable_dialog_title)
    val overlayEnableDialogMessage = stringResource(R.string.home_overlay_enable_dialog_message)
    val overlayEnableDialogDismiss = stringResource(R.string.common_cancel)
    val overlayEnableDialogConfirm = stringResource(R.string.home_overlay_enable_dialog_confirm)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .padding(bottom = contentBottomInset)
            .onSizeChanged { rootSize = it }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                SearchEntryButton(onClick = onNavigateSearch)
            }

            PullToRefreshBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh
            ) {
                if (!state.isLoaded) {
                    HomeLoadingState()
                } else if (state.items.isEmpty()) {
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
                                text = stringResource(R.string.home_empty_hint),
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
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            end = 0.dp,
                            top = 0.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                WatchItemCard(
                                    item = item,
                                    overlaySelected = state.overlayIds.contains(item.id),
                                    onClick = {
                                        if (quickMenuState?.itemId == item.id) {
                                            quickMenuState = null
                                        }
                                    },
                                    onLongPress = { anchorInRoot ->
                                        if (quickMenuState?.itemId == item.id) {
                                            quickMenuState = null
                                        } else {
                                            quickMenuState = HomeQuickMenuState(
                                                itemId = item.id,
                                                anchorInRoot = anchorInRoot
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        quickMenuState?.let { menuState ->
            HomeQuickActionsOverlay(
                quickMenuState = menuState,
                screenWidthPx = rootSize.width,
                overlaySelected = state.overlayIds.contains(menuState.itemId),
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
    onToggleOverlay: () -> Unit,
    onDelete: () -> Unit
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
    onDismiss: () -> Unit,
    onToggleOverlay: () -> Unit,
    onDelete: () -> Unit,
    dismissInteractionSource: MutableInteractionSource
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var menuSize by remember(quickMenuState.itemId, overlaySelected) { mutableStateOf(IntSize.Zero) }
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
                onToggleOverlay = onToggleOverlay,
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun SearchEntryButton(onClick: () -> Unit) {
    val colors = CoinMonitorThemeTokens.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.fabContainer,
        tonalElevation = 0.dp,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.home_open_search),
                tint = colors.fabContent
            )
        }
    }
}
