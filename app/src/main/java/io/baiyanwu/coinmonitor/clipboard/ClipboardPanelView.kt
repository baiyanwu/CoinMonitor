package io.baiyanwu.coinmonitor.clipboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.domain.model.*
import io.baiyanwu.coinmonitor.overlay.QuoteFormatter
import io.baiyanwu.coinmonitor.ui.components.*
import io.baiyanwu.coinmonitor.ui.theme.*
import io.baiyanwu.coinmonitor.ui.format.AssetAmountFormatter
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ClipboardPanelView(
    state: ClipboardPanelState, settings: ClipboardSettings, watchlist: List<WatchItem>, anchor: () -> IntArray,
    onDismiss: () -> Unit, onRetryRead: () -> Unit, onFloatingChange: (Boolean, WatchItem?) -> Unit,
    onToggleWatchlist: (ClipboardMatch, WatchItem?) -> Unit, onOpen: (String) -> Unit, onSettings: () -> Unit,
    onOverlayItems: () -> Unit, onCopy: (String) -> Unit, onValueModeChange: (String) -> Unit, onWalletSettings: () -> Unit, onTrackAddress: (String, String, WalletPortfolioSnapshot?, String?) -> Unit
) = CoinMonitorDesignTheme { ClipboardCompactTheme {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
        Layout(content = {
            Surface(shape = RoundedCornerShape(14.dp), color = CoinMonitorThemeTokens.design.colors.surface,
                border = BorderStroke(0.5.dp, CoinMonitorThemeTokens.design.colors.divider), shadowElevation = 6.dp) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (state.mode == ClipboardContentMode.FOMO) FomoResult(state, onCopy, onOpen, onTrackAddress)
                    else {
                        QueryHeader(state, settings, watchlist, onCopy, onFloatingChange, onToggleWatchlist, onTrackAddress)
                        if (state.fomoWallets != null) ClipboardTextButton(onClick = state::showFomoResult) { Text(stringResource(R.string.clipboard_fomo_back)) }
                        else if (state.supportsWallet) QueryModeControls(state)
                        if (state.mode == ClipboardContentMode.WALLET) WalletResult(state, onOpen, onWalletSettings)
                        else if (state.selected != null) TokenResult(state, settings, onOpen, onValueModeChange, onOverlayItems, onSettings)
                        else PendingResult(state, onRetryRead)
                        state.actionMessage?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                    if (state.mode != ClipboardContentMode.TOKEN || state.selected == null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
                            QueryOverlayItemsButton(onOverlayItems)
                            QuerySettingsButton(onSettings)
                        }
                    }
                }
            }
        }) { measurables, constraints ->
            val margin = 12.dp.roundToPx()
            val width = (constraints.maxWidth - margin * 2).coerceAtLeast(0)
            val height = (constraints.maxHeight - margin * 2).coerceAtLeast(0)
            val measure = clipboardPanelMeasure(width, height, constraints.maxHeight, 250.dp.roundToPx(), 250.dp.roundToPx())
            val child = measurables.single().measure(Constraints(minWidth = measure.width, maxWidth = measure.width, maxHeight = measure.maxHeight))
            val coordinates = anchor()
            val position = clipboardPanelPosition(coordinates[0] - margin, coordinates[1] - margin, coordinates[2] - margin, child.width, child.height, width, height, 8.dp.roundToPx())
            layout(constraints.maxWidth, constraints.maxHeight) { child.place(position.x + margin, position.y + margin) }
        }
    }
} }

