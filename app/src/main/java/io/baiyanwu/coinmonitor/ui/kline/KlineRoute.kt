package io.baiyanwu.coinmonitor.ui.kline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.AiChatRole
import io.baiyanwu.coinmonitor.domain.model.KlineIndicator
import io.baiyanwu.coinmonitor.domain.model.KlineInterval
import io.baiyanwu.coinmonitor.domain.model.KlineSource
import io.baiyanwu.coinmonitor.ui.kline.chart.KlineChartHostView
import io.baiyanwu.coinmonitor.ui.kline.chart.KlineChartPalette
import io.baiyanwu.coinmonitor.ui.kline.chart.KlineChartRenderModel
import io.baiyanwu.coinmonitor.ui.kline.chart.KlineChartStyleDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

/**
 * K 线页路由入口。
 *
 * 页面层只负责组装状态、交互和统一渲染模型，
 * 具体图表库相关逻辑全部收口到 `ui.kline.chart` 包中。
 */
@Composable
fun KlineRoute(
    container: AppContainer,
    chartHostView: KlineChartHostView,
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
        onRefresh = viewModel::refreshNow,
        onRetry = viewModel::retry,
        onSendMessage = viewModel::sendMessage
    )
}

/**
 * K 线页主界面。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val uriHandler = LocalUriHandler.current
    var pairMenuExpanded by remember { mutableStateOf(false) }
    var chatDialogVisible by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PairSelectorField(
                    value = state.selectedItem?.symbol.orEmpty(),
                    badge = state.selectedSource?.title.orEmpty(),
                    enabled = false,
                    expanded = pairMenuExpanded,
                    onExpandedChange = { pairMenuExpanded = it }
                ) {
                    state.availableItems.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                PairMenuLabel(
                                    symbol = item.symbol,
                                    sourceTitle = KlineSource.fromWatchItem(item).title
                                )
                            },
                            onClick = {
                                pairMenuExpanded = false
                                onSelectItem(item.id)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    onClick = onOpenSearch,
                    shape = CircleShape,
                    color = colors.fabContainer,
                    tonalElevation = 0.dp,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.open_search),
                            tint = colors.fabContent
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                IntervalRow(
                    selected = state.selectedInterval,
                    onSelect = onSelectInterval
                )

                Spacer(modifier = Modifier.height(8.dp))

                IndicatorRow(
                    mainSelected = state.selectedMainIndicator,
                    onSelectMain = onSelectMainIndicator,
                    subSelected = state.selectedSubIndicator,
                    onSelectSub = onSelectSubIndicator,
                    onOpenIndicatorSettings = onOpenIndicatorSettings
                )

                Spacer(modifier = Modifier.height(8.dp))

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .height(460.dp),
                    shape = RoundedCornerShape(24.dp)
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
                    }
                }

                state.selectedItem?.let { item ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = colors.cardBackground
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.symbol,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(
                                    R.string.kline_summary_meta,
                                    state.selectedSource?.title.orEmpty(),
                                    state.selectedInterval.label,
                                    state.selectedMainIndicator.label,
                                    state.selectedSubIndicator.label
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                            Text(
                                text = stringResource(
                                    R.string.kline_summary_quote,
                                    item.lastPrice?.toString() ?: "--",
                                    item.change24hPercent?.toString() ?: "--"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                            Text(
                                text = stringResource(R.string.kline_attribution),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri("https://www.tradingview.com/")
                                }
                            )
                            if (state.isRefreshing) {
                                Text(
                                    text = stringResource(R.string.kline_refreshing),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(96.dp))
            }
        }
        ExtendedFloatingActionButton(
            onClick = { chatDialogVisible = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null
                )
            },
            text = { Text(stringResource(R.string.kline_chat)) },
            shape = CircleShape
        )
    }

    if (chatDialogVisible) {
        KlineChatDialog(
            state = state,
            onDismiss = { chatDialogVisible = false },
            onSendMessage = onSendMessage
        )
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
        if (isDarkChart) {
            KlineChartStyleDefaults.darkPalette
        } else {
            KlineChartStyleDefaults.lightPalette
        }
    }
    val renderModel = remember(state.candles, state.selectedMainIndicator, state.selectedSubIndicator, state.indicatorSettings, palette) {
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
        update = { hostView ->
            hostView.render(renderModel)
        }
    )
}

/**
 * 顶部币对选择框。
 */
