package io.baiyanwu.coinmonitor.ui.trending

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
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
import io.baiyanwu.coinmonitor.ui.components.CoinSymbolIcon
import io.baiyanwu.coinmonitor.ui.components.MainTabTopBar
import io.baiyanwu.coinmonitor.ui.components.MarketModeTabs
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
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
    val colors = CoinMonitorThemeTokens.colors
    val pager = rememberPagerState(initialPage = state.board.ordinal, pageCount = { TrendingBoard.entries.size })
    val scope = rememberCoroutineScope()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var token by remember { mutableStateOf<TrendingToken?>(null) }
    LaunchedEffect(pager.settledPage) { onSelectBoard(TrendingBoard.entries[pager.settledPage]) }
    Column(Modifier.fillMaxSize().background(colors.pageBackground).padding(top = contentTopInset, bottom = contentBottomInset)) {
        MainTabTopBar {
            Text(stringResource(R.string.tab_trending), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, color = colors.primaryText, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh, enabled = state.demand.any { source ->
                source in state.ready && state.snapshots[source]?.loading != true &&
                    (state.snapshots[source]?.retryAt ?: 0) <= state.now
            }) { Icon(Icons.Rounded.Refresh, stringResource(R.string.trending_refresh), tint = colors.accent) }
            IconButton(onClick = { showFilters = true }) { Icon(Icons.Rounded.Tune, stringResource(R.string.trending_filters), tint = colors.accent) }
        }
        MarketModeTabs(
            selectedPage = pager.currentPage,
            onSelectPage = { page -> scope.launch { pager.animateScrollToPage(page) } },
            labelResources = listOf(R.string.trending_shared, R.string.trending_gmgn, R.string.trending_pump, R.string.trending_okx),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
        )
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            TrendingList(state, TrendingBoard.entries[page], onOpenApiSettings, onDisplay) { token = it }
        }
    }
    if (showFilters) ModalBottomSheet(onDismissRequest = { showFilters = false }, containerColor = colors.pageBackground) {
        TrendingFilters(state, onToggleSource, onOptions, onDisplay, onOpenApiSettings)
    }
    token?.let { selected -> ModalBottomSheet(onDismissRequest = { token = null }, containerColor = colors.pageBackground) {
        TrendingTokenDetails(selected, onOpenBrowser)
    } }
}

