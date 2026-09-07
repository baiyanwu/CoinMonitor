package io.baiyanwu.coinmonitor.ui.walletwatch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.WalletAsset
import io.baiyanwu.coinmonitor.ui.components.CoilCoinSymbolIcon
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WalletWatchRoute(container: AppContainer, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val viewModel: WalletWatchViewModel = viewModel(factory = WalletWatchViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WalletWatchScreen(
        state = state,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onAddressChange = viewModel::updateAddress,
        onQuery = { viewModel.query() },
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onIncludeRiskChange = viewModel::setIncludeRiskInTotal,
        onHideSmallAssetsChange = viewModel::setHideAssetsBelowOneUsd,
        onHideAsset = viewModel::hideAsset,
        onShowAsset = viewModel::showAsset,
        onSelectChain = viewModel::selectChain,
        onHideChain = viewModel::hideChain,
        onShowChain = viewModel::showChain
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletWatchScreen(
    state: WalletWatchUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddressChange: (String) -> Unit,
    onQuery: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onIncludeRiskChange: (Boolean) -> Unit,
    onHideSmallAssetsChange: (Boolean) -> Unit,
    onHideAsset: (String) -> Unit,
    onShowAsset: (String) -> Unit,
    onSelectChain: (String) -> Unit,
    onHideChain: (String) -> Unit,
    onShowChain: (String) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val context = LocalContext.current
    val addressInteractionSource = remember { MutableInteractionSource() }
    val addressFocused by addressInteractionSource.collectIsFocusedAsState()
    var showHiddenAssets by remember { mutableStateOf(false) }
    var chainsEditing by remember { mutableStateOf(false) }
    val hiddenChainAssets = remember(state.snapshot?.assets, state.hiddenChainIndexes) {
        state.snapshot?.assets.orEmpty()
            .filter { it.chainIndex in state.hiddenChainIndexes }
            .distinctBy(WalletAsset::chainIndex)
    }
    val hiddenAssets = remember(state.snapshot?.assets, state.hiddenAssetIds, state.hiddenChainIndexes) {
        state.snapshot?.assets.orEmpty().filter {
            it.id in state.hiddenAssetIds && it.chainIndex !in state.hiddenChainIndexes
        }
    }
    val copiedMessage = stringResource(R.string.wallet_watch_contract_copied)
    val copyContract: (String) -> Unit = { contractAddress ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Contract address", contractAddress))
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
    }
    Scaffold(
        containerColor = colors.pageBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.wallet_watch_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back)) } },
                colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.pageBackground)
            )
        },
        bottomBar = {
            if (hiddenChainAssets.isNotEmpty() || hiddenAssets.isNotEmpty()) {
                HiddenAssetsBottomBar(
                    chainCount = hiddenChainAssets.size,
                    assetCount = hiddenAssets.size,
                    onClick = { showHiddenAssets = true }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(
                        width = if (addressFocused || state.addressError) 2.dp else 1.dp,
                        color = if (state.addressError) MaterialTheme.colorScheme.error
                        else if (addressFocused) colors.accent else colors.divider
                    )
                ) {
                    BasicTextField(
                        value = state.address,
                        onValueChange = onAddressChange,
                        modifier = Modifier.fillMaxSize(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.primaryText,
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        ),
                        singleLine = true,
                        interactionSource = addressInteractionSource,
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onQuery() }),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (state.address.isBlank()) {
                                    Text(
                                        stringResource(R.string.wallet_watch_address_hint),
                                        color = colors.tertiaryText,
                                        fontSize = 14.sp,
                                        maxLines = 1
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                Button(
                    onClick = onQuery,
                    modifier = Modifier.height(44.dp),
                    enabled = !state.isInitialLoading,
                    colors = CoinMonitorComponentDefaults.primaryButtonColors()
                ) {
                    Text(stringResource(R.string.wallet_watch_query))
                }
            }
            if (state.addressError) {
                Text(
                    stringResource(R.string.wallet_watch_invalid_address),
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(6.dp))
            if (!state.secureStorageAvailable || !state.credentialsReady) {
                CredentialsRequiredCard(state.secureStorageAvailable, onOpenSettings)
            } else if (state.isInitialLoading) {
                LoadingWalletSkeleton()
            } else {
                val assets = state.snapshot?.assets.orEmpty()
                val selectedAssets = remember(
                    assets,
                    state.selectedChainIndex,
                    state.hideAssetsBelowOneUsd,
                    state.includeRiskInTotal,
                    state.hiddenAssetIds,
                    state.hiddenChainIndexes
                ) {
                    assets.filter {
                        it.id !in state.hiddenAssetIds &&
                            it.chainIndex !in state.hiddenChainIndexes &&
                            it.chainIndex == state.selectedChainIndex && shouldShowWalletAsset(
                            asset = it,
                            hideSmallAssets = state.hideAssetsBelowOneUsd,
                            hideRiskAssets = !state.includeRiskInTotal
                        )
                    }
                }
                val visiblePortfolioAssets = remember(
                    assets,
                    state.hideAssetsBelowOneUsd,
                    state.includeRiskInTotal,
                    state.hiddenAssetIds,
                    state.hiddenChainIndexes
                ) {
                    assets.filter {
                        it.id !in state.hiddenAssetIds &&
                            it.chainIndex !in state.hiddenChainIndexes &&
                            shouldShowWalletAsset(
                                asset = it,
                                hideSmallAssets = state.hideAssetsBelowOneUsd,
                                hideRiskAssets = !state.includeRiskInTotal
                            )
                    }
                }
                PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        state.snapshot?.let { snapshot ->
                            item {
                                PortfolioSummary(
                                    total = visibleWalletPortfolioTotal(visiblePortfolioAssets),
                                    count = visiblePortfolioAssets.size,
                                    updatedAtMillis = snapshot.updatedAtMillis,
                                    estimated = snapshot.totalIsEstimated,
                                    hideRiskAssets = !state.includeRiskInTotal,
                                    hideSmallAssets = state.hideAssetsBelowOneUsd,
                                    onHideRiskAssetsChange = { hide -> onIncludeRiskChange(!hide) },
                                    onHideSmallAssetsChange = onHideSmallAssetsChange
                                )
                            }
                            if (snapshot.assets.isNotEmpty()) {
                                item {
                                    ChainSelector(
                                        assets = snapshot.assets,
                                        selectedChainIndex = state.selectedChainIndex,
                                        hiddenChainIndexes = state.hiddenChainIndexes,
                                        editing = chainsEditing,
                                        onEditingChange = { chainsEditing = it },
                                        onSelectChain = onSelectChain,
                                        onHideChain = onHideChain
                                    )
                                }
                            }
                        }
                        state.errorMessage?.let { error ->
                            item { ErrorCard(error, onRetry, onOpenSettings, Modifier.padding(bottom = 10.dp)) }
                        }
                        if (state.snapshot != null && assets.isEmpty()) {
                            item { EmptyCard(stringResource(R.string.wallet_watch_no_assets)) }
                        } else if (state.snapshot == null && state.errorMessage == null) {
                            item { EmptyCard(stringResource(R.string.wallet_watch_empty_guide)) }
                        }
                        items(selectedAssets, key = WalletAsset::id) { asset ->
                            WalletAssetRow(asset = asset, onCopyContract = copyContract, onHide = { onHideAsset(asset.id) })
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
    if (showHiddenAssets && (hiddenChainAssets.isNotEmpty() || hiddenAssets.isNotEmpty())) {
        HiddenAssetsSheet(
            allAssets = state.snapshot?.assets.orEmpty(),
            hiddenChains = hiddenChainAssets,
            assets = hiddenAssets,
            onDismiss = { showHiddenAssets = false },
            onCopyContract = copyContract,
            onShowAsset = onShowAsset,
            onShowChain = onShowChain
        )
    }
}

@Composable
private fun CredentialsRequiredCard(secureStorageAvailable: Boolean, onOpenSettings: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CoinMonitorComponentDefaults.elevatedCardColors()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (secureStorageAvailable) stringResource(R.string.wallet_watch_credentials_required) else stringResource(R.string.okx_wallet_secure_storage_unavailable))
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wallet_watch_open_settings)) }
        }
    }
}

