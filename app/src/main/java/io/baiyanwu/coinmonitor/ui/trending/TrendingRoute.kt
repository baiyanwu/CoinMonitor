package io.baiyanwu.coinmonitor.ui.trending

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.data.network.TrendingFailure
import io.baiyanwu.coinmonitor.overlay.QuoteFormatter
import io.baiyanwu.coinmonitor.domain.model.*
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorIcons
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorPageTopBar
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorSegmentedTab
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorSegmentedTabs
import io.baiyanwu.coinmonitor.ui.components.CoinMonitorSmallIconButton
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorDesignTheme
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.Locale

@Composable
fun TrendingRoute(container: AppContainer, contentTopInset: Dp, contentBottomInset: Dp, onOpenApiSettings: () -> Unit, onOpenBrowser: (String) -> Unit) {
    val model: TrendingViewModel = viewModel(factory = TrendingViewModel.factory(container))
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, model) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.start()
            try { awaitCancellation() } finally { model.stop() }
        }
    }
    TrendingScreen(state, contentTopInset, contentBottomInset, model::selectBoard, model::toggleSource,
        model::updateOptions, model::updateDisplay, model::refresh, onOpenApiSettings, onOpenBrowser)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrendingScreen(
    state: TrendingUiState, contentTopInset: Dp, contentBottomInset: Dp,
    onSelectBoard: (TrendingBoard) -> Unit, onToggleSource: (TrendingSource) -> Unit,
    onOptions: (TrendingSource, TrendingOptions) -> Unit, onDisplay: (TrendingBoard, TrendingOptions) -> Unit,
    onRefresh: () -> Unit, onOpenApiSettings: () -> Unit, onOpenBrowser: (String) -> Unit
) {
    CoinMonitorDesignTheme {
        val design = CoinMonitorThemeTokens.design
        val pager = rememberPagerState(initialPage = state.board.ordinal, pageCount = { TrendingBoard.entries.size })
        val scope = rememberCoroutineScope()
        var selectedBoard by rememberSaveable { mutableStateOf(state.board) }
        var pageSwitchJob by remember { mutableStateOf<Job?>(null) }
        var clickTarget by remember { mutableStateOf<TrendingBoard?>(null) }
        var switchRequest by remember { mutableStateOf(0L) }
        val boardSettled = clickTarget == null && !pager.isScrollInProgress && state.board == selectedBoard
        val clipboard = LocalClipboardManager.current
        val context = LocalContext.current
        val copiedLabel = stringResource(R.string.trending_address_copied)
        var showFilters by rememberSaveable { mutableStateOf(false) }
        var token by remember { mutableStateOf<TrendingToken?>(null) }
        LaunchedEffect(pager.settledPage, pager.isScrollInProgress, clickTarget) {
            if (clickTarget == null && !pager.isScrollInProgress) {
                // Start/cancel ranking demand only after the transition has settled.
                selectedBoard = TrendingBoard.entries[pager.settledPage]
                onSelectBoard(selectedBoard)
            }
        }
        Column(
            Modifier.fillMaxSize().background(design.colors.pageBackground)
                .padding(top = contentTopInset, bottom = contentBottomInset)
        ) {
            CoinMonitorPageTopBar(stringResource(R.string.tab_trending)) {
                CoinMonitorSmallIconButton(
                    CoinMonitorIcons.Refresh, stringResource(R.string.trending_refresh), onRefresh,
                    containerColor = design.colors.surface,
                    enabled = !state.cacheRestoring && state.demand.any { source ->
                        source in state.ready && state.snapshots[source]?.loading != true &&
                            (state.snapshots[source]?.retryAt ?: 0) <= state.now
                    }
                )
                CoinMonitorSmallIconButton(
                    CoinMonitorIcons.Filters, stringResource(R.string.trending_filters), onClick = { showFilters = true },
                    containerColor = design.colors.surface
                )
            }
            CoinMonitorSegmentedTabs(
                tabs = TrendingBoard.entries.map { CoinMonitorSegmentedTab(it.name, boardTabTitle(it)) },
                selectedId = TrendingBoard.entries[pager.currentPage].name,
                onSelect = { id ->
                    val target = TrendingBoard.valueOf(id)
                    val request = ++switchRequest
                    pageSwitchJob?.cancel()
                    clickTarget = target
                    selectedBoard = target
                    pageSwitchJob = scope.launch {
                        try {
                            pager.animateScrollToPage(
                                target.ordinal,
                                animationSpec = tween(design.motion.pageSwitchDurationMillis, easing = FastOutSlowInEasing)
                            )
                        } finally {
                            if (switchRequest == request) clickTarget = null
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = design.spacing.pageHorizontal),
                compactTrack = true,
                indicatorPosition = { pager.currentPage + pager.currentPageOffsetFraction }
            )
            // Warm adjacent cached lists before scrolling; composition never adds data demand.
            HorizontalPager(
                state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1,
                key = { TrendingBoard.entries[it].name }
            ) { page ->
                TrendingList(
                    state, TrendingBoard.entries[page], onOpenApiSettings, onDisplay,
                    onFilters = { if (boardSettled) showFilters = true }, onToken = { token = it },
                    onCopyAddress = { address ->
                        clipboard.setText(AnnotatedString(address))
                        Toast.makeText(context, copiedLabel, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
        if (showFilters) ModalBottomSheet(
            onDismissRequest = { showFilters = false }, shape = design.shapes.bottomBar,
            containerColor = design.colors.pageBackground, contentColor = design.colors.primaryText
        ) {
            TrendingFilters(state, onToggleSource, onOptions, onDisplay, onOpenApiSettings)
        }
        token?.let { selected -> ModalBottomSheet(
            onDismissRequest = { token = null }, shape = design.shapes.bottomBar,
            containerColor = design.colors.pageBackground, contentColor = design.colors.primaryText
        ) {
            TrendingTokenDetails(selected, onOpenBrowser)
        } }
    }
}

@Composable
private fun TrendingList(
    state: TrendingUiState, board: TrendingBoard, onOpenApiSettings: () -> Unit,
    onDisplay: (TrendingBoard, TrendingOptions) -> Unit, onFilters: () -> Unit,
    onToken: (TrendingToken) -> Unit, onCopyAddress: (String) -> Unit
) {
    val design = CoinMonitorThemeTokens.design
    val summary = state.summary(board)
    val options = state.displayOptions(board)
    val newIds = state.newIds(board)
    // A status/countdown tick must not re-sort unchanged token lists on every page.
    val rows = remember(board, state.snapshots, state.sharedSnapshot, state.sources, options, newIds) {
        state.rows(board)
    }
    val missingKeysLabel = if (summary.missing.isNotEmpty()) {
        stringResource(R.string.trending_missing_keys, summary.missing.joinToString { it.title })
    } else null
    val ready = summary.missing.isEmpty() && summary.sources.all { state.snapshots[it]?.updatedAt != null }
    Column(
        Modifier.fillMaxSize().padding(horizontal = design.spacing.pageHorizontal, vertical = design.spacing.small),
        verticalArrangement = Arrangement.spacedBy(design.spacing.small)
    ) {
        TrendingSourceSummary(summary, onFilters)
        // Cached rows remain usable: show refresh failures only on an empty board.
        if (rows.isEmpty()) summary.failures.forEach { source ->
            val snapshot = state.snapshots.getValue(source)
            val seconds = ((snapshot.retryAt - state.now).coerceAtLeast(0) + 999) / 1000
            Text(
                "${source.title} · ${failureTitle(snapshot.failure!!, seconds)}",
                Modifier.padding(horizontal = design.spacing.inline), style = design.typography.caption, color = design.colors.error
            )
        }
        Surface(
            Modifier.fillMaxWidth().then(if (rows.isEmpty()) Modifier else Modifier.weight(1f))
                .testTag("trending-${board.name}-container"), shape = design.shapes.card,
            color = design.colors.surface, contentColor = design.colors.primaryText
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = design.spacing.cardPadding, end = design.spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#", Modifier.width(design.sizes.rankWidth), style = design.typography.caption, color = design.colors.secondaryText)
                    Text(stringResource(R.string.trending_token), Modifier.weight(1f), style = design.typography.caption, color = design.colors.secondaryText)
                    TextButton(onClick = { onDisplay(board, options.copy(value =
                        if (options.value == TrendingValue.MARKET_CAP) TrendingValue.PRICE else TrendingValue.MARKET_CAP)) }) {
                        Text(stringResource(if (options.value == TrendingValue.MARKET_CAP) R.string.trending_market_cap else R.string.trending_price), style = design.typography.caption)
                        Spacer(Modifier.width(design.spacing.small))
                        Icon(
                            CoinMonitorIcons.Cycle,
                            stringResource(if (options.value == TrendingValue.MARKET_CAP) R.string.trending_switch_to_price else R.string.trending_switch_to_market_cap),
                            Modifier.size(design.sizes.inlineActionIcon)
                        )
                    }
                }
                if (rows.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = design.sizes.trendingEmptyBodyMinimumHeight)
                            .padding(design.spacing.pageHorizontal),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(design.spacing.inline)) {
                            if (state.cacheRestoring || summary.loading) CircularProgressIndicator(
                                Modifier.size(design.sizes.actionIcon), color = design.colors.accent, strokeWidth = 2.dp
                            )
                            if (missingKeysLabel != null) TextButton(onClick = onOpenApiSettings) {
                                Text(missingKeysLabel, style = design.typography.label)
                            }
                            Text(
                                stringResource(when {
                                    state.cacheRestoring -> R.string.trending_loading_cache
                                    summary.loading -> R.string.trending_loading
                                    summary.missing.isNotEmpty() -> R.string.trending_configure_hint
                                    summary.failures.isNotEmpty() -> R.string.trending_unavailable
                                    !ready -> R.string.trending_loading
                                    board == TrendingBoard.SHARED -> R.string.trending_no_overlap
                                    else -> R.string.trending_empty
                                }), style = design.typography.rowSubtitle, color = design.colors.secondaryText, textAlign = TextAlign.Center
                            )
                        }
                    }
                } else LazyColumn(
                    Modifier.weight(1f).testTag("trending-${board.name}-list"),
                    contentPadding = PaddingValues(horizontal = design.spacing.small, vertical = design.spacing.inline)
                ) {
                    itemsIndexed(rows, key = { _, item -> item.id }, contentType = { _, _ -> "trending-token" }) { index, item ->
                        TrendingTokenRow(
                            item, index + 1, item.id in newIds, options.value,
                            showSmartMoney = TrendingSource.GMGN in summary.sources,
                            onToken = onToken, onCopyAddress = onCopyAddress
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = design.spacing.cardPadding, vertical = design.spacing.inline),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(design.spacing.inline)
                ) {
                    Text(
                        if (summary.updatedAt == null) stringResource(R.string.trending_waiting_update)
                        else if (board == TrendingBoard.SHARED) pluralStringResource(R.plurals.trending_shared_results, rows.size, rows.size, summary.sources.size)
                        else pluralStringResource(R.plurals.trending_count, rows.size, rows.size),
                        Modifier.weight(1f),
                        style = design.typography.caption, color = design.colors.secondaryText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    TrendingUpdateStatus(summary, Modifier.weight(1f).testTag("trending-${board.name}-updated"))
                }
            }
        }
    }
}

@Composable
private fun TrendingSourceSummary(summary: TrendingBoardSummary, onFilters: () -> Unit) {
    val design = CoinMonitorThemeTokens.design
    Surface(
        onClick = onFilters, shape = design.shapes.card, color = design.colors.surface,
        contentColor = design.colors.primaryText, modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.heightIn(min = design.sizes.minimumTouchTarget).padding(horizontal = design.spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(design.spacing.inline)
        ) {
            TrendingSourceDots(summary.sources.toSet(), prominent = true)
            Text(
                if (summary.sources.size == 1) summary.sources.first().title
                else pluralStringResource(R.plurals.trending_source_count, summary.sources.size, summary.sources.size),
                Modifier.weight(1f), style = design.typography.label
            )
            Row(
                Modifier.widthIn(max = 180.dp).background(design.colors.selectedContainer, design.shapes.pill)
                    .padding(horizontal = design.spacing.inline, vertical = design.spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(scopeTitle(summary), Modifier.weight(1f, fill = false), style = design.typography.caption, color = design.colors.selectedContent)
                Icon(CoinMonitorIcons.Expand, stringResource(R.string.trending_filters), Modifier.size(design.sizes.inlineActionIcon), tint = design.colors.selectedContent)
            }
        }
    }
}

@Composable
private fun TrendingUpdateStatus(summary: TrendingBoardSummary, modifier: Modifier = Modifier) {
    val design = CoinMonitorThemeTokens.design
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(design.spacing.small)
    ) {
        Text(
            summary.updatedAt?.let {
                stringResource(R.string.trending_updated, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it)))
            } ?: "",
            Modifier.weight(1f), style = design.typography.caption, color = design.colors.secondaryText,
            textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (summary.loading) CircularProgressIndicator(Modifier.size(design.sizes.inlineActionIcon), strokeWidth = 2.dp, color = design.colors.accent)
    }
}

@Composable
private fun scopeTitle(summary: TrendingBoardSummary): String {
    val chain = when {
        summary.mixedChains -> stringResource(R.string.trending_mixed_chains)
        summary.chainTitle != null -> summary.chainTitle
        else -> stringResource(R.string.trending_all_chains)
    }
    val range = when {
        summary.sources == listOf(TrendingSource.PUMP) -> summary.period
        summary.sources.size > 1 && (TrendingSource.PUMP in summary.sources || summary.mixedPeriods || summary.mode == null) ->
            stringResource(R.string.trending_independent_filters)
        summary.mode == TrendingMode.HOT_SEARCH -> listOfNotNull(summary.period, modeTitle(summary.mode)).joinToString(" · ")
        else -> summary.period
    }
    return listOfNotNull(chain, range).joinToString(" · ")
}

@Composable
private fun boardTabTitle(board: TrendingBoard): String = when (board) {
    TrendingBoard.SHARED -> stringResource(R.string.trending_shared_short)
    TrendingBoard.GMGN -> TrendingSource.GMGN.title
    TrendingBoard.PUMP -> TrendingSource.PUMP.title
    TrendingBoard.OKX -> TrendingSource.OKX.title
}

@Composable internal fun boardTitle(board: TrendingBoard): String = stringResource(when (board) {
    TrendingBoard.SHARED -> R.string.trending_shared
    TrendingBoard.GMGN -> R.string.trending_gmgn
    TrendingBoard.PUMP -> R.string.trending_pump
    TrendingBoard.OKX -> R.string.trending_okx
})
@Composable internal fun modeTitle(mode: TrendingMode): String = stringResource(if (mode == TrendingMode.TRENDING) R.string.trending_mode_trending else R.string.trending_mode_search)
@Composable internal fun sortTitle(sort: TrendingSort): String = stringResource(when (sort) {
    TrendingSort.DEFAULT -> R.string.trending_default_order
    TrendingSort.TOKEN -> R.string.trending_token
    TrendingSort.MARKET_CAP -> R.string.trending_market_cap
    TrendingSort.PRICE -> R.string.trending_price
    TrendingSort.CHANGE -> R.string.trending_change
    TrendingSort.VOLUME -> R.string.trending_volume
    TrendingSort.LIQUIDITY -> R.string.trending_liquidity
    TrendingSort.SMART_MONEY -> R.string.trending_smart_money
})
@Composable private fun failureTitle(failure: TrendingFailure, seconds: Long): String = when (failure) {
    TrendingFailure.RATE_LIMIT -> stringResource(R.string.trending_rate_limit, seconds)
    TrendingFailure.AUTHENTICATION -> stringResource(R.string.trending_auth_error)
    TrendingFailure.QUOTA -> stringResource(R.string.trending_quota_error)
    TrendingFailure.CREDENTIALS -> stringResource(R.string.trending_configure_hint)
    TrendingFailure.INVALID_DATA -> stringResource(R.string.trending_invalid_data)
    TrendingFailure.NETWORK -> stringResource(R.string.trending_network_error, seconds)
}
internal fun compactUsd(value: Double?): String {
    if (value == null || !value.isFinite()) return "--"
    val (divisor, suffix) = when {
        kotlin.math.abs(value) >= 1e9 -> 1e9 to "B"
        kotlin.math.abs(value) >= 1e6 -> 1e6 to "M"
        kotlin.math.abs(value) >= 1e3 -> 1e3 to "K"
        else -> 1.0 to ""
    }
    return "$" + String.format(Locale.US, "%.2f", value / divisor) + suffix
}
internal fun priceUsd(value: Double?): String = value?.takeIf(Double::isFinite)?.let {
    "$" + QuoteFormatter.formatPrice(it)
} ?: "--"
