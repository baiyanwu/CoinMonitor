package io.coinbar.tokenmonitor.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.coinbar.tokenmonitor.R
import io.coinbar.tokenmonitor.data.AppContainer
import io.coinbar.tokenmonitor.domain.model.ExchangeSource
import io.coinbar.tokenmonitor.domain.model.WatchItem
import io.coinbar.tokenmonitor.ui.components.CoinSymbolIcon
import io.coinbar.tokenmonitor.ui.theme.TokenMonitorThemeTokens

private const val TAB_ALL = 0
private const val TAB_BINANCE_FAMILY = 1
private const val TAB_OKX = 2

@Composable
fun SearchRoute(
    container: AppContainer,
    onBack: () -> Unit
) {
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))
    val state by viewModel.uiState.collectAsState()
    SearchScreen(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::updateQuery,
        onClearQuery = viewModel::clearQuery,
        onSearch = viewModel::search,
        onToggleItem = viewModel::toggleWatchItem
    )
}

@Composable
private fun SearchScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearch: () -> Unit,
    onToggleItem: (WatchItem) -> Unit
) {
    val colors = TokenMonitorThemeTokens.colors
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_ALL) }
    val sortedResults = remember(state.results) {
        state.results.sortedWith(
            compareBy<WatchItem>(
                { ExchangeSource.sortRank(it.exchangeSource) },
                { it.symbol }
            )
        )
    }
    val filteredResults = remember(sortedResults, selectedTab) {
        when (selectedTab) {
            TAB_BINANCE_FAMILY -> sortedResults.filter {
                it.exchangeSource == ExchangeSource.BINANCE || it.exchangeSource == ExchangeSource.BINANCE_ALPHA
            }

            TAB_OKX -> sortedResults.filter { it.exchangeSource == ExchangeSource.OKX }
            else -> sortedResults
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        SearchHeader(
            state = state,
            onBack = onBack,
            onQueryChange = onQueryChange,
            onClearQuery = onClearQuery,
            onSearch = onSearch
        )

        if (sortedResults.isNotEmpty()) {
            SearchTabs(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it }
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (state.errorMessage != null) {
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
                        Text(
                            text = stringResource(
                                if (state.hasSearched) {
                                    R.string.search_empty_result
                                } else {
                                    R.string.search_empty_initial
                                }
                            ),
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
                                    selectedTab = selectedTab,
                                    added = state.addedIds.contains(item.id),
                                    onToggleItem = onToggleItem
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
    onSearch: () -> Unit
) {
    val colors = TokenMonitorThemeTokens.colors
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.pageBackground)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colors.accent
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (state.query.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.search_input_hint),
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

        if (state.loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(14.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = stringResource(R.string.search_loading),
                    color = colors.secondaryText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SearchTabs(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val colors = TokenMonitorThemeTokens.colors
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
                modifier = Modifier.width(itemWidth)
            )
            SearchTabButton(
                selected = selectedTab == TAB_BINANCE_FAMILY,
                onClick = { onSelectTab(TAB_BINANCE_FAMILY) },
                shape = RoundedCornerShape(4.dp),
                label = stringResource(R.string.search_tab_binance_family),
                modifier = Modifier.width(itemWidth)
            )
            SearchTabButton(
                selected = selectedTab == TAB_OKX,
                onClick = { onSelectTab(TAB_OKX) },
                shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
                label = stringResource(R.string.search_tab_okx),
                modifier = Modifier.width(itemWidth)
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
    modifier: Modifier = Modifier
) {
    val colors = TokenMonitorThemeTokens.colors
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = shape,
        color = if (selected) colors.chipSelectedContainer else colors.cardBackground,
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
private fun SearchResultRow(
    item: WatchItem,
    selectedTab: Int,
    added: Boolean,
    onToggleItem: (WatchItem) -> Unit
) {
    val colors = TokenMonitorThemeTokens.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoinSymbolIcon(
            symbol = item.baseSymbol,
            modifier = Modifier.size(18.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buildAnnotatedString {
                    val base = item.baseSymbol
                    val quote = item.symbol.substringAfter("/", "")
                    withStyle(
                        SpanStyle(
                            color = colors.primaryText,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(base)
                    }
                    if (quote.isNotBlank()) {
                        append("/")
                        withStyle(
                            SpanStyle(
                                color = colors.secondaryText,
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(quote)
                        }
                    }
                },
                style = MaterialTheme.typography.titleSmall
            )
            if (selectedTab == TAB_BINANCE_FAMILY && item.exchangeSource == ExchangeSource.BINANCE_ALPHA) {
                Surface(
                    color = colors.accent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = stringResource(R.string.search_tag_alpha),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = colors.accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
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
