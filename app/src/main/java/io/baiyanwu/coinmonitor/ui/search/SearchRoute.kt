package io.baiyanwu.coinmonitor.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.ui.components.CoinSymbolIcon
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

private const val TAB_ALL = 0
private const val TAB_BINANCE_FAMILY = 1
private const val TAB_OKX = 2
private const val TAB_ONCHAIN_SOL = 3
private const val TAB_ONCHAIN_EVM = 4
private const val SOLANA_CHAIN_INDEX = "501"
private const val DEFAULT_EVM_CHAIN_INDEX = "1"
private const val MIN_SOL_ADDRESS_LENGTH = 32
private const val MAX_SOL_ADDRESS_LENGTH = 44

private enum class ChainFamilyLabel {
    EVM,
    SOL,
    UNKNOWN
}

private data class OnchainChainOption(
    val label: String,
    val chainIndex: String
)

/**
 * 链上页签先只暴露当前产品要用的主流链，并直接复用 chainIndex 做前端筛选，
 * 这样不需要额外拉一轮链元数据，也能让用户快速切到目标链。
 */
private val ONCHAIN_EVM_OPTIONS = listOf(
    OnchainChainOption(label = "ETH", chainIndex = "1"),
    OnchainChainOption(label = "Base", chainIndex = "8453"),
    OnchainChainOption(label = "BSC", chainIndex = "56"),
    OnchainChainOption(label = "Arbitrum", chainIndex = "42161"),
    OnchainChainOption(label = "Polygon", chainIndex = "137"),
    OnchainChainOption(label = "Optimism", chainIndex = "10"),
    OnchainChainOption(label = "Avalanche", chainIndex = "43114"),
    OnchainChainOption(label = "Linea", chainIndex = "59144"),
    OnchainChainOption(label = "Scroll", chainIndex = "534352"),
    OnchainChainOption(label = "Blast", chainIndex = "81457"),
    OnchainChainOption(label = "Mode", chainIndex = "34443"),
    OnchainChainOption(label = "Mantle", chainIndex = "5000"),
    OnchainChainOption(label = "Polygon zkEVM", chainIndex = "1101"),
    OnchainChainOption(label = "zkSync", chainIndex = "324"),
    OnchainChainOption(label = "Fantom", chainIndex = "250"),
    OnchainChainOption(label = "Zeta", chainIndex = "7000")
)

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
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearch: (OnchainSearchSelection?) -> Unit,
    onSearchModeChange: (SearchMode) -> Unit,
    onToggleItem: (WatchItem) -> Unit,
    onSelectForKline: (WatchItem) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    var exchangeTab by rememberSaveable { mutableIntStateOf(TAB_ALL) }
    var onchainTab by rememberSaveable { mutableIntStateOf(TAB_ONCHAIN_EVM) }
    var selectedEvmChainIndex by rememberSaveable { mutableStateOf(DEFAULT_EVM_CHAIN_INDEX) }
    val selectedEvmChain = remember(selectedEvmChainIndex) {
        ONCHAIN_EVM_OPTIONS.firstOrNull { it.chainIndex == selectedEvmChainIndex } ?: ONCHAIN_EVM_OPTIONS.first()
    }

    val sortedResults = remember(state.results, state.searchMode) {
        when (state.searchMode) {
            SearchMode.EXCHANGE -> state.results.sortedWith(
                compareBy<WatchItem>(
                    { ExchangeSource.sortRank(it.exchangeSource) },
                    { it.symbol }
                )
            )

            SearchMode.ONCHAIN -> state.results.sortedWith(
                compareBy<WatchItem>(
                    { chainRank(it) },
                    { it.symbol.uppercase() },
                    { it.name.uppercase() }
                )
            )
        }
    }
    val modeResults = remember(sortedResults, state.searchMode) {
        when (state.searchMode) {
            SearchMode.EXCHANGE -> sortedResults.filterNot(::isOnchainItem)
            SearchMode.ONCHAIN -> sortedResults.filter(::isOnchainItem)
        }
    }
    val filteredResults = remember(
        modeResults,
        state.searchMode,
        exchangeTab,
        onchainTab,
        selectedEvmChainIndex
    ) {
        when (state.searchMode) {
            SearchMode.EXCHANGE -> when (exchangeTab) {
                TAB_BINANCE_FAMILY -> modeResults.filter {
                    it.exchangeSource == ExchangeSource.BINANCE || it.exchangeSource == ExchangeSource.BINANCE_ALPHA
                }

                TAB_OKX -> modeResults.filter { it.exchangeSource == ExchangeSource.OKX }
                else -> modeResults
            }

            SearchMode.ONCHAIN -> when (onchainTab) {
                TAB_ONCHAIN_SOL -> modeResults.filter { inferChainFamily(it) == ChainFamilyLabel.SOL }
                TAB_ONCHAIN_EVM -> modeResults.filter { resolveChainIndex(it) == selectedEvmChainIndex }
                else -> modeResults
            }
        }
    }
    val showCredentialHint = state.searchMode == SearchMode.ONCHAIN && !state.hasOkxCredentials

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
    ) {
        SearchHeader(
            state = state,
            onBack = onBack,
            onQueryChange = onQueryChange,
            onClearQuery = onClearQuery,
            onSearch = {
                val onchainSelection = if (state.searchMode == SearchMode.ONCHAIN) {
                    val chainIndex = if (onchainTab == TAB_ONCHAIN_SOL) {
                        SOLANA_CHAIN_INDEX
                    } else {
                        selectedEvmChainIndex
                    }
                    OnchainSearchSelection(chainIndex = chainIndex)
                } else {
                    null
                }
                onSearch(onchainSelection)
            },
            onSearchModeChange = onSearchModeChange
        )

        SearchTabs(
            searchMode = state.searchMode,
            selectedExchangeTab = exchangeTab,
            onSelectExchangeTab = { exchangeTab = it },
            selectedOnchainTab = onchainTab,
            selectedOnchainChain = selectedEvmChain,
            onSelectOnchainTab = { onchainTab = it },
            onSelectOnchainChain = { option ->
                selectedEvmChainIndex = option.chainIndex
                onchainTab = TAB_ONCHAIN_EVM
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (showCredentialHint) {
                item("search_onchain_credential_hint") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        color = colors.cardBackground,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.search_onchain_credential_tip),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = colors.secondaryText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (state.errorMessage != null && !showCredentialHint) {
                item("search_error") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        color = colors.cardBackground,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = state.errorMessage,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else if (filteredResults.isEmpty() && !state.loading) {
                item("search_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 56.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val emptyTextRes = when {
                            showCredentialHint -> R.string.search_onchain_credential_missing
                            state.hasSearched -> R.string.search_empty_result
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

            if (filteredResults.isNotEmpty()) {
                item("search_result_list") {
                    ElevatedCard(
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            filteredResults.forEach { item ->
                                SearchResultRow(
                                    item = item,
                                    entryMode = entryMode,
                                    searchMode = state.searchMode,
                                    added = state.addedIds.contains(item.id),
                                    onToggleItem = onToggleItem,
                                    onSelectForKline = onSelectForKline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(
    state: SearchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearch: () -> Unit,
    onSearchModeChange: (SearchMode) -> Unit
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
    val placeholderRes = if (state.searchMode == SearchMode.ONCHAIN) {
        R.string.search_input_hint_onchain
    } else {
        R.string.search_input_hint
    }
    var modeMenuExpanded by remember { mutableStateOf(false) }
    val selectedModeLabel = if (state.searchMode == SearchMode.ONCHAIN) {
        stringResource(R.string.search_mode_onchain)
    } else {
        stringResource(R.string.search_mode_exchange)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.pageBackground)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    modifier = Modifier
                        .height(40.dp)
                        .clickable { modeMenuExpanded = true },
                    color = colors.cardBackground,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedModeLabel,
                            color = colors.primaryText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.secondaryText
                        )
                    }
                }
                DropdownMenu(
                    expanded = modeMenuExpanded,
                    onDismissRequest = { modeMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.search_mode_exchange)) },
                        onClick = {
                            modeMenuExpanded = false
                            onSearchModeChange(SearchMode.EXCHANGE)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.search_mode_onchain)) },
                        onClick = {
                            modeMenuExpanded = false
                            onSearchModeChange(SearchMode.ONCHAIN)
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                color = colors.cardBackground,
                shape = RoundedCornerShape(18.dp)
            ) {
                BasicTextField(
                    value = state.query,
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
                            if (state.loading) {
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
                                if (state.query.isBlank()) {
                                    Text(
                                        text = stringResource(placeholderRes),
                                        color = colors.tertiaryText,
                                        style = placeholderTextStyle,
                                        maxLines = 1
                                    )
                                }
                                innerTextField()
                            }

                            if (state.query.isNotBlank()) {
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
}

@Composable
private fun SearchTabs(
    searchMode: SearchMode,
    selectedExchangeTab: Int,
    onSelectExchangeTab: (Int) -> Unit,
    selectedOnchainTab: Int,
    selectedOnchainChain: OnchainChainOption,
    onSelectOnchainTab: (Int) -> Unit,
    onSelectOnchainChain: (OnchainChainOption) -> Unit
) {
    when (searchMode) {
        SearchMode.EXCHANGE -> ExchangeSearchTabs(
            selectedTab = selectedExchangeTab,
            onSelectTab = onSelectExchangeTab
        )

        SearchMode.ONCHAIN -> OnchainSearchTabs(
            selectedTab = selectedOnchainTab,
            selectedChain = selectedOnchainChain,
            onSelectSol = { onSelectOnchainTab(TAB_ONCHAIN_SOL) },
            onSelectChain = onSelectOnchainChain
        )
    }
}

@Composable
private fun ExchangeSearchTabs(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.pageBackground)
            .padding(horizontal = 14.dp)
    ) {
        val itemWidth = (maxWidth - 8.dp) / 3
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SearchTabButton(
                selected = selectedTab == TAB_ALL,
                onClick = { onSelectTab(TAB_ALL) },
                shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
                label = stringResource(R.string.search_tab_all),
                modifier = Modifier.width(itemWidth),
                selectedContainerColor = colors.chipSelectedContainer,
                unselectedContainerColor = colors.cardBackground
            )
            SearchTabButton(
                selected = selectedTab == TAB_BINANCE_FAMILY,
                onClick = { onSelectTab(TAB_BINANCE_FAMILY) },
                shape = RoundedCornerShape(4.dp),
                label = stringResource(R.string.search_tab_binance_family),
                modifier = Modifier.width(itemWidth),
                selectedContainerColor = colors.chipSelectedContainer,
                unselectedContainerColor = colors.cardBackground
            )
            SearchTabButton(
                selected = selectedTab == TAB_OKX,
                onClick = { onSelectTab(TAB_OKX) },
                shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
                label = stringResource(R.string.search_tab_okx),
                modifier = Modifier.width(itemWidth),
                selectedContainerColor = colors.chipSelectedContainer,
                unselectedContainerColor = colors.cardBackground
            )
        }
    }
}

@Composable
private fun OnchainSearchTabs(
    selectedTab: Int,
    selectedChain: OnchainChainOption,
    onSelectSol: () -> Unit,
    onSelectChain: (OnchainChainOption) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    var chainMenuExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.pageBackground)
            .padding(horizontal = 14.dp)
    ) {
        val itemWidth = (maxWidth - 4.dp) / 2
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.width(itemWidth)) {
                DropdownSearchTabButton(
                    selected = selectedTab == TAB_ONCHAIN_EVM,
                    label = selectedChain.label,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
                    selectedContainerColor = colors.chipSelectedContainer,
                    unselectedContainerColor = colors.cardBackground,
                    onClick = { chainMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = chainMenuExpanded,
                    onDismissRequest = { chainMenuExpanded = false },
                    modifier = Modifier
                        .width(itemWidth)
                        .heightIn(max = 280.dp),
                    offset = DpOffset(x = 0.dp, y = 4.dp)
                ) {
                    ONCHAIN_EVM_OPTIONS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = option.label) },
                            onClick = {
                                chainMenuExpanded = false
                                onSelectChain(option)
                            }
                        )
                    }
                }
            }
            SearchTabButton(
                selected = selectedTab == TAB_ONCHAIN_SOL,
                onClick = onSelectSol,
                shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
                label = stringResource(R.string.search_tab_sol),
                modifier = Modifier.width(itemWidth),
                selectedContainerColor = colors.chipSelectedContainer,
                unselectedContainerColor = colors.cardBackground
            )
        }
    }
}

