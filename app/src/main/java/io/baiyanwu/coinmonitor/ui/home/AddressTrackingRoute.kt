package io.baiyanwu.coinmonitor.ui.home

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.*
import io.baiyanwu.coinmonitor.ui.components.compactContractAddress
import io.baiyanwu.coinmonitor.ui.format.AssetAmountFormatter
import io.baiyanwu.coinmonitor.ui.settings.ThirdPartyApiSettingsActivity
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens
import io.baiyanwu.coinmonitor.ui.walletwatch.WalletWatchActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.DateFormat
import java.util.Date

internal class AddressTrackingViewModel(private val container: AppContainer) : ViewModel() {
    val entries = container.addressTrackingStore.entries
    val refreshing = MutableStateFlow(false)
    val failed = MutableStateFlow(false)
    val credentials = container.okxWalletCredentialsRepository.observeCredentials()

    fun track(address: String) {
        val chain = if (WalletAddressParser.classify(address) == WalletAddressKind.SOLANA) "501" else "1"
        val alreadyTracked = entries.value.any { trackedAddressKey(it.address) == trackedAddressKey(address) }
        container.addressTrackingStore.track(address, chain)
        if (alreadyTracked) refresh(listOf(address))
    }

    fun remove(address: String) {
        queued.remove(address)
        container.addressTrackingStore.remove(address)
    }

    private val queued = linkedSetOf<String>()

    fun refresh(addresses: List<String> = entries.value.map { it.address }) {
        if (!container.okxWalletCredentialsRepository.getCredentials().isReady) return
        queued.addAll(addresses)
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            failed.value = false
            try {
                while (queued.isNotEmpty()) {
                    val address = queued.first()
                    queued.remove(address)
                    if (entries.value.none { trackedAddressKey(it.address) == trackedAddressKey(address) }) continue
                    try {
                        val snapshot = withTimeout(20_000) { container.walletPortfolioRepository.load(address, false) }
                        container.addressTrackingStore.update(snapshot)
                    } catch (error: CancellationException) {
                        if (error is kotlinx.coroutines.TimeoutCancellationException) failed.value = true else throw error
                    } catch (_: Exception) { failed.value = true }
                }
            } finally { refreshing.value = false }
        }
    }
}

@Composable
internal fun AddressTrackingRoute(container: AppContainer, showAdd: Boolean = false, onDismissAdd: () -> Unit = {}) {
    val vm: AddressTrackingViewModel = viewModel(factory = viewModelFactory { initializer { AddressTrackingViewModel(container) } })
    val entries by vm.entries.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val credentials by vm.credentials.collectAsStateWithLifecycle(initialValue = container.okxWalletCredentialsRepository.getCredentials())
    val context = LocalContext.current
    var adding by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<TrackedAddress?>(null) }
    val design = CoinMonitorThemeTokens.design
    // Query on entry / newly tracked addresses / credentials becoming available, never on recomposition or expansion.
    LaunchedEffect(entries.map { trackedAddressKey(it.address) }, credentials.isReady) { vm.refresh() }
    Column(Modifier.fillMaxSize().padding(horizontal = design.spacing.pageHorizontal)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { adding = true }) { Text(stringResource(R.string.address_add)) }
            TextButton(onClick = { vm.refresh() }, enabled = !refreshing && credentials.isReady) {
                Text(stringResource(R.string.address_refresh))
            }
        }
        if (!credentials.isReady) TextButton(onClick = { ThirdPartyApiSettingsActivity.start(context as Activity) }) {
            Text(stringResource(R.string.wallet_watch_credentials_required))
        }
        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Text(stringResource(R.string.address_refresh_failed), color = design.colors.negative, style = design.typography.caption)
        if (entries.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.address_tracking_empty), color = design.colors.secondaryText)
        } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(entries, key = { trackedAddressKey(it.address) }) { entry ->
                var expanded by rememberSaveable(entry.address) { mutableStateOf(false) }
                Surface(shape = design.shapes.card, color = design.colors.surface) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable {
                                WalletWatchActivity.start(context as Activity, entry.address,
                                    if (WalletAddressParser.classify(entry.address) == WalletAddressKind.EVM) "1" else "501")
                            }.padding(4.dp)) {
                                Text(compactContractAddress(entry.address), maxLines = 1, overflow = TextOverflow.Ellipsis, style = design.typography.rowTitle)
                                entry.updatedAtMillis?.let {
                                    Text(stringResource(R.string.address_updated, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))),
                                        style = design.typography.caption, color = design.colors.secondaryText)
                                }
                            }
                            Text(entry.totalValueUsd?.let { (if (entry.totalIsEstimated) "≈" else "") + "$" + AssetAmountFormatter.fiat(it.toBigDecimal()) }
                                ?: stringResource(R.string.address_estimate_pending), style = design.typography.label)
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    stringResource(if (expanded) R.string.address_collapse else R.string.address_expand))
                            }
                            IconButton(onClick = { removing = entry }) { Icon(Icons.Rounded.Delete, stringResource(R.string.address_remove)) }
                        }
                        if (expanded) entry.chains.sortedWith(compareBy<String> { it != "1" }.thenBy { OkxWalletChainRegistry.displayName(it) }).forEach { chain ->
                            Row(Modifier.fillMaxWidth().clickable {
                                WalletWatchActivity.start(context as Activity, entry.address, chain)
                            }.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(OkxWalletChainRegistry.displayName(chain), style = design.typography.label)
                                Text(entry.chainValuesUsd[chain]?.let { (if (chain in entry.estimatedChains) "≈" else "") + "$" + AssetAmountFormatter.fiat(it.toBigDecimal()) }
                                    ?: stringResource(R.string.address_estimate_pending), style = design.typography.label)
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding || showAdd) AlertDialog(
        onDismissRequest = { adding = false; onDismissAdd() },
        title = { Text(stringResource(R.string.address_add)) },
        text = { OutlinedTextField(value = input, onValueChange = { input = it; invalid = false }, label = { Text(stringResource(R.string.address_input)) },
            isError = invalid, supportingText = { if (invalid) Text(stringResource(R.string.wallet_watch_invalid_address)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            if (WalletAddressParser.classify(input) == null) invalid = true
            else { vm.track(input.trim()); input = ""; adding = false; onDismissAdd() }
        }) { Text(stringResource(R.string.address_track)) } },
        dismissButton = { TextButton(onClick = { adding = false; onDismissAdd() }) { Text(stringResource(R.string.common_cancel)) } }
    )
    removing?.let { entry -> AlertDialog(
        onDismissRequest = { removing = null }, title = { Text(stringResource(R.string.address_remove)) }, text = { Text(entry.address) },
        confirmButton = { TextButton(onClick = { vm.remove(entry.address); removing = null }) { Text(stringResource(R.string.common_confirm)) } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.common_cancel)) } }
    ) }
}
