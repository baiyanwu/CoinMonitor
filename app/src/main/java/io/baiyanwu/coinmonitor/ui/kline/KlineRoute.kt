package io.baiyanwu.coinmonitor.ui.kline
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.AiAnalysisOption
import io.baiyanwu.coinmonitor.domain.model.AiChatRole
import io.baiyanwu.coinmonitor.domain.model.KlineIndicator
import io.baiyanwu.coinmonitor.domain.model.KlineInterval
import io.baiyanwu.coinmonitor.domain.model.KlineSource
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.overlay.QuoteFormatter
import io.baiyanwu.coinmonitor.ui.components.TopBarCircleActionButton
import io.baiyanwu.coinmonitor.ui.resolveChangeColor
import io.baiyanwu.coinmonitor.ui.resolveLivePriceColor
import io.baiyanwu.coinmonitor.ui.kline.chart.KlineChartHostView
import io.baiyanwu.coinmonitor.ui.kline.chart.KlineChartRenderModel
import io.baiyanwu.coinmonitor.ui.kline.chart.KlineChartStyleDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

private val KLINE_HEADER_HEIGHT = 38.dp

/**
 * K 线页路由入口。
 *
 * 当前页面以 AI 聊天为主，K 线和周期指标作为顶部可折叠参考面板展示。
 */