@Composable
private fun PairSelectorField(
    modifier: Modifier = Modifier,
    value: String,
    badge: String,
    enabled: Boolean = true,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    Box {
        Surface(
            modifier = Modifier
                .then(modifier)
                .widthIn(max = 280.dp)
                .height(40.dp)
                // 这里暂时关闭点击展开行为，后续如果要恢复币对下拉切换，只需要把 enabled 改回 true。
                .clickable(enabled = enabled) { onExpandedChange(true) },
            shape = RoundedCornerShape(18.dp),
            color = colors.fabContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value.ifBlank { "--" },
                    color = CoinMonitorThemeTokens.colors.chipSelectedContent,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                SourceMiniTag(sourceTitle = badge)
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

/**
 * 下拉菜单中的币对标签。
 */
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

/**
 * 来源小标签。
 */
@Composable
private fun SourceMiniTag(sourceTitle: String) {
    val colors = CoinMonitorThemeTokens.colors
    val (containerColor, contentColor) = when (sourceTitle) {
        "Alpha" -> colors.cardBackground to colors.primaryText
        else -> colors.cardBackground to colors.primaryText
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Box {
            Text(
                text = sourceTitle.uppercase(),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * 周期选择行。
 */
@Composable
private fun IntervalRow(
    selected: KlineInterval,
    onSelect: (KlineInterval) -> Unit
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }
    val extendedInterval = selected.takeIf { it in EXTENDED_INTERVALS }
    val moreIntervalLabel = stringResource(R.string.kline_more_intervals)
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PRIMARY_INTERVALS.forEach { interval ->
                SelectionPill(
                    text = interval.label,
                    selected = interval == selected,
                    onClick = { onSelect(interval) }
                )
            }
            Box(
                modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically)
            ) {
                SelectionPill(
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
}

/**
 * 主副指标合并行。
 */
@Composable
private fun IndicatorRow(
    mainSelected: KlineIndicator,
    onSelectMain: (KlineIndicator) -> Unit,
    subSelected: KlineIndicator,
    onSelectSub: (KlineIndicator) -> Unit,
    onOpenIndicatorSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(KlineIndicator.MA, KlineIndicator.EMA, KlineIndicator.BOLL).forEach { indicator ->
                SelectionPill(
                    text = indicator.label,
                    selected = indicator == mainSelected,
                    onClick = { onSelectMain(indicator) }
                )
            }

            Box(
                modifier = Modifier
                    .height(16.dp)
                    .widthIn(min = 1.dp, max = 1.dp)
                    .background(CoinMonitorThemeTokens.colors.divider)
            )

            listOf(KlineIndicator.VOL, KlineIndicator.MACD, KlineIndicator.RSI, KlineIndicator.KDJ)
                .forEach { indicator ->
                    SelectionPill(
                        text = indicator.label,
                        selected = indicator == subSelected,
                        onClick = { onSelectSub(indicator) }
                    )
                }
        }

        Surface(
            onClick = onOpenIndicatorSettings,
            shape = CircleShape,
            color = CoinMonitorThemeTokens.colors.fabContainer,
            tonalElevation = 0.dp,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.kline_indicator_settings_title),
                    tint = CoinMonitorThemeTokens.colors.fabContent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 统一轻量选择胶囊。
 */
@Composable
private fun SelectionPill(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    Box(
        modifier = Modifier
            .then(modifier)
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) {
                    colors.fabContainer
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) colors.fabContent else colors.primaryText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
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

/**
 * 错误态。
 */
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

/**
 * 空态。
 */
@Composable
private fun EmptyStateText(textRes: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * AI 聊天弹窗。
 */
@Composable
private fun KlineChatDialog(
    state: KlineUiState,
    onDismiss: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.kline_chat))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!state.aiReady) {
                    Text(
                        text = stringResource(R.string.kline_ai_not_ready),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.chatMessages) { message ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (message.role == AiChatRole.USER) {
                                CoinMonitorThemeTokens.colors.heroBackground
                            } else {
                                CoinMonitorThemeTokens.colors.cardBackground
                            }
                        ) {
                            Text(
                                text = message.content,
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.kline_chat_hint)) }
                )
                if (state.isAiSending) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.kline_ai_thinking),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSendMessage(input)
                    input = ""
                },
                enabled = input.isNotBlank() && state.aiReady && !state.isAiSending
            ) {
                Text(stringResource(R.string.kline_chat_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}
