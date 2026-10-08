package io.baiyanwu.coinmonitor.ui.trending

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.domain.model.*
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

@Composable
internal fun TrendingFilters(state: TrendingUiState, onToggleSource: (TrendingSource) -> Unit,
    onOptions: (TrendingSource, TrendingOptions) -> Unit, onDisplay: (TrendingBoard, TrendingOptions) -> Unit, onOpenApiSettings: () -> Unit) {
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(stringResource(R.string.trending_filters), style = MaterialTheme.typography.titleLarge) }
        if (state.board == TrendingBoard.SHARED) {
            item {
                FilterTitle(stringResource(R.string.trending_sources))
                ChoiceRow(TrendingSource.entries.toList(), { it in state.sources }, { it.title }, onToggleSource,
                    enabled = { it !in state.sources || state.sources.size > 2 })
                Text(stringResource(R.string.trending_shared_hint), style = MaterialTheme.typography.bodySmall, color = CoinMonitorThemeTokens.colors.secondaryText)
            }
        }
        val demand = TrendingRules.demand(state.board, state.sources)
        demand.forEach { source ->
            val options = state.options.getValue(source)
            item {
                FilterTitle(source.title)
                if (source != TrendingSource.PUMP) {
                    ChoiceRow(TrendingMode.entries.toList(), { it == options.mode }, { modeTitle(it) }, { onOptions(source, options.copy(mode = it)) })
                    val chains = if (source == TrendingSource.OKX) TrendingChain.entries.take(5) else TrendingChain.entries
                    ChoiceRow(listOf<TrendingChain?>(null) + chains, { it == options.chain }, { it?.title ?: stringResource(R.string.trending_all_chains) },
                        { onOptions(source, options.copy(chain = it)) })
                    ChoiceRow(TrendingOptions.periods(source), { it == options.period }, { it }, { onOptions(source, options.copy(period = it)) })
                } else Text(stringResource(R.string.trending_pump_hint), style = MaterialTheme.typography.bodySmall, color = CoinMonitorThemeTokens.colors.secondaryText)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.trending_auto_refresh), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    Switch(options.autoRefresh, { onOptions(source, options.copy(autoRefresh = it)) }, colors = CoinMonitorComponentDefaults.switchColors())
                }
                ChoiceRow(TrendingOptions.refreshIntervals, { it == options.refreshSeconds }, { stringResource(R.string.trending_seconds, it) },
                    { onOptions(source, options.copy(refreshSeconds = it)) })
            }
        }
        item {
            val options = state.displayOptions(state.board)
            FilterTitle(stringResource(R.string.trending_sort))
            val fields = if (state.board == TrendingBoard.PUMP) listOf(TrendingSort.DEFAULT, TrendingSort.TOKEN, TrendingSort.MARKET_CAP, TrendingSort.LIQUIDITY)
                else TrendingSort.entries.filter { it != TrendingSort.SMART_MONEY || TrendingSource.GMGN in demand }
            ChoiceRow(fields, { it == options.sort }, { sortTitle(it) }, { onDisplay(state.board, options.copy(sort = it)) })
            if (options.sort != TrendingSort.DEFAULT) ChoiceRow(listOf(false, true), { it == options.ascending },
                { stringResource(if (it) R.string.trending_ascending else R.string.trending_descending) }, { onDisplay(state.board, options.copy(ascending = it)) })
            FilterTitle(stringResource(R.string.trending_display_value))
            ChoiceRow(TrendingValue.entries.toList(), { it == options.value },
                { stringResource(if (it == TrendingValue.MARKET_CAP) R.string.trending_market_cap else R.string.trending_price) },
                { onDisplay(state.board, options.copy(value = it)) })
            TextButton(onClick = onOpenApiSettings) { Text(stringResource(R.string.trending_api_settings)) }
        }
    }
}

@Composable private fun FilterTitle(title: String) {
    Text(title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}
@Composable private fun <T> ChoiceRow(values: List<T>, selected: (T) -> Boolean, label: @Composable (T) -> String,
    onSelect: (T) -> Unit, enabled: (T) -> Boolean = { true }) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(values) { value -> FilterChip(selected(value), { onSelect(value) }, label = { Text(label(value)) },
            enabled = enabled(value), colors = CoinMonitorComponentDefaults.filterChipColors()) }
    }
}

@Composable internal fun TrendingTokenDetails(token: TrendingToken, onOpenBrowser: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(token.symbol, style = MaterialTheme.typography.headlineSmall)
            Text(listOf(token.name, token.chainTitle).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text(token.address, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { clipboard.setText(AnnotatedString(token.address)) }) { Text(stringResource(R.string.trending_copy_address)) }
        }
        val metrics = listOf(R.string.trending_market_cap to compactUsd(token.marketCap), R.string.trending_price to priceUsd(token.price),
            R.string.trending_liquidity to compactUsd(token.liquidity), R.string.trending_volume to compactUsd(token.volume),
            R.string.trending_change to (token.change?.let { java.lang.String.format(java.util.Locale.US, "%+.2f%%", it) } ?: "--"),
            R.string.trending_smart_money to (token.smartMoney?.toLong()?.toString() ?: "--"))
        items(metrics) { (label, value) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(label), color = CoinMonitorThemeTokens.colors.secondaryText); Text(value)
        } }
        token.links.forEach { (source, url) -> item {
            OutlinedButton(onClick = { onOpenBrowser(url) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.trending_open_source, source.title)) }
        } }
    }
}