@Composable
fun KlineRoute(
    container: AppContainer,
    chartHostView: KlineChartHostView,
    contentTopInset: androidx.compose.ui.unit.Dp = 0.dp,
    contentBottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    onOpenSearch: () -> Unit,
    onOpenIndicatorSettings: () -> Unit
) {
    val viewModel: KlineViewModel = viewModel(factory = KlineViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    KlineScreen(
        state = state,
        onSelectItem = viewModel::selectItem,
        onSelectInterval = viewModel::setInterval,
        onSelectMainIndicator = viewModel::setMainIndicator,
        onSelectSubIndicator = viewModel::setSubIndicator,
        onOpenSearch = onOpenSearch,
        onOpenIndicatorSettings = onOpenIndicatorSettings,
        chartHostView = chartHostView,
        contentTopInset = contentTopInset,
        contentBottomInset = contentBottomInset,
        onRetry = viewModel::retry,
        onSendMessage = viewModel::sendMessage,
        onStopGeneration = viewModel::stopAiGeneration
    )
}

@Composable
private fun KlineScreen(
    state: KlineUiState,
    onSelectItem: (String) -> Unit,
    onSelectInterval: (KlineInterval) -> Unit,
    onSelectMainIndicator: (KlineIndicator) -> Unit,
    onSelectSubIndicator: (KlineIndicator) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenIndicatorSettings: () -> Unit,
    chartHostView: KlineChartHostView,
    contentTopInset: androidx.compose.ui.unit.Dp,
    contentBottomInset: androidx.compose.ui.unit.Dp,
    onRetry: () -> Unit,
    onSendMessage: (String, Set<AiAnalysisOption>) -> Unit,
    onStopGeneration: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    var klineExpanded by rememberSaveable { mutableStateOf(true) }
    var pairMenuExpanded by remember { mutableStateOf(false) }
    var composerHeightPx by remember { mutableIntStateOf(0) }
    var topOverlayHeightPx by remember { mutableIntStateOf(0) }
    var stableTopOverlayHeightPx by rememberSaveable { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val composerHeight = with(density) { composerHeightPx.toDp() }
    val imeBottomInset = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val bottomDockInset = if (imeBottomInset > contentBottomInset) imeBottomInset else contentBottomInset
    val stableTopInset = with(density) { stableTopOverlayHeightPx.toDp() }

    /**
     * 顶部悬浮层展开时记住最大高度，聊天列表始终按稳定高度留白，
     * 避免收起/展开 K 线时把中间消息区整体顶着滚动。
     */
    LaunchedEffect(klineExpanded, topOverlayHeightPx) {
        if (klineExpanded && topOverlayHeightPx > 0) {
            stableTopOverlayHeightPx = maxOf(stableTopOverlayHeightPx, topOverlayHeightPx)
        } else if (stableTopOverlayHeightPx == 0 && topOverlayHeightPx > 0) {
            stableTopOverlayHeightPx = topOverlayHeightPx
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        KlineAiChatMessages(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .background(colors.pageBackground),
            topInset = stableTopInset,
            bottomInset = composerHeight + bottomDockInset
        )

        KlineTopOverlay(
            state = state,
            expanded = klineExpanded,
            pairMenuExpanded = pairMenuExpanded,
            chartHostView = chartHostView,
            contentTopInset = contentTopInset,
            onExpandedChange = { klineExpanded = it },
            onPairMenuExpandedChange = { pairMenuExpanded = it },
            onSelectItem = onSelectItem,
            onSelectInterval = onSelectInterval,
            onSelectMainIndicator = onSelectMainIndicator,
            onSelectSubIndicator = onSelectSubIndicator,
            onOpenSearch = onOpenSearch,
            onOpenIndicatorSettings = onOpenIndicatorSettings,
            onRetry = onRetry,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onGloballyPositioned { coordinates ->
                    topOverlayHeightPx = coordinates.size.height
                }
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomDockInset)
        ) {
            KlineChatComposerBar(
                state = state,
                onSendMessage = onSendMessage,
                onStopGeneration = onStopGeneration,
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    composerHeightPx = coordinates.size.height
                }
            )
        }
    }
}

@Composable
private fun KlineTopOverlay(
    state: KlineUiState,
    expanded: Boolean,
    pairMenuExpanded: Boolean,
    chartHostView: KlineChartHostView,
    contentTopInset: androidx.compose.ui.unit.Dp,
    onExpandedChange: (Boolean) -> Unit,
    onPairMenuExpandedChange: (Boolean) -> Unit,
    onSelectItem: (String) -> Unit,
    onSelectInterval: (KlineInterval) -> Unit,
    onSelectMainIndicator: (KlineIndicator) -> Unit,
    onSelectSubIndicator: (KlineIndicator) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenIndicatorSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = CoinMonitorThemeTokens.colors
    val configuration = LocalConfiguration.current
    val chartHeight = configuration.screenHeightDp.dp * 0.24f

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.pageBackground,
        shadowElevation = 0.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = contentTopInset)
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                KlineOverlayHeader(
                    selectedItem = state.selectedItem,
                    sourceTitle = state.selectedSource?.title.orEmpty(),
                    expanded = expanded,
                    pairMenuExpanded = pairMenuExpanded,
                    onExpandedChange = onExpandedChange,
                    onPairMenuExpandedChange = onPairMenuExpandedChange,
                    onOpenSearch = onOpenSearch,
                    onSelectItem = onSelectItem,
                    availableItems = state.availableItems
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = 500f
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = 500f
                    )
                ) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                        CompactIntervalRow(
                            selected = state.selectedInterval,
                            onSelect = onSelectInterval
                        )
                    }
                    Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                        CompactIndicatorRow(
                            mainSelected = state.selectedMainIndicator,
                            onSelectMain = onSelectMainIndicator,
                            subSelected = state.selectedSubIndicator,
                            onSelectSub = onSelectSubIndicator,
                            onOpenIndicatorSettings = onOpenIndicatorSettings
                        )
                    }
                    KlineChartOverlayCard(
                        state = state,
                        chartHostView = chartHostView,
                        chartHeight = chartHeight,
                        onRetry = onRetry
                    )
                }
            }
        }
    }
}