@Composable
private fun TrendingList(state: TrendingUiState, board: TrendingBoard, onOpenApiSettings: () -> Unit,
    onDisplay: (TrendingBoard, TrendingOptions) -> Unit, onToken: (TrendingToken) -> Unit) {
    val colors = CoinMonitorThemeTokens.colors
    val rows = state.rows(board)
    val demand = TrendingRules.demand(board, state.sources)
    val loading = demand.any { state.snapshots[it]?.loading == true }
    val options = state.displayOptions(board)
    val missing = demand.filter { it !in state.ready }
    val failures = demand.filter { state.snapshots[it]?.failure != null && it in state.ready }
    val ready = missing.isEmpty() && demand.all { state.snapshots[it]?.updatedAt != null }
    val newIds = state.newIds(board)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (board == TrendingBoard.SHARED) state.sources.joinToString(" ∩ ") { it.title }
                else if (board == TrendingBoard.PUMP) "Solana · PumpFun" else {
                    val source = demand.first(); val selection = state.options.getValue(source)
                    "${selection.chain?.title ?: stringResource(R.string.trending_all_chains)} · ${selection.period} · ${modeTitle(selection.mode)}"
                }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = colors.secondaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.accent)
            else Text(pluralStringResource(R.plurals.trending_count, rows.size, rows.size), style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
        }
        if (missing.isNotEmpty()) TextButton(onClick = onOpenApiSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.trending_missing_keys, missing.joinToString { it.title }))
        }
        failures.forEach { source ->
            val snapshot = state.snapshots.getValue(source)
            val seconds = ((snapshot.retryAt - state.now).coerceAtLeast(0) + 999) / 1000
            Text("${source.title} · ${failureTitle(snapshot.failure!!, seconds)}" +
                if (snapshot.updatedAt != null) " · ${stringResource(R.string.trending_previous_result)}" else "",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.trending_token), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
            Text(stringResource(if (options.value == TrendingValue.MARKET_CAP) R.string.trending_market_cap else R.string.trending_price),
                style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
            IconButton(
                onClick = { onDisplay(board, options.copy(value = if (options.value == TrendingValue.MARKET_CAP) TrendingValue.PRICE else TrendingValue.MARKET_CAP)) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Rounded.Autorenew,
                    contentDescription = stringResource(if (options.value == TrendingValue.MARKET_CAP) R.string.trending_switch_to_price else R.string.trending_switch_to_market_cap),
                    modifier = Modifier.size(14.dp), tint = colors.accent)
            }
        }
        HorizontalDivider(color = colors.divider.copy(alpha = 0.35f))
        if (rows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(when {
                    state.cacheRestoring -> R.string.trending_loading_cache
                    loading -> R.string.trending_loading
                    missing.isNotEmpty() -> R.string.trending_configure_hint
                    failures.isNotEmpty() -> R.string.trending_unavailable
                    !ready -> R.string.trending_loading
                    board == TrendingBoard.SHARED -> R.string.trending_no_overlap
                    else -> R.string.trending_empty
                }), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryText)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                itemsIndexed(rows, key = { _, item -> item.id }) { index, item ->
                    TrendingTokenRow(item, index + 1, item.id in newIds, options.value,
                        showSmartMoney = TrendingSource.GMGN in demand, onToken = onToken)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.divider.copy(alpha = 0.5f))
                }
            }
        }
        val times = demand.mapNotNull { state.snapshots[it]?.updatedAt }
        if (times.isNotEmpty()) {
            val pausedLabel = stringResource(R.string.trending_paused)
            val refresh = demand.joinToString(" · ") { source ->
                val selection = state.options.getValue(source)
                val snapshot = state.snapshots[source]
                if (!selection.autoRefresh) "${source.title} $pausedLabel"
                else if (snapshot?.failure != null) source.title
                else {
                    val due = maxOf((snapshot?.updatedAt ?: state.now) + selection.refreshSeconds * 1000L, snapshot?.retryAt ?: 0)
                    "${source.title} ${((due - state.now).coerceAtLeast(0) + 999) / 1000}s"
                }
            }
            Text(stringResource(R.string.trending_updated,
                java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(times.min()))) + " · " + refresh,
                Modifier.padding(horizontal = 16.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = colors.secondaryText,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrendingTokenRow(token: TrendingToken, rank: Int, isNew: Boolean, value: TrendingValue,
    showSmartMoney: Boolean, onToken: (TrendingToken) -> Unit) {
    val colors = CoinMonitorThemeTokens.colors
    Row(Modifier.fillMaxWidth().clickable { onToken(token) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(rank.toString(), Modifier.width(22.dp), style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
        TrendingTokenIcon(token)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(token.symbol, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.primaryText)
                if (showSmartMoney) TrendingSmartMoney(token.smartMoney)
                if (isNew) Text("NEW", style = MaterialTheme.typography.labelSmall, color = colors.accent)
                if (token.risk) Text(stringResource(R.string.trending_risk), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Text(token.address.take(4) + "…" + token.address.takeLast(4),
                style = MaterialTheme.typography.labelSmall, color = colors.secondaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (value == TrendingValue.MARKET_CAP) compactUsd(token.marketCap) else priceUsd(token.price),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = colors.primaryText)
            if (token.change != null) Text(String.format(Locale.US, "%+.2f%%", token.change),
                style = MaterialTheme.typography.labelMedium, color = if (token.change >= 0) colors.positive else colors.negative)
            else Text(stringResource(R.string.trending_liquidity_short, compactUsd(token.liquidity)),
                style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
        }
    }
}

@Composable
private fun TrendingTokenIcon(token: TrendingToken) {
    val colors = CoinMonitorThemeTokens.colors
    val chainLogo = when (token.chainIndex) {
        "501" -> R.drawable.trending_chain_sol
        "56" -> R.drawable.trending_chain_bsc
        "8453" -> R.drawable.trending_chain_base
        "1" -> R.drawable.trending_chain_eth
        "42161" -> R.drawable.trending_chain_arbitrum
        "999" -> R.drawable.trending_chain_hyperevm
        "4663" -> R.drawable.trending_chain_robinhood
        "5042" -> R.drawable.trending_chain_arc
        "988" -> R.drawable.trending_chain_stable
        else -> null
    }
    Box(Modifier.size(32.dp)) {
        CoinSymbolIcon(token.symbol, iconUrl = token.iconUrl, allowSymbolLookup = false,
            size = 30.dp, modifier = Modifier.align(Alignment.Center))
        val badge = Modifier.align(Alignment.BottomEnd).size(14.dp)
            .background(colors.pageBackground, CircleShape).padding(1.dp).clip(CircleShape)
        if (chainLogo != null) Image(painterResource(chainLogo), token.chainTitle, modifier = badge)
        else Icon(Icons.Rounded.Language, token.chainTitle, modifier = badge, tint = colors.secondaryText)
    }
}

@Composable
private fun TrendingSmartMoney(count: Double?) {
    val color = CoinMonitorThemeTokens.colors.accent
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(Icons.Rounded.Person, stringResource(R.string.trending_smart_money), modifier = Modifier.size(12.dp), tint = color)
        Text(count?.takeIf(Double::isFinite)?.let { java.text.NumberFormat.getIntegerInstance(Locale.US).format(it) } ?: "--",
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
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
