package io.baiyanwu.coinmonitor.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.data.repository.DappAddressParser
import io.baiyanwu.coinmonitor.domain.model.DappCategory
import io.baiyanwu.coinmonitor.domain.model.DappDefinition
import io.baiyanwu.coinmonitor.ui.components.CompactSearchField
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import java.util.Locale

@Composable
fun DappDiscoveryRoute(
    container: AppContainer,
    contentTopInset: Dp,
    contentBottomInset: Dp,
    onOpenBrowser: (String) -> Unit
) {
    val viewModel: DappDiscoveryViewModel = viewModel(
        factory = DappDiscoveryViewModel.factory(container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingThirdPartyAddress by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedCategory = selectedCategoryName?.let { value ->
        runCatching { DappCategory.valueOf(value) }.getOrNull()
    }
    val filteredDapps = remember(state.catalog, query, selectedCategory) {
        state.catalog.filter { dapp ->
            (selectedCategory == null || dapp.category == selectedCategory) &&
                dapp.matches(query)
        }
    }
    val webAddressSuggestion = remember(query) {
        DappAddressParser.normalizeUserWebAddress(query)
    }
    val submitSearch = {
        val submitted = query.trim()
        if (submitted.isNotBlank()) {
            viewModel.recordSearch(submitted)
            focusManager.clearFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoinMonitorThemeTokens.colors.pageBackground)
            .padding(top = contentTopInset + 12.dp)
    ) {
        CompactSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.dapp_search_hint),
            onSearch = submitSearch,
            onClear = { query = "" },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            keyboardType = KeyboardType.Uri
        )

        webAddressSuggestion?.let { address ->
            WebAddressSuggestion(
                address = address,
                onClick = {
                    focusManager.clearFocus()
                    pendingThirdPartyAddress = address
                }
            )
        }

        if (state.history.isNotEmpty()) {
            SectionHeader(
                title = stringResource(R.string.dapp_search_history),
                action = stringResource(R.string.dapp_search_clear_history),
                onAction = viewModel::clearHistory
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.history, key = { it.query.lowercase(Locale.ROOT) }) { item ->
                    AssistChip(
                        onClick = { query = item.query },
                        label = {
                            Text(
                                text = item.query,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = CoinMonitorComponentDefaults.assistChipColors()
                    )
                }
            }
        }

        LazyRow(
            modifier = Modifier.padding(top = if (state.history.isEmpty()) 14.dp else 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategoryName = null },
                    label = { Text(stringResource(R.string.dapp_category_all)) },
                    colors = CoinMonitorComponentDefaults.filterChipColors()
                )
            }
            items(DappCategory.entries, key = DappCategory::name) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategoryName = category.name },
                    label = { Text(stringResource(category.labelRes())) },
                    colors = CoinMonitorComponentDefaults.filterChipColors()
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = contentBottomInset + 16.dp)
        ) {
            item(key = "catalog-title") {
                SectionHeader(
                    title = stringResource(
                        if (query.isBlank()) R.string.dapp_common_title else R.string.dapp_search_results
                    )
                )
            }

            if (filteredDapps.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.Language,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = CoinMonitorThemeTokens.colors.tertiaryText
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.dapp_search_empty),
                            color = CoinMonitorThemeTokens.colors.secondaryText
                        )
                    }
                }
            } else {
                items(filteredDapps, key = DappDefinition::id) { dapp ->
                    DappRow(dapp = dapp, onClick = { onOpenBrowser(dapp.url) })
                }
            }
        }
    }

    pendingThirdPartyAddress?.let { address ->
        ThirdPartyWebsiteWarningDialog(
            address = address,
            onDismiss = { pendingThirdPartyAddress = null },
            onContinue = {
                val submitted = query.trim()
                viewModel.recordSearch(submitted)
                query = ""
                pendingThirdPartyAddress = null
                onOpenBrowser(address)
            }
        )
    }
}

@Composable
private fun WebAddressSuggestion(address: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = CoinMonitorThemeTokens.colors.cardBackground,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = CoinMonitorThemeTokens.colors.accent
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dapp_open_web_address),
                    style = MaterialTheme.typography.labelMedium,
                    color = CoinMonitorThemeTokens.colors.secondaryText
                )
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoinMonitorThemeTokens.colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = CoinMonitorThemeTokens.colors.tertiaryText
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = CoinMonitorThemeTokens.colors.primaryText,
            modifier = Modifier.weight(1f)
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun DappRow(dapp: DappDefinition, onClick: () -> Unit) {
    val configuration = LocalConfiguration.current
    val isChinese = configuration.locales[0].language.startsWith("zh")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(parseColor(dapp.iconColor)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dapp.iconLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dapp.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = CoinMonitorThemeTokens.colors.primaryText,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(dapp.category.labelRes()),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoinMonitorThemeTokens.colors.accent
                    )
                }
                Text(
                    text = dapp.domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoinMonitorThemeTokens.colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isChinese) dapp.descriptionZh else dapp.descriptionEn,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoinMonitorThemeTokens.colors.tertiaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val networks = dapp.chainIds.toNetworkLabels()
                if (networks.isNotEmpty()) {
                    Text(
                        text = networks,
                        style = MaterialTheme.typography.labelSmall,
                        color = CoinMonitorThemeTokens.colors.tertiaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = CoinMonitorThemeTokens.colors.tertiaryText
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 56.dp),
            color = CoinMonitorThemeTokens.colors.divider.copy(alpha = 0.55f)
        )
    }
}

private fun DappDefinition.matches(query: String): Boolean {
    val normalized = query.trim()
    if (normalized.isBlank()) return true
    return name.contains(normalized, ignoreCase = true) ||
        domain.contains(normalized, ignoreCase = true) ||
        descriptionZh.contains(normalized, ignoreCase = true) ||
        descriptionEn.contains(normalized, ignoreCase = true) ||
        category.name.contains(normalized, ignoreCase = true)
}

private fun DappCategory.labelRes(): Int = when (this) {
    DappCategory.SWAP -> R.string.dapp_category_swap
    DappCategory.LENDING -> R.string.dapp_category_lending
    DappCategory.BRIDGE -> R.string.dapp_category_bridge
    DappCategory.NFT -> R.string.dapp_category_nft
    DappCategory.DATA -> R.string.dapp_category_data
}

private fun List<Long>.toNetworkLabels(): String = take(3).joinToString(" · ") { chainId ->
    when (chainId) {
        1L -> "Ethereum"
        56L -> "BNB"
        8453L -> "Base"
        42161L -> "Arbitrum"
        10L -> "Optimism"
        137L -> "Polygon"
        else -> chainId.toString()
    }
}

private fun parseColor(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color(0xFF6750A4))