@Composable
private fun KlineOverlayHeader(
    selectedItem: WatchItem?,
    sourceTitle: String,
    expanded: Boolean,
    pairMenuExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPairMenuExpandedChange: (Boolean) -> Unit,
    onOpenSearch: () -> Unit,
    onSelectItem: (String) -> Unit,
    availableItems: List<io.baiyanwu.coinmonitor.domain.model.WatchItem>
) {
    val colors = CoinMonitorThemeTokens.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = colors.fabContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KLINE_HEADER_HEIGHT)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactPairSelector(
                    modifier = Modifier.weight(1f),
                    item = selectedItem,
                    badge = sourceTitle,
                    expanded = pairMenuExpanded,
                    onExpandedChange = onPairMenuExpandedChange
                ) {
                    availableItems.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                PairMenuLabel(
                                    symbol = item.symbol,
                                    sourceTitle = KlineSource.fromWatchItem(item).title
                                )
                            },
                            onClick = {
                                onPairMenuExpandedChange(false)
                                onSelectItem(item.id)
                            }
                        )
                    }
                }

                Surface(
                    onClick = onOpenSearch,
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.open_search),
                            tint = colors.fabContent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        TopBarCircleActionButton(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (expanded) stringResource(R.string.common_close) else stringResource(R.string.kline_chat),
            onClick = { onExpandedChange(!expanded) }
        )
    }
}

