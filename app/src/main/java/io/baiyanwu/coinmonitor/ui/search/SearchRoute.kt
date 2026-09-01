package io.baiyanwu.coinmonitor.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OnchainChainRegistry
import io.baiyanwu.coinmonitor.domain.model.OnchainChainIconRegistry
import io.baiyanwu.coinmonitor.domain.model.OnchainPoolOption
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.ui.components.CoinSymbolIcon
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import kotlinx.coroutines.launch
import java.util.Locale

private const val SOLANA_CHAIN_INDEX = "501"
private const val MIN_SOL_ADDRESS_LENGTH = 32
private const val MAX_SOL_ADDRESS_LENGTH = 44
private const val MAX_VISIBLE_ALTERNATIVE_POOLS = 5
private val SEARCH_MODES = listOf(SearchMode.EXCHANGE, SearchMode.ONCHAIN)

private enum class ChainFamilyLabel {
    EVM,
    SOL,
    UNKNOWN
}

@Composable
fun SearchRoute(
    container: AppContainer,
    entryMode: SearchEntryMode = SearchEntryMode.HOME,
    onBack: () -> Unit,
    onSelectForKline: (String) -> Unit = {}
) {
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        entryMode = entryMode,
        onBack = onBack,
        onQueryChange = viewModel::updateQuery,
        onClearQuery = viewModel::clearQuery,
        onSearch = viewModel::search,
        onSearchModeChange = viewModel::setSearchMode,
        onToggleItem = viewModel::toggleWatchItem,
        onSelectOnchainPool = viewModel::selectOnchainPool,
        onSelectForKline = { item ->
            viewModel.selectItemForKline(item, onSelectForKline)
        }
    )
}

@Composable
private fun SearchScreen(
    state: SearchUiState,
    entryMode: SearchEntryMode,
    onBack: () -> Unit,
    onQueryChange: (SearchMode, String) -> Unit,
    onClearQuery: (SearchMode) -> Unit,
    onSearch: (SearchMode) -> Unit,
    onSearchModeChange: (SearchMode) -> Unit,
    onToggleItem: (WatchItem) -> Unit,
    onSelectOnchainPool: (WatchItem, OnchainPoolOption) -> Unit,
    onSelectForKline: (WatchItem) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val pagerState = rememberPagerState(
        initialPage = SEARCH_MODES.indexOf(state.searchMode).coerceAtLeast(0),
        pageCount = SEARCH_MODES::size
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        onSearchModeChange(SEARCH_MODES[pagerState.currentPage])
    }

    val exchangePage = state.pageState(SearchMode.EXCHANGE)
    val onchainPage = state.pageState(SearchMode.ONCHAIN)
    val exchangeResults = remember(exchangePage.results) {
        exchangePage.results
            .filterNot(::isOnchainItem)
    }
    val onchainResults = remember(onchainPage.results) {
        onchainPage.results
            .filter(::isOnchainItem)
            .sortedWith(
                compareBy<WatchItem>(
                    { chainRank(it) },
                    { it.symbol.uppercase() },
                    { it.name.uppercase() }
                )
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
    ) {
        SearchModeTabs(
            selectedPage = pagerState.currentPage,
            onSelectPage = { page ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(page)
                }
            }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.Top
        ) {
            val pageMode = SEARCH_MODES[it]
            val pageState = state.pageState(pageMode)
            Column(modifier = Modifier.fillMaxSize()) {
                SearchHeader(
                    searchMode = pageMode,
                    pageState = pageState,
                    onBack = onBack,
                    onQueryChange = { query -> onQueryChange(pageMode, query) },
                    onClearQuery = { onClearQuery(pageMode) },
                    onSearch = { onSearch(pageMode) }
                )
                SearchModePage(
                    pageState = pageState,
                    addedSemanticKeys = state.addedSemanticKeys,
                    entryMode = entryMode,
                    pageMode = pageMode,
                    results = if (pageMode == SearchMode.EXCHANGE) exchangeResults else onchainResults,
                    onToggleItem = onToggleItem,
                    onSelectOnchainPool = onSelectOnchainPool,
                    onSelectForKline = onSelectForKline
                )
            }
        }
    }
}