@Composable
private fun LoadingWalletSkeleton() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(28.dp))
        repeat(4) { Card(Modifier.fillMaxWidth().height(72.dp), shape = RoundedCornerShape(18.dp)) {} }
    }
}

@Composable
private fun PortfolioSummary(
    total: BigDecimal,
    count: Int,
    updatedAtMillis: Long,
    estimated: Boolean,
    hideRiskAssets: Boolean,
    hideSmallAssets: Boolean,
    onHideRiskAssetsChange: (Boolean) -> Unit,
    onHideSmallAssetsChange: (Boolean) -> Unit
) {
    var showDisplayOptions by remember { mutableStateOf(false) }
    Card(modifier = Modifier.padding(bottom = 6.dp), shape = RoundedCornerShape(16.dp), colors = CoinMonitorComponentDefaults.elevatedCardColors()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.wallet_watch_total_asset),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = CoinMonitorThemeTokens.colors.secondaryText
                )
                Box {
                    IconButton(onClick = { showDisplayOptions = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.wallet_watch_display_options), modifier = Modifier.size(19.dp))
                    }
                    DropdownMenu(expanded = showDisplayOptions, onDismissRequest = { showDisplayOptions = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.wallet_watch_hide_below_one_usd)) },
                            leadingIcon = { Checkbox(checked = hideSmallAssets, onCheckedChange = null) },
                            onClick = { onHideSmallAssetsChange(!hideSmallAssets) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.wallet_watch_hide_risk_assets)) },
                            leadingIcon = { Checkbox(checked = hideRiskAssets, onCheckedChange = null) },
                            onClick = { onHideRiskAssetsChange(!hideRiskAssets) }
                        )
                    }
                }
            }
            Text(stringResource(R.string.wallet_watch_usd_value, formatWalletValue(total)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (estimated) Text(stringResource(R.string.wallet_watch_total_estimated), style = MaterialTheme.typography.bodySmall, color = CoinMonitorThemeTokens.colors.secondaryText)
            Text(stringResource(R.string.wallet_watch_summary_meta, count, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(updatedAtMillis))), style = MaterialTheme.typography.labelSmall, color = CoinMonitorThemeTokens.colors.secondaryText)
        }
    }
}