@Composable
private fun QueryHeader(state: ClipboardPanelState, settings: ClipboardSettings, watchlist: List<WatchItem>, onCopy: (String) -> Unit, onFloatingChange: (Boolean, WatchItem?) -> Unit, onToggleWatchlist: (ClipboardMatch, WatchItem?) -> Unit, onTrack: (String, String, WalletPortfolioSnapshot?, String?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val match = state.selected
        if (state.mode == ClipboardContentMode.TOKEN && match != null) CoinSymbolIcon(match.selection.tokenSymbol,
            iconUrl = match.selection.pair.info?.imageUrl.takeIf { match.selection.tokenSide == PoolTokenSide.BASE }, allowSymbolLookup = false, size = 20.dp)
        else Icon(Icons.Rounded.AccountBalanceWallet, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            val title = if (state.mode == ClipboardContentMode.WALLET) stringResource(R.string.clipboard_wallet_mode) else match?.selection?.tokenSymbol ?: stringResource(R.string.clipboard_title)
            val subtitle = if (state.mode == ClipboardContentMode.WALLET) state.walletChain?.let(OkxWalletChainRegistry::displayName) ?: stringResource(R.string.clipboard_query_readonly) else match?.chain?.displayName
            Text(listOfNotNull(title, subtitle).joinToString(" · "), style = MaterialTheme.typography.titleMedium, maxLines = 2)
            state.address?.let { address ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AddressText(address, Modifier.weight(1f, fill = false))
                    AddressCopyButton(address, onCopy)
                }
            }
        }
        state.address?.let { address ->
            if (state.mode == ClipboardContentMode.WALLET) AddressTrackButton {
                val chain = state.walletChain ?: if (WalletAddressParser.classify(address) == WalletAddressKind.SOLANA) "501" else "1"
                val name = state.fomoWallets?.takeIf { wallets ->
                    listOfNotNull(wallets.solana, wallets.evm).any { trackedAddressKey(it.address) == trackedAddressKey(address) }
                }?.let { it.name.ifBlank { it.handle } }
                onTrack(address, chain, state.walletSnapshot, name)
            }
        }
        match?.takeIf { state.mode == ClipboardContentMode.TOKEN }?.let {
            val existing = watchlist.firstOrNull { item -> item.semanticKey == it.watchItem(false).semanticKey }
            ClipboardFloatingToggle(checked = existing?.overlaySelected ?: settings.addToFloating, enabled = !state.actionBusy,
                label = stringResource(R.string.clipboard_add_floating), onCheckedChange = { value -> onFloatingChange(value, existing) }, size = 20.dp)
            ClipboardIconButton(onClick = { onToggleWatchlist(it, existing) }, enabled = !state.actionBusy) {
                Icon(if (existing == null) Icons.Rounded.Add else Icons.Rounded.Check,
                    stringResource(if (existing == null) R.string.clipboard_add_watch else R.string.clipboard_remove_watch), Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun QueryOverlayItemsButton(onOverlayItems: () -> Unit) {
    ClipboardIconButton(onClick = onOverlayItems, size = 24.dp) {
        Icon(Icons.Rounded.Layers, stringResource(R.string.overlay_select_items), Modifier.size(14.dp))
    }
}

@Composable
private fun QuerySettingsButton(onSettings: () -> Unit) {
    ClipboardIconButton(onClick = onSettings, size = 24.dp) { Icon(Icons.Rounded.Settings, stringResource(R.string.clipboard_settings), Modifier.size(14.dp)) }
}

@Composable
private fun AddressText(address: String, modifier: Modifier = Modifier) {
    Text(shortClipboardAddress(address), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.semantics { contentDescription = address })
}

@Composable
private fun AddressCopyButton(address: String, onCopy: (String) -> Unit) {
    var copied by remember(address) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1400); copied = false } }
    ClipboardIconButton(onClick = { onCopy(address); copied = true }, size = 20.dp) {
        Icon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, stringResource(R.string.clipboard_copy_address), Modifier.size(12.dp))
    }
}

@Composable
private fun AddressTrackButton(onClick: () -> Unit) {
    ClipboardIconButton(onClick = onClick) {
        Icon(Icons.Rounded.Add, stringResource(R.string.address_track), Modifier.size(16.dp))
    }
}

@Composable
private fun AddressCopy(address: String, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AddressText(address, Modifier.weight(1f))
        AddressCopyButton(address, onCopy)
    }
}

@Composable
private fun PendingResult(state: ClipboardPanelState, onRetryRead: () -> Unit) {
    when {
        state.loading -> LoadingMessage(R.string.clipboard_resolving)
        state.addresses.size > 1 && state.address == null -> {
            Text(stringResource(R.string.clipboard_choose_address), style = MaterialTheme.typography.bodySmall)
            state.addresses.forEach { address -> ClipboardTextButton(onClick = { state.chooseAddress(address) }, modifier = Modifier.fillMaxWidth()) { Text(shortClipboardAddress(address), fontFamily = FontFamily.Monospace) } }
        }
        state.matches.size > 1 -> {
            Text(stringResource(R.string.clipboard_choose_chain), style = MaterialTheme.typography.bodySmall)
            state.matches.forEach { match -> ClipboardTextButton(onClick = { state.chooseChain(match) }, modifier = Modifier.fillMaxWidth()) { Text(match.chain.displayName) } }
        }
        else -> {
            state.message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
            ClipboardTextButton(onClick = { state.address?.let(state::chooseAddress) ?: onRetryRead() }) { Text(stringResource(R.string.clipboard_retry)) }
        }
    }
}