@Composable
private fun SearchModeTabs(
    selectedPage: Int,
    onSelectPage: (Int) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val tabWidth = 96.dp
    val indicatorWidth = 32.dp
    val indicatorOffset by animateDpAsState(
        targetValue = if (selectedPage == 0) 32.dp else 128.dp,
        animationSpec = tween(durationMillis = 180),
        label = "search-mode-indicator"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.pageBackground)
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(tabWidth * SEARCH_MODES.size)
                .height(44.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                SEARCH_MODES.forEachIndexed { index, mode ->
                    val selected = index == selectedPage
                    val labelRes = if (mode == SearchMode.EXCHANGE) {
                        R.string.search_mode_exchange
                    } else {
                        R.string.search_mode_onchain
                    }
                    Box(
                        modifier = Modifier
                            .width(tabWidth)
                            .height(44.dp)
                            .clickable { onSelectPage(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            color = if (selected) colors.accent else colors.secondaryText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .height(3.dp)
                    .background(colors.accent, RoundedCornerShape(2.dp))
            ) {
            }
        }
    }
}

@Composable
private fun SearchHeader(
    searchMode: SearchMode,
    pageState: SearchPageState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearch: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val searchTextStyle = MaterialTheme.typography.bodyMedium.copy(
        color = colors.primaryText,
        fontSize = 15.sp,
        lineHeight = 18.sp
    )
    val placeholderTextStyle = MaterialTheme.typography.bodyMedium.copy(
        color = colors.tertiaryText,
        fontSize = 15.sp,
        lineHeight = 18.sp
    )
    val placeholderRes = if (searchMode == SearchMode.ONCHAIN) {
        R.string.search_input_hint_onchain
    } else {
        R.string.search_input_hint
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.pageBackground)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            color = colors.cardBackground,
            shape = RoundedCornerShape(18.dp)
        ) {
            BasicTextField(
                value = pageState.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                textStyle = searchTextStyle,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                cursorBrush = SolidColor(colors.accent),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pageState.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = colors.accent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colors.accent
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (pageState.query.isBlank()) {
                                Text(
                                    text = stringResource(placeholderRes),
                                    color = colors.tertiaryText,
                                    style = placeholderTextStyle,
                                    maxLines = 1
                                )
                            }
                            innerTextField()
                        }

                        if (pageState.query.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(onClick = onClearQuery),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.common_clear),
                                    modifier = Modifier.size(16.dp),
                                    tint = colors.secondaryText
                                )
                            }
                        }
                    }
                }
            )
        }

        Text(
            text = stringResource(R.string.common_cancel),
            color = colors.accent,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun SearchModePage(
    pageState: SearchPageState,
    addedSemanticKeys: Set<String>,
    entryMode: SearchEntryMode,
    pageMode: SearchMode,
    results: List<WatchItem>,
    onToggleItem: (WatchItem) -> Unit,
    onSelectOnchainPool: (WatchItem, OnchainPoolOption) -> Unit,
    onSelectForKline: (WatchItem) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (pageState.errorMessage != null) {
                item("search_error") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        color = colors.cardBackground,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = pageState.errorMessage,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else if (results.isEmpty() && !pageState.loading) {
                item("search_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 56.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val emptyTextRes = when {
                            pageState.hasSearched -> R.string.search_empty_result
                            pageMode == SearchMode.ONCHAIN -> R.string.search_empty_initial_onchain
                            else -> R.string.search_empty_initial
                        }
                        Text(
                            text = stringResource(emptyTextRes),
                            color = colors.secondaryText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (results.isNotEmpty()) {
                itemsIndexed(
                    items = results,
                    key = { _, item -> "${item.semanticKey}:${item.poolAddress.orEmpty()}" }
                ) { index, item ->
                    val rowShape = when {
                        results.size == 1 -> RoundedCornerShape(22.dp)
                        index == 0 -> RoundedCornerShape(
                            topStart = 22.dp,
                            topEnd = 22.dp
                        )
                        index == results.lastIndex -> RoundedCornerShape(
                            bottomStart = 22.dp,
                            bottomEnd = 22.dp
                        )
                        else -> RoundedCornerShape(0.dp)
                    }
                    ElevatedCard(
                        shape = rowShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(
                                top = if (index == 0) 8.dp else 0.dp,
                                bottom = if (index == results.lastIndex) 8.dp else 0.dp
                            )
                    ) {
                        SearchResultRow(
                            item = item,
                            entryMode = entryMode,
                            searchMode = pageMode,
                            added = item.semanticKey in addedSemanticKeys,
                            onToggleItem = onToggleItem,
                            onSelectOnchainPool = onSelectOnchainPool,
                            onSelectForKline = onSelectForKline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    item: WatchItem,
    entryMode: SearchEntryMode,
    searchMode: SearchMode,
    added: Boolean,
    onToggleItem: (WatchItem) -> Unit,
    onSelectOnchainPool: (WatchItem, OnchainPoolOption) -> Unit,
    onSelectForKline: (WatchItem) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val onchainMode = searchMode == SearchMode.ONCHAIN
    val klineEntry = entryMode == SearchEntryMode.KLINE
    val selectedPool = item.selectedPool
    val allAlternativePools = if (onchainMode && item.poolOptions.size > 1) {
        item.poolOptions.filterNot { option -> option.poolAddress == item.poolAddress }
    } else {
        emptyList()
    }
    val alternativePools = allAlternativePools.take(MAX_VISIBLE_ALTERNATIVE_POOLS)
    var poolsExpanded by rememberSaveable(item.semanticKey) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = klineEntry) { onSelectForKline(item) }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onchainMode) {
                CoinSymbolIcon(
                    symbol = item.baseSymbol,
                    iconUrl = item.iconUrl,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                CoinSymbolIcon(item = item, modifier = Modifier.size(18.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val titleText = when {
                    onchainMode -> selectedPool?.pairLabel ?: resolveOnchainSymbol(item)
                    isUsdtFuturesItem(item) -> item.symbol.uppercase()
                    else -> item.baseSymbol
                }
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (onchainMode) {
                    OnchainPoolMetadata(item = item, pool = selectedPool)
                    Text(
                        text = resolveOnchainAddressSubtitle(item),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.tertiaryText,
                        maxLines = 1
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isUsdtFuturesItem(item)) {
                            MarketTypeBadge(text = stringResource(R.string.market_tag_usdt_futures))
                        } else {
                            Text(
                                text = item.symbol.substringAfter("/", ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryText
                            )
                        }
                        ExchangeSourceBadge(source = item.exchangeSource)
                    }
                }
            }
            if (!klineEntry) {
                Box(
                    modifier = Modifier
                        .clickable { onToggleItem(item) }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (added) stringResource(R.string.delete) else stringResource(R.string.add),
                        color = if (added) colors.negative else colors.positive,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (alternativePools.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clickable { poolsExpanded = !poolsExpanded }
                    .padding(start = 40.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (poolsExpanded) {
                        stringResource(R.string.search_onchain_hide_pools)
                    } else {
                        stringResource(R.string.search_onchain_other_pools, allAlternativePools.size)
                    },
                    modifier = Modifier.weight(1f),
                    color = colors.accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = if (poolsExpanded) {
                        Icons.Rounded.KeyboardArrowUp
                    } else {
                        Icons.Rounded.KeyboardArrowDown
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.accent
                )
            }

            AnimatedVisibility(visible = poolsExpanded) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 38.dp, end = 10.dp, bottom = 8.dp),
                    color = colors.pageBackground.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        alternativePools.forEach { option ->
                            OnchainPoolOptionRow(
                                option = option,
                                onSelect = { onSelectOnchainPool(item, option) }
                            )
                        }
                        if (allAlternativePools.size > alternativePools.size) {
                            Text(
                                text = stringResource(
                                    R.string.search_onchain_pool_limit,
                                    alternativePools.size
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                color = colors.tertiaryText,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnchainPoolMetadata(item: WatchItem, pool: OnchainPoolOption?) {
    val colors = CoinMonitorThemeTokens.colors
    val chainName = OnchainChainRegistry.find(resolveChainIndex(item))?.displayName.orEmpty()
    val details = pool?.let { option ->
        val liquidity = stringResource(
            R.string.search_onchain_liquidity,
            formatUsdCompact(option.liquidityUsd)
        )
        listOf(chainName, formatDexName(option), liquidity)
            .filter(String::isNotBlank)
            .joinToString(" · ")
    } ?: chainName

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoinSymbolIcon(
            symbol = chainName,
            iconUrl = OnchainChainIconRegistry.resolveIconUrl(resolveChainIndex(item)),
            size = 14.dp
        )
        Text(
            text = details,
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText,
            maxLines = 1
        )
    }
}

@Composable
private fun OnchainPoolOptionRow(
    option: OnchainPoolOption,
    onSelect: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = option.pairLabel,
                color = colors.primaryText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = "${formatDexName(option)} · ${stringResource(R.string.search_onchain_liquidity, formatUsdCompact(option.liquidityUsd))}",
                color = colors.secondaryText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        Text(
            text = stringResource(R.string.search_onchain_switch_pool),
            color = colors.accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MarketTypeBadge(text: String) {
    val colors = CoinMonitorThemeTokens.colors
    Surface(
        color = colors.cardBackground,
        shape = RoundedCornerShape(100.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = colors.secondaryText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 搜索结果里的交易所来源标签。
 */
@Composable
private fun ExchangeSourceBadge(source: ExchangeSource) {
    val colors = CoinMonitorThemeTokens.colors
    val containerColor = colors.accent.copy(alpha = 0.16f)
    val contentColor = colors.accent
    val label = when (source) {
        ExchangeSource.BINANCE -> stringResource(R.string.exchange_badge_binance)
        ExchangeSource.BINANCE_ALPHA -> stringResource(R.string.exchange_badge_binance_alpha)
        ExchangeSource.OKX -> stringResource(R.string.exchange_badge_okx)
        ExchangeSource.ONCHAIN -> stringResource(R.string.exchange_badge_onchain)
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(100.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun isUsdtFuturesItem(item: WatchItem): Boolean {
    return item.marketType == MarketType.CEX_USDT_FUTURES
}

private fun isOnchainItem(item: WatchItem): Boolean {
    if (item.marketType == MarketType.ONCHAIN_TOKEN) return true
    val id = item.id.lowercase()
    if (id.startsWith("okx-onchain:") || id.startsWith("onchain:") || id.startsWith("okx-dex:")) {
        return true
    }
    if (item.symbol.contains("/")) {
        return false
    }
    val rawIdentity = resolveOnchainIdentity(item)
    return looksLikeHexAddress(rawIdentity) || looksLikeSolAddress(rawIdentity)
}

private fun chainRank(item: WatchItem): Int {
    return when (inferChainFamily(item)) {
        ChainFamilyLabel.SOL -> 0
        ChainFamilyLabel.EVM -> 1
        ChainFamilyLabel.UNKNOWN -> 2
    }
}

private fun inferChainFamily(item: WatchItem): ChainFamilyLabel {
    return when {
        item.chainFamily?.name == ChainFamilyLabel.EVM.name -> ChainFamilyLabel.EVM
        item.chainFamily?.name == ChainFamilyLabel.SOL.name -> ChainFamilyLabel.SOL
        resolveChainIndex(item) == SOLANA_CHAIN_INDEX -> ChainFamilyLabel.SOL
        looksLikeHexAddress(resolveOnchainIdentity(item)) -> ChainFamilyLabel.EVM
        looksLikeSolAddress(resolveOnchainIdentity(item)) -> ChainFamilyLabel.SOL
        item.id.lowercase().contains(":sol:") || item.id.lowercase().contains("solana") -> ChainFamilyLabel.SOL
        item.id.lowercase().contains(":evm:") || item.id.lowercase().contains("ethereum") -> ChainFamilyLabel.EVM
        else -> ChainFamilyLabel.UNKNOWN
    }
}

private fun resolveOnchainSymbol(item: WatchItem): String {
    return when {
        item.symbol.contains("/") -> item.symbol.substringBefore("/").uppercase()
        item.symbol.isNotBlank() -> item.symbol.uppercase()
        else -> item.name.uppercase()
    }
}

private fun resolveOnchainAddressSubtitle(item: WatchItem): String {
    return resolveOnchainIdentity(item)
        .takeIf { it.isNotBlank() }
        ?.let(::shortenAddress)
        ?: "--"
}

/**
 * 优先吃新字段里的 chainIndex，只有历史数据缺字段时，才回退到旧的 id 结构兜底解析。
 */
private fun resolveChainIndex(item: WatchItem): String? {
    item.chainIndex?.takeIf { it.isNotBlank() }?.let { return it }
    val segments = item.id.split(":")
    if (segments.size < 3) return null
    return when (segments.first().lowercase()) {
        "okx-onchain", "onchain", "okx-dex" -> segments.getOrNull(1)
        else -> null
    }
}

private fun resolveOnchainIdentity(item: WatchItem): String {
    item.tokenAddress?.takeIf { it.isNotBlank() }?.let { return it }
    return item.id.substringAfterLast(":", item.id)
}

private fun looksLikeHexAddress(value: String): Boolean {
    return value.length == 42 &&
        value.startsWith("0x", ignoreCase = true) &&
        value.drop(2).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

private fun looksLikeSolAddress(value: String): Boolean {
    if (value.length !in MIN_SOL_ADDRESS_LENGTH..MAX_SOL_ADDRESS_LENGTH) return false
    return value.all { char ->
        char in '1'..'9' ||
            char in 'A'..'H' ||
            char in 'J'..'N' ||
            char in 'P'..'Z' ||
            char in 'a'..'k' ||
            char in 'm'..'z'
    }
}

private fun formatDexName(option: OnchainPoolOption): String {
    val baseName = when (option.dexId.lowercase()) {
        "uniswap" -> "Uniswap"
        "pancakeswap" -> "PancakeSwap"
        "sushiswap" -> "SushiSwap"
        "raydium" -> "Raydium"
        "orca" -> "Orca"
        "aerodrome" -> "Aerodrome"
        "camelot" -> "Camelot"
        "traderjoe" -> "Trader Joe"
        else -> option.dexId
            .split('-', '_')
            .filter(String::isNotBlank)
            .joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
                }
            }
            .ifBlank { "DEX" }
    }
    val versionLabel = option.labels.firstOrNull { it.isNotBlank() }
        ?.let { label ->
            if (label.matches(Regex("v\\d+", RegexOption.IGNORE_CASE))) {
                label.uppercase()
            } else {
                label
            }
        }
    return listOfNotNull(baseName, versionLabel).joinToString(" ")
}

private fun formatUsdCompact(value: Double): String {
    return when {
        value >= 1_000_000_000.0 -> String.format(Locale.US, "\$%.1fB", value / 1_000_000_000.0)
        value >= 1_000_000.0 -> String.format(Locale.US, "\$%.1fM", value / 1_000_000.0)
        value >= 1_000.0 -> String.format(Locale.US, "\$%.1fK", value / 1_000.0)
        else -> String.format(Locale.US, "\$%.0f", value)
    }
}

private fun shortenAddress(value: String): String {
    if (value.length <= 10) return value
    return "${value.take(6)}...${value.takeLast(4)}"
}