@Composable
private fun SearchTabButton(
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    label: String,
    modifier: Modifier = Modifier,
    selectedContainerColor: androidx.compose.ui.graphics.Color,
    unselectedContainerColor: androidx.compose.ui.graphics.Color
) {
    val colors = CoinMonitorThemeTokens.colors
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = shape,
        color = if (selected) selectedContainerColor else unselectedContainerColor,
        tonalElevation = if (selected) 1.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) colors.chipSelectedContent else colors.secondaryText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DropdownSearchTabButton(
    selected: Boolean,
    label: String,
    shape: Shape,
    selectedContainerColor: androidx.compose.ui.graphics.Color,
    unselectedContainerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = shape,
        color = if (selected) selectedContainerColor else unselectedContainerColor,
        tonalElevation = if (selected) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (selected) colors.chipSelectedContent else colors.secondaryText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) colors.chipSelectedContent else colors.secondaryText
            )
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
    onSelectForKline: (WatchItem) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val onchainMode = searchMode == SearchMode.ONCHAIN
    val klineEntry = entryMode == SearchEntryMode.KLINE

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = klineEntry) { onSelectForKline(item) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoinSymbolIcon(item = item, modifier = Modifier.size(18.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val titleText = if (onchainMode) {
                resolveOnchainSymbol(item)
            } else {
                item.baseSymbol
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleSmall,
                color = colors.primaryText,
                fontWeight = FontWeight.SemiBold
            )
            if (onchainMode) {
                Text(
                    text = resolveOnchainSubtitle(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                    maxLines = 1
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.symbol.substringAfter("/", ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
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

private fun resolveOnchainSubtitle(item: WatchItem): String {
    val shortAddress = resolveOnchainIdentity(item)
        .takeIf { it.isNotBlank() }
        ?.let(::shortenAddress)
        ?: "--"
    return "${resolveOnchainChainLabel(item)} | $shortAddress"
}

private fun resolveOnchainChainLabel(item: WatchItem): String {
    val chainIndex = resolveChainIndex(item)
    if (chainIndex == SOLANA_CHAIN_INDEX) return "SOL"
    val optionLabel = ONCHAIN_EVM_OPTIONS.firstOrNull { it.chainIndex == chainIndex }?.label
    if (optionLabel != null) return optionLabel
    return when (inferChainFamily(item)) {
        ChainFamilyLabel.EVM -> "EVM"
        ChainFamilyLabel.SOL -> "SOL"
        ChainFamilyLabel.UNKNOWN -> "CHAIN"
    }
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

private fun shortenAddress(value: String): String {
    if (value.length <= 10) return value
    return "${value.take(6)}...${value.takeLast(4)}"
}