@Composable
private fun QueryModeControls(state: ClipboardPanelState) {
    ClipboardModeTabs(wallet = state.mode == ClipboardContentMode.WALLET,
        onSelect = { if (it) state.useWalletMode() else state.useTokenMode() },
        tokenLabel = stringResource(R.string.clipboard_token_mode), walletLabel = stringResource(R.string.clipboard_wallet_mode),
        modifier = Modifier.fillMaxWidth(0.5f))
}

@Composable
private fun TokenQuote(state: ClipboardPanelState, settings: ClipboardSettings, match: ClipboardMatch, onValueModeChange: (String) -> Unit) {
    val loadingDescription = stringResource(R.string.clipboard_chart_loading)
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        // Keep the chart in the right two thirds; the left value can grow vertically at larger font sizes.
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            ClipboardChart(state.chart, match.selection.change24hPercent, Modifier.fillMaxWidth(2f / 3f).fillMaxHeight())
        }
        val value = if (settings.valueMode == "marketCap") compactClipboardNumber(match.marketCap)
            else "$${QuoteFormatter.formatPrice(match.selection.priceUsd)}"
        val valueDescription = stringResource(if (settings.valueMode == "marketCap") R.string.clipboard_value_market_cap else R.string.clipboard_value_price)
        Column(Modifier.fillMaxWidth(1f / 3f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(value, Modifier.weight(1f, fill = false).semantics { contentDescription = "$valueDescription $value" },
                        fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold,
                        color = trendColor(match.selection.change24hPercent))
                    ClipboardIconButton(onClick = { onValueModeChange(if (settings.valueMode == "marketCap") "price" else "marketCap") }, size = 20.dp) {
                        Icon(Icons.Rounded.SwapHoriz, stringResource(R.string.clipboard_toggle_value), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            match.selection.change24hPercent?.let {
                Text("${if (it > 0) "+" else ""}${String.format(java.util.Locale.US, "%.2f", it)}%",
                    style = MaterialTheme.typography.bodySmall, color = trendColor(it))
            }
        }
        Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            state.holderCount?.let { count ->
                val description = stringResource(R.string.clipboard_holders_description, count)
                Row(Modifier.clearAndSetSemantics { contentDescription = description },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(Icons.Rounded.Person, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(count.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (match.selection.change24hPercent != null) {
                Text("24H", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            state.chartLoading -> CircularProgressIndicator(Modifier.align(Alignment.BottomEnd).size(12.dp)
                .semantics { contentDescription = loadingDescription }, strokeWidth = 1.dp)
            state.chartUnavailable -> Icon(Icons.AutoMirrored.Rounded.ShowChart, stringResource(R.string.clipboard_chart_unavailable),
                Modifier.align(Alignment.BottomEnd).size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TokenResult(state: ClipboardPanelState, settings: ClipboardSettings, onOpen: (String) -> Unit, onValueModeChange: (String) -> Unit, onOverlayItems: () -> Unit, onSettings: () -> Unit) {
    val match = state.selected ?: return
    TokenQuote(state, settings, match, onValueModeChange)
    if (state.matches.size > 1) NetworkPicker(match.chain.chainIndex, state.matches.map { it.chain.chainIndex to it.chain.displayName }) { value -> state.matches.firstOrNull { it.chain.chainIndex == value }?.let(state::chooseChain) }
    HorizontalDivider()
    val entries = settings.destinations.filter { it.enabled }.map { SiteLink(it.name, it.url(match), it.id, it.iconUrl) }
    LinkGrid(entries, match.chain.dexScreenerId, onOpen, fixedViewport = true)
    ProjectLinks(match.projectLinks, onOpen)
    val question = settings.aiQuestion ?: stringResource(R.string.clipboard_ai_default_question)
    val aiLabels = ClipboardAIPromptLabels(
        token = stringResource(R.string.clipboard_ai_token), chain = stringResource(R.string.clipboard_ai_chain),
        contract = stringResource(R.string.clipboard_ai_contract), price = stringResource(R.string.clipboard_ai_price),
        marketCap = stringResource(R.string.clipboard_ai_market_cap), pool = stringResource(R.string.clipboard_ai_pool),
        market = stringResource(R.string.clipboard_ai_market), website = stringResource(R.string.clipboard_ai_website),
        unknown = stringResource(R.string.clipboard_ai_unknown)
    )
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 24.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            ClipboardTonalButton(onClick = { onOpen(clipboardAIPromptUrl("gpt", match, question, aiLabels)) }, modifier = Modifier.weight(1f), minimumHeight = 24.dp) {
                Icon(painterResource(R.drawable.clipboard_logo_gpt), null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp)); Text("GPT")
            }
            ClipboardTonalButton(onClick = { onOpen(clipboardAIPromptUrl("grok", match, question, aiLabels)) }, modifier = Modifier.weight(1f), minimumHeight = 24.dp) {
                Icon(painterResource(R.drawable.clipboard_logo_grok), null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp)); Text("Grok")
            }
            QueryOverlayItemsButton(onOverlayItems)
            QuerySettingsButton(onSettings)
        }
    }
}

@Composable
private fun WalletResult(state: ClipboardPanelState, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val address = state.address ?: return
    if (WalletAddressParser.classify(address) == WalletAddressKind.EVM) NetworkPicker(state.walletChain,
        OkxWalletChainRegistry.evmChainIndexes.map { it to OkxWalletChainRegistry.displayName(it) }, state::chooseWalletChain)
    if (state.walletChain == null) { Text(stringResource(R.string.clipboard_wallet_choose_network), style = MaterialTheme.typography.bodySmall); return }
    state.walletMessage?.let { error ->
        Text(stringResource(error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (error == R.string.wallet_watch_credentials_required) ClipboardTonalButton(onClick = onSettings) { Text(stringResource(R.string.wallet_watch_open_settings)) }
        else ClipboardTextButton(onClick = state::loadWallet) { Text(stringResource(R.string.clipboard_retry)) }
    }
    if (state.walletLoading) LoadingMessage(R.string.clipboard_wallet_loading)
    val preview = state.walletPreview
    if (preview != null && state.walletMessage != R.string.wallet_watch_credentials_required) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(if (preview.partial) R.string.clipboard_wallet_partial_value else R.string.clipboard_wallet_value), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text("$${AssetAmountFormatter.fiat(preview.value)}", fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(stringResource(R.string.clipboard_wallet_updated, preview.assets.size, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(preview.snapshot.updatedAtMillis))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var expanded by remember(address, state.walletChain) { mutableStateOf(false) }
        (if (expanded) preview.assets else preview.assets.take(3)).forEach { asset ->
            WalletHoldingRow(asset)
            HorizontalDivider()
        }
        if (preview.assets.isEmpty()) Text(stringResource(R.string.wallet_watch_no_assets), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (preview.assets.size > 3) ClipboardTextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.clipboard_wallet_collapse else R.string.clipboard_wallet_all_assets, preview.assets.size)) }
            Spacer(Modifier.weight(1f))
            ClipboardTextButton(onClick = state::loadWallet, enabled = !state.walletLoading) { Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp)); Text(stringResource(R.string.clipboard_wallet_refresh)) }
        }
    }
    val chain = state.walletChain ?: return

    val dexChain = mapOf("1" to "ethereum", "56" to "bsc", "8453" to "base", "501" to "solana", "4663" to "robinhood")[chain]
    val route = mapOf("1" to "eth", "56" to "bsc", "8453" to "base", "501" to "sol")[chain]
    val explorer = clipboardDestinations().first { it.id == "explorer" }.chainTemplates[dexChain]?.replace("{ca}", address)
    LinkGrid(listOfNotNull(route?.let { SiteLink("GMGN", "https://gmgn.ai/$it/address/$address", "gmgn") }, explorer?.let { SiteLink(stringResource(R.string.clipboard_explorer), it, "explorer") }), dexChain.orEmpty(), onOpen)
}

@Composable
private fun WalletHoldingRow(asset: WalletAsset) {
    val value = asset.holdingValueUsd?.let { "$${AssetAmountFormatter.fiat(it)}" } ?: stringResource(R.string.wallet_watch_no_price)
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(vertical = 2.dp)) {
        val stack = maxWidth < 210.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f || value.length > 16
        if (stack) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(asset.symbol, style = MaterialTheme.typography.titleSmall)
            Text(AssetAmountFormatter.token(asset.balance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(asset.symbol, style = MaterialTheme.typography.titleSmall)
                Text(AssetAmountFormatter.token(asset.balance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FomoResult(state: ClipboardPanelState, onCopy: (String) -> Unit, onOpen: (String) -> Unit, onTrack: (String, String, WalletPortfolioSnapshot?, String?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FomoAvatar(state.fomoWallets?.avatarUrl)
        Column(Modifier.weight(1f)) { Text(state.fomoWallets?.name ?: state.fomoLink?.handle ?: "FoMo", style = MaterialTheme.typography.titleMedium); Text("@${state.fomoLink?.handle.orEmpty()} · FoMo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        state.fomoLink?.let { link -> ClipboardIconButton(onClick = { onOpen(link.url) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.clipboard_fomo_open_profile), Modifier.size(16.dp)) } }
    }
    if (state.fomoLink == null) state.fomoLinks.forEach { link -> ClipboardTextButton(onClick = { state.chooseFomo(link) }) { Text("@${link.handle}") } }
    if (state.fomoLoading) LoadingMessage(R.string.clipboard_fomo_loading)
    state.fomoMessage?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall); ClipboardTextButton(onClick = { state.fomoLink?.let(state::chooseFomo) }) { Text(stringResource(R.string.clipboard_retry)) } }
    state.fomoWallets?.let { wallets ->
        for ((title, evidence) in listOf("Solana" to wallets.solana, "EVM" to wallets.evm)) Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (evidence == null) Text(stringResource(R.string.clipboard_fomo_unconfirmed), style = MaterialTheme.typography.bodySmall)
                    else {
                        AddressCopy(evidence.address, onCopy, Modifier.weight(1f))
                        AddressTrackButton { onTrack(evidence.address, if (title == "Solana") "501" else state.fomoEvmChain ?: "1", null, wallets.name.ifBlank { wallets.handle }) }
                    }
                }
                if (evidence != null) {
                    if (title == "EVM") NetworkPicker(state.fomoEvmChain, listOf("1", "8453", "56", "143", "4663").map { it to OkxWalletChainRegistry.displayName(it) }, state::selectFomoChain)
                    ClipboardTonalButton(onClick = { state.showFomoWallet(evidence) }, enabled = title == "Solana" || state.fomoEvmChain != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.clipboard_fomo_view_wallet)) }
                }
            }
        }
    }
}

@Composable
private fun FomoAvatar(url: String?) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        if (url != null) try {
            val result = withTimeout(6_000) { FomoHttpTransport().avatar(url) }
            bitmap = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(result, 0, result.size, bounds)
                if (bounds.outWidth.toLong() * bounds.outHeight in 1..4_000_000) BitmapFactory.decodeByteArray(result, 0, result.size) else null
            }
        } catch (error: CancellationException) { if (error !is TimeoutCancellationException) throw error }
        catch (_: Exception) { bitmap = null }
    }
    bitmap?.let { Image(it.asImageBitmap(), null, Modifier.size(20.dp).clip(CircleShape)); Spacer(Modifier.width(8.dp)) }
}

@Composable
private fun LoadingMessage(label: Int) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(stringResource(label), style = MaterialTheme.typography.bodySmall) } }