@Composable
private fun KlineChartOverlayCard(
    state: KlineUiState,
    chartHostView: KlineChartHostView,
    chartHeight: androidx.compose.ui.unit.Dp,
    onRetry: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(chartHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.cardBackground)
        ) {
            when {
                state.availableItems.isEmpty() -> EmptyStateText(R.string.kline_empty_watchlist)
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.errorMessage != null -> ErrorState(
                    message = state.errorMessage,
                    onRetry = onRetry
                )
                state.selectedItem == null -> EmptyStateText(R.string.kline_empty_watchlist)
                state.candles.isEmpty() -> EmptyStateText(R.string.kline_loading)
                else -> {
                    KlineChartSection(
                        state = state,
                        chartHostView = chartHostView,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Text(
                text = stringResource(R.string.kline_attribution),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clickable { uriHandler.openUri("https://www.tradingview.com/") },
                style = MaterialTheme.typography.labelSmall,
                color = colors.fabContent.copy(alpha = 0.58f)
            )
        }
    }
}

/**
 * 组装统一渲染模型并把它交给图表宿主视图。
 */
@Composable
private fun KlineChartSection(
    state: KlineUiState,
    chartHostView: KlineChartHostView,
    modifier: Modifier = Modifier
) {
    val isDarkChart = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = remember(isDarkChart) {
        if (isDarkChart) KlineChartStyleDefaults.darkPalette else KlineChartStyleDefaults.lightPalette
    }
    val renderModel = remember(
        state.candles,
        state.selectedMainIndicator,
        state.selectedSubIndicator,
        state.indicatorSettings,
        palette
    ) {
        KlineChartRenderModel(
            candles = state.candles,
            mainIndicator = state.selectedMainIndicator,
            subIndicator = state.selectedSubIndicator,
            indicatorSettings = state.indicatorSettings,
            palette = palette,
            strokeStyle = KlineChartStyleDefaults.strokeStyle
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { chartHostView },
        update = { hostView -> hostView.render(renderModel) }
    )
}

@Composable
private fun CompactPairSelector(
    modifier: Modifier = Modifier,
    item: WatchItem?,
    badge: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val symbol = item?.symbol.orEmpty().ifBlank { "--" }
    val priceText = QuoteFormatter.formatPrice(item?.lastPrice)
    val changeText = QuoteFormatter.formatChange(item?.change24hPercent)
    val priceColor = item?.resolveLivePriceColor(colors, colors.fabContent) ?: colors.fabContent
    val changeColor = item?.resolveChangeColor(colors, colors.secondaryText) ?: colors.secondaryText
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clickable { onExpandedChange(true) }
                .padding(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = symbol,
                    color = colors.fabContent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                SourceMiniTag(sourceTitle = badge)
                Text(
                    text = priceText,
                    color = priceColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = changeText,
                    color = changeColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            content()
        }
    }
}

@Composable
private fun PairMenuLabel(
    symbol: String,
    sourceTitle: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        SourceMiniTag(sourceTitle = sourceTitle)
    }
}

@Composable
private fun SourceMiniTag(sourceTitle: String) {
    val colors = CoinMonitorThemeTokens.colors
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
        color = colors.accent.copy(alpha = 0.16f)
    ) {
        Text(
            text = sourceTitle.uppercase(),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = colors.accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CompactIntervalRow(
    selected: KlineInterval,
    onSelect: (KlineInterval) -> Unit
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }
    val extendedInterval = selected.takeIf { it in EXTENDED_INTERVALS }
    val moreIntervalLabel = stringResource(R.string.kline_more_intervals)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 20.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PRIMARY_INTERVALS.forEach { interval ->
            CompactTextToggle(
                text = interval.label,
                selected = interval == selected,
                onClick = { onSelect(interval) }
            )
        }
        Box(
            modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically)
        ) {
            CompactTextToggle(
                text = extendedInterval?.label ?: moreIntervalLabel,
                selected = extendedInterval != null,
                onClick = { moreMenuExpanded = true }
            )
            DropdownMenu(
                expanded = moreMenuExpanded,
                onDismissRequest = { moreMenuExpanded = false }
            ) {
                EXTENDED_INTERVALS.forEach { interval ->
                    DropdownMenuItem(
                        text = { Text(interval.label) },
                        onClick = {
                            moreMenuExpanded = false
                            onSelect(interval)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactIndicatorRow(
    mainSelected: KlineIndicator,
    onSelectMain: (KlineIndicator) -> Unit,
    subSelected: KlineIndicator,
    onSelectSub: (KlineIndicator) -> Unit,
    onOpenIndicatorSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(KlineIndicator.MA, KlineIndicator.EMA, KlineIndicator.BOLL).forEach { indicator ->
                CompactTextToggle(
                    text = indicator.label,
                    selected = indicator == mainSelected,
                    onClick = { onSelectMain(indicator) }
                )
            }
            listOf(KlineIndicator.VOL, KlineIndicator.MACD, KlineIndicator.RSI, KlineIndicator.KDJ).forEach { indicator ->
                CompactTextToggle(
                    text = indicator.label,
                    selected = indicator == subSelected,
                    onClick = { onSelectSub(indicator) }
                )
            }
        }

        Surface(
            onClick = onOpenIndicatorSettings,
            shape = CircleShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.kline_indicator_settings_title),
                    tint = CoinMonitorThemeTokens.colors.primaryText,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactTextToggle(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    Text(
        text = text,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 0.dp),
        color = if (selected) colors.accent else colors.primaryText,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
    )
}

@Composable
private fun KlineAiChatMessages(
    state: KlineUiState,
    modifier: Modifier = Modifier,
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val colors = CoinMonitorThemeTokens.colors
    LazyColumn(
        modifier = modifier
            .background(colors.pageBackground)
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(
            top = topInset + 6.dp,
            bottom = bottomInset + 4.dp
        ),
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "ai-ready-tip") {
            if (!state.aiReady) {
                Text(
                    text = stringResource(R.string.kline_ai_not_ready),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        items(
            items = state.chatMessages.asReversed(),
            key = { message -> message.id }
        ) { message ->
            val isStreamingPlaceholder = state.isAiSending &&
                message.role == AiChatRole.ASSISTANT &&
                message.content.isBlank()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (message.role == AiChatRole.USER) Arrangement.End else Arrangement.Start
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 320.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (message.role == AiChatRole.USER) colors.fabContainer else colors.cardBackground
                ) {
                    if (isStreamingPlaceholder) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = stringResource(R.string.kline_ai_thinking),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        Text(
                            text = message.content,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (message.role == AiChatRole.USER) colors.fabContent else colors.primaryText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KlineChatComposerBar(
    state: KlineUiState,
    onSendMessage: (String, Set<AiAnalysisOption>) -> Unit,
    onStopGeneration: () -> Unit,
    modifier: Modifier = Modifier
) {
    var input by rememberSaveable { mutableStateOf("") }
    var selectedOptions by rememberSaveable {
        mutableStateOf(AiAnalysisOption.defaultSelection.map { it.name }.toSet())
    }
    val resolvedOptions = selectedOptions.map(AiAnalysisOption::valueOf).toSet()
    val colors = CoinMonitorThemeTokens.colors
    val inputTextStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.pageBackground)
            .padding(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AiAnalysisOption.entries.forEach { option ->
                ChatSelectionPill(
                    text = stringResource(option.labelResId()),
                    selected = option.name in selectedOptions,
                    enabled = !state.isAiSending,
                    onClick = {
                        selectedOptions = selectedOptions.toMutableSet().apply {
                            if (!add(option.name)) remove(option.name)
                        }.toSet()
                    }
                )
            }
        }
        if (resolvedOptions.isEmpty()) {
            Text(
                text = stringResource(R.string.kline_ai_analysis_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.kline_chat_hint),
                    style = inputTextStyle
                )
            },
            textStyle = inputTextStyle,
            singleLine = false,
            minLines = 1,
            maxLines = 3,
            keyboardActions = KeyboardActions(
                onSend = {
                    if (!state.isAiSending && input.isNotBlank() && state.aiReady && resolvedOptions.isNotEmpty()) {
                        onSendMessage(input, resolvedOptions)
                        input = ""
                    }
                }
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            trailingIcon = {
                if (state.isAiSending) {
                    IconButton(onClick = onStopGeneration) {
                        Icon(
                            imageVector = Icons.Rounded.StopCircle,
                            contentDescription = stringResource(R.string.kline_chat_stop),
                            tint = colors.secondaryText
                        )
                    }
                } else {
                    val sendEnabled = input.isNotBlank() && state.aiReady && resolvedOptions.isNotEmpty()
                    IconButton(
                        onClick = {
                            onSendMessage(input, resolvedOptions)
                            input = ""
                        },
                        enabled = sendEnabled
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.kline_chat_send),
                            tint = if (sendEnabled) colors.accent else colors.secondaryText
                        )
                    }
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
        if (state.isAiSending) {
            Text(
                text = stringResource(R.string.kline_ai_thinking),
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText
            )
        }
    }
}

@Composable
private fun ChatSelectionPill(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) colors.fabContainer else colors.cardBackground
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = when {
                selected -> colors.fabContent
                enabled -> colors.primaryText
                else -> colors.secondaryText
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun AiAnalysisOption.labelResId(): Int {
    return when (this) {
        AiAnalysisOption.INDICATOR_INFO -> R.string.kline_ai_option_indicator_info
        AiAnalysisOption.BINANCE_ANNOUNCEMENT -> R.string.kline_ai_option_binance_announcement
        AiAnalysisOption.OKX_ANNOUNCEMENT -> R.string.kline_ai_option_okx_announcement
        AiAnalysisOption.PROJECT_INFO -> R.string.kline_ai_option_project_info
    }
}

private val PRIMARY_INTERVALS = listOf(
    KlineInterval.ONE_MINUTE,
    KlineInterval.FIVE_MINUTES,
    KlineInterval.FIFTEEN_MINUTES,
    KlineInterval.ONE_HOUR,
    KlineInterval.FOUR_HOURS,
    KlineInterval.ONE_DAY
)

private val EXTENDED_INTERVALS = listOf(
    KlineInterval.THREE_DAYS,
    KlineInterval.ONE_WEEK,
    KlineInterval.ONE_MONTH
)

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.kline_retry))
            }
        }
    }
}

@Composable
private fun EmptyStateText(textRes: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