@Composable
private fun ChainSelector(
    assets: List<WalletAsset>,
    selectedChainIndex: String?,
    hiddenChainIndexes: Set<String>,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSelectChain: (String) -> Unit,
    onHideChain: (String) -> Unit
) {
    val chains = assets.filter { it.chainIndex !in hiddenChainIndexes }.distinctBy(WalletAsset::chainIndex)
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "chain_edit") {
            Surface(
                modifier = Modifier.size(32.dp).clickable { onEditingChange(!editing) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (editing) Icons.Rounded.Done else Icons.Rounded.Edit,
                        contentDescription = stringResource(if (editing) R.string.wallet_watch_finish_chain_edit else R.string.wallet_watch_edit_chains),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
        items(chains, key = WalletAsset::chainIndex) { chain ->
            val selected = chain.chainIndex == selectedChainIndex
            Box(modifier = Modifier.padding(top = if (editing) 4.dp else 0.dp, end = if (editing) 4.dp else 0.dp)) {
                Surface(
                    modifier = Modifier.clickable { onSelectChain(chain.chainIndex) },
                    shape = RoundedCornerShape(50),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Text(
                        text = chain.chainName,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
                if (editing) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp).size(18.dp).clickable {
                            onHideChain(chain.chainIndex)
                        },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.wallet_watch_hide_chain, chain.chainName),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletAssetRow(asset: WalletAsset, onCopyContract: (String) -> Unit, onHide: () -> Unit) {
    var showHideMenu by remember { mutableStateOf(false) }
    var longPressPosition by remember { mutableStateOf(Offset.Zero) }
    val hideLabel = stringResource(R.string.wallet_watch_hide_asset, asset.symbol)
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(asset.id) {
                    detectTapGestures(onLongPress = { position ->
                        longPressPosition = position
                        showHideMenu = true
                    })
                }
                .semantics {
                    onLongClick(label = hideLabel) {
                        longPressPosition = Offset.Zero
                        showHideMenu = true
                        true
                    }
                }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CoilCoinSymbolIcon(symbol = asset.symbol, size = 32.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(asset.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        asset.tokenPriceUsd?.let { stringResource(R.string.wallet_watch_usd_value, formatWalletPrice(it)) }
                            ?: stringResource(R.string.wallet_watch_no_price),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoinMonitorThemeTokens.colors.secondaryText
                    )
                    if (asset.isRiskToken) Text(stringResource(R.string.wallet_watch_risk), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                if (asset.contractAddress.isBlank()) {
                    Text(stringResource(R.string.wallet_watch_native_token), style = MaterialTheme.typography.bodySmall, color = CoinMonitorThemeTokens.colors.secondaryText)
                } else {
                    ContractAddressLine(asset.contractAddress, onCopyContract)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    asset.holdingValueUsd?.let { stringResource(R.string.wallet_watch_usd_value, formatWalletValue(it)) }
                        ?: stringResource(R.string.wallet_watch_no_price),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    formatWalletQuantity(asset.balance),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoinMonitorThemeTokens.colors.secondaryText
                )
            }
        }
        Box(
            modifier = Modifier
                .size(1.dp)
                .offset {
                    IntOffset(longPressPosition.x.roundToInt(), longPressPosition.y.roundToInt())
                }
        ) {
            DropdownMenu(
                expanded = showHideMenu,
                onDismissRequest = { showHideMenu = false },
                modifier = Modifier.widthIn(min = 108.dp, max = 132.dp)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.wallet_watch_hide_action), style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showHideMenu = false
                        onHide()
                    }
                )
            }
        }
    }
}

@Composable
private fun HiddenAssetsBottomBar(chainCount: Int, assetCount: Int, onClick: () -> Unit) {
    val colors = CoinMonitorThemeTokens.colors
    Surface(color = colors.pageBackground) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable(onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            color = colors.cardBackground,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.wallet_watch_hidden_summary, chainCount, assetCount),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge
                )
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenAssetsSheet(
    allAssets: List<WalletAsset>,
    hiddenChains: List<WalletAsset>,
    assets: List<WalletAsset>,
    onDismiss: () -> Unit,
    onCopyContract: (String) -> Unit,
    onShowAsset: (String) -> Unit,
    onShowChain: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.wallet_watch_hidden_assets_title),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(bottom = 18.dp)) {
            if (hiddenChains.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.wallet_watch_hidden_chains_section),
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = CoinMonitorThemeTokens.colors.secondaryText
                    )
                }
                items(hiddenChains, key = { "hidden_chain:${it.chainIndex}" }) { chain ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(chain.chainName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(
                                    R.string.wallet_watch_hidden_chain_asset_count,
                                    allAssets.count { it.chainIndex == chain.chainIndex }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = CoinMonitorThemeTokens.colors.secondaryText
                            )
                        }
                        IconButton(onClick = { onShowChain(chain.chainIndex) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Rounded.Visibility,
                                contentDescription = stringResource(R.string.wallet_watch_show_chain, chain.chainName),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
            if (assets.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.wallet_watch_hidden_assets_section),
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = CoinMonitorThemeTokens.colors.secondaryText
                    )
                }
            }
            items(assets, key = WalletAsset::id) { asset ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(asset.symbol, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(asset.chainName, style = MaterialTheme.typography.labelSmall, color = CoinMonitorThemeTokens.colors.secondaryText)
                        }
                        if (asset.contractAddress.isBlank()) {
                            Text(stringResource(R.string.wallet_watch_native_token), style = MaterialTheme.typography.bodySmall, color = CoinMonitorThemeTokens.colors.secondaryText)
                        } else {
                            ContractAddressLine(asset.contractAddress, onCopyContract)
                        }
                    }
                    Text(formatWalletQuantity(asset.balance), style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { onShowAsset(asset.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.VisibilityOff,
                            contentDescription = stringResource(R.string.wallet_watch_show_asset, asset.symbol),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContractAddressLine(contractAddress: String, onCopyContract: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            shortenWalletContract(contractAddress),
            modifier = Modifier.clickable { onCopyContract(contractAddress) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        IconButton(onClick = { onCopyContract(contractAddress) }, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.home_copy_ca_description),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable private fun EmptyCard(text: String) { Card(shape = RoundedCornerShape(18.dp)) { Text(text, Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodyMedium) } }
@Composable private fun ErrorCard(message: String, onRetry: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) { Card(modifier = modifier, shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onRetry) { Text(stringResource(R.string.wallet_watch_retry)) }; Button(onClick = onOpenSettings) { Text(stringResource(R.string.wallet_watch_api_settings)) } } } } }

internal fun formatWalletValue(value: BigDecimal): String = formatWalletNumber(value, maxFractionDigits = 2, tinyThreshold = BigDecimal("0.01"))
internal fun formatWalletPrice(value: BigDecimal): String = formatWalletNumber(value, maxFractionDigits = 8, tinyThreshold = BigDecimal("0.00000001"))
internal fun formatWalletQuantity(value: BigDecimal): String = formatWalletNumber(value, maxFractionDigits = 8, tinyThreshold = BigDecimal("0.00000001"))

private fun formatWalletNumber(value: BigDecimal, maxFractionDigits: Int, tinyThreshold: BigDecimal): String {
    if (value.compareTo(BigDecimal.ZERO) == 0) return "0"
    if (value.abs() < tinyThreshold) return "<${tinyThreshold.stripTrailingZeros().toPlainString()}"
    val rounded = value.setScale(maxFractionDigits, RoundingMode.HALF_UP).stripTrailingZeros()
    return DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = maxFractionDigits
        roundingMode = RoundingMode.HALF_UP
    }.format(rounded)
}

internal fun shouldShowWalletAsset(asset: WalletAsset, hideSmallAssets: Boolean, hideRiskAssets: Boolean): Boolean =
    (!hideRiskAssets || !asset.isRiskToken) &&
        (!hideSmallAssets || (asset.holdingValueUsd != null && asset.holdingValueUsd >= BigDecimal.ONE))

internal fun visibleWalletPortfolioTotal(assets: List<WalletAsset>): BigDecimal =
    assets.mapNotNull(WalletAsset::holdingValueUsd).fold(BigDecimal.ZERO, BigDecimal::add)

internal fun shortenWalletContract(value: String): String = if (value.length <= 10) value else "${value.take(6)}…${value.takeLast(4)}"