@Composable
internal fun NetworkPicker(selected: String?, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ClipboardOutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(options.firstOrNull { it.first == selected }?.second ?: stringResource(R.string.clipboard_wallet_choose_network)); Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = CoinMonitorThemeTokens.design.sizes.pickerMaximumHeight.coerceAtMost(280.dp)), properties = PopupProperties(focusable = false)) {
            options.forEach { (id, name) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 32.dp)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { open = false; onSelect(id) }
                    .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private data class SiteLink(val name: String, val url: String?, val id: String = "", val iconUrl: String? = null)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProjectLinks(links: List<ClipboardProjectLink>, onOpen: (String) -> Unit) {
    if (links.isEmpty()) return
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 20.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.clipboard_project_links), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                links.sortedBy { it.kind.ordinal }.forEach { link ->
                    val label = when (link.kind) {
                        ClipboardProjectLinkKind.WEBSITE -> stringResource(R.string.clipboard_project_website)
                        ClipboardProjectLinkKind.DOCUMENTS -> stringResource(R.string.clipboard_project_docs)
                        ClipboardProjectLinkKind.X -> "X"
                        ClipboardProjectLinkKind.DISCORD -> "Discord"
                        ClipboardProjectLinkKind.OTHER -> link.label
                    }
                    val description = "$label · ${java.net.URI(link.url).host}"
                    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(description) } }, state = rememberTooltipState()) {
                        ClipboardIconButton(onClick = { onOpen(link.url) }, size = 20.dp) {
                            when (link.kind) {
                                ClipboardProjectLinkKind.X -> Text("X", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { contentDescription = description })
                                ClipboardProjectLinkKind.DISCORD -> Image(painterResource(R.drawable.clipboard_logo_discord), description, Modifier.size(16.dp))
                                else -> Icon(when (link.kind) {
                                    ClipboardProjectLinkKind.WEBSITE -> Icons.Rounded.Language
                                    ClipboardProjectLinkKind.DOCUMENTS -> Icons.Rounded.Description
                                    else -> Icons.Rounded.Link
                                }, description, Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkGrid(links: List<SiteLink>, chain: String, onOpen: (String) -> Unit, fixedViewport: Boolean = false) {
    if (links.isEmpty()) return
    if (fixedViewport) {
        // Four full rows and all three intervening gaps stay visible.
        val viewportHeight = 28.dp * 4 + 3.dp * 3
        LazyColumn(Modifier.fillMaxWidth().height(viewportHeight), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(links.chunked(2)) { entries -> SiteLinkRow(entries, chain, onOpen, fixedHeight = true) }
        }
    } else links.chunked(2).forEach { SiteLinkRow(it, chain, onOpen, fixedHeight = false) }
}

@Composable
private fun SiteLinkRow(entries: List<SiteLink>, chain: String, onOpen: (String) -> Unit, fixedHeight: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { entry -> ClipboardOutlinedButton(onClick = { entry.url?.let(onOpen) }, enabled = entry.url != null,
            modifier = Modifier.weight(1f).then(if (fixedHeight) Modifier.height(28.dp) else Modifier)) {
            ClipboardSiteIcon(entry.id, chain, entry.iconUrl, size = 14.dp); Spacer(Modifier.width(3.dp))
            Text(entry.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        } }
        if (entries.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
internal fun ClipboardSiteIcon(id: String, chain: String = "", iconUrl: String? = null, size: androidx.compose.ui.unit.Dp = 22.dp) {
    val resource = destinationIconResource(id, chain)
    if (iconUrl != null && safeClipboardUrl(iconUrl) != null) Box(Modifier.size(size).clip(RoundedCornerShape(5.dp))) {
        Icon(Icons.Rounded.Language, null, Modifier.fillMaxSize())
        AsyncImage(ImageRequest.Builder(LocalContext.current).data(iconUrl).size(64).build(), null, Modifier.fillMaxSize())
    }
    else if (resource != null) Image(painterResource(resource), null, Modifier.size(size).clip(RoundedCornerShape(5.dp)))
    else Icon(Icons.Rounded.Language, null, Modifier.size(size))
}

internal fun destinationIconResource(id: String, chainId: String): Int? = when (id) {
    "dex" -> R.drawable.clipboard_logo_dexscreener
    "gmgn" -> R.drawable.clipboard_logo_gmgn
    "okx" -> R.drawable.clipboard_logo_okx
    "binance" -> R.drawable.clipboard_logo_binance
    "x" -> R.drawable.clipboard_logo_x
    "fomo" -> R.drawable.clipboard_logo_fomo
    "explorer" -> when (chainId) { "ethereum" -> R.drawable.clipboard_logo_etherscan; "bsc" -> R.drawable.clipboard_logo_bscscan; "base" -> R.drawable.clipboard_logo_basescan; "solana" -> R.drawable.clipboard_logo_solscan; "robinhood" -> R.drawable.clipboard_logo_blockscout; else -> null }
    else -> null
}

@Composable
private fun ClipboardChart(points: List<ClipboardChartPoint>, change: Double?, modifier: Modifier) {
    val color = trendColor(change)
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val minimum = points.minOf { it.price }
        val range = (points.maxOf { it.price } - minimum).coerceAtLeast(minimum * 0.001)
        val duration = (points.last().timestamp - points.first().timestamp).coerceAtLeast(1.0)
        val line = Path()
        points.forEachIndexed { index, point ->
            val x = ((point.timestamp - points.first().timestamp) / duration * size.width).toFloat()
            val y = (size.height - 5.dp.toPx() - ((point.price - minimum) / range * (size.height - 10.dp.toPx()))).toFloat()
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.02f))))
        drawPath(line, color = color, style = Stroke(width = 1.3.dp.toPx()))
    }
}

@Composable
private fun trendColor(change: Double?): Color {
    val colors = CoinMonitorThemeTokens.colors
    return when {
        change == null -> MaterialTheme.colorScheme.onSurface
        change == 0.0 -> MaterialTheme.colorScheme.onSurface
        change > 0 -> colors.positive
        else -> colors.negative
    }
}


internal fun shortClipboardAddress(value: String): String = if (value.length > 11) "${value.take(6)}…${value.takeLast(4)}" else value
