package io.baiyanwu.coinmonitor.ui.browser

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.data.repository.DappAddressParser
import io.baiyanwu.coinmonitor.domain.model.OnchainChainIconRegistry
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.ui.components.CoilCoinSymbolIcon
import io.baiyanwu.coinmonitor.data.wallet.WalletBiometricManager
import io.baiyanwu.coinmonitor.ui.wallet.WalletUnlockGate

@Composable
fun DappBrowserRoute(
    container: AppContainer,
    webView: DappWebView?,
    initialUrl: String,
    onExit: () -> Unit
) {
    val viewModel: DappBrowserViewModel = viewModel(factory = DappBrowserViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var progress by remember { mutableIntStateOf(webView?.currentProgress ?: 0) }
    var providerAvailable by remember { mutableStateOf(true) }
    var address by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var pendingThirdPartyAddress by rememberSaveable { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val biometricManager = remember(context) { WalletBiometricManager(context) }

    LaunchedEffect(webView, viewModel) {
        val browser = webView ?: return@LaunchedEffect
        viewModel.commands.collect { command ->
            browser.deliver(command)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(top = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .background(MaterialTheme.colorScheme.background)
        ) {
            BrowserToolbar(
                address = address,
                selectedNetwork = state.selectedNetwork,
                networks = state.networks.enabledNetworks.filter(WalletNetwork::isEvm),
                canGoForward = webView?.canGoForward() == true,
                onAddressChange = { address = it },
                onNavigate = { value ->
                    DappAddressParser.normalizeUserWebAddress(value)?.let { normalized ->
                        pendingThirdPartyAddress = normalized
                        true
                    } ?: false
                },
                onSelectNetwork = viewModel::selectNetwork,
                onBack = {
                    val browser = webView
                    if (browser?.canGoBack() == true) browser.goBack() else onExit()
                },
                onForward = { webView?.goForward() },
                onReload = { webView?.reload() },
                onClose = onExit
            )
            if (progress < 100) {
                if (progress <= 0) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }
            }
            Text(
                text = if (providerAvailable) {
                    stringResource(R.string.browser_connection_preview)
                } else {
                    stringResource(R.string.browser_provider_unavailable)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            if (state.vault.initialized && !state.vault.unlocked) {
                WalletUnlockGate(
                    onUnlock = viewModel::unlockWallet,
                    biometricManager = biometricManager,
                    onBiometricKey = viewModel::unlockWalletWithDerivedKey
                )
            } else webView?.let { browser ->
                AndroidView(
                    factory = {
                        (browser.parent as? ViewGroup)?.removeView(browser)
                        browser.apply {
                            initialize(
                                initialUrl = initialUrl,
                                initialChainId = state.selectedNetwork.chainId ?: 1L,
                                onProgress = { progress = it },
                                onProviderAvailability = { providerAvailable = it },
                                onMessage = viewModel::handleBridgeMessage,
                                onUrlChange = { address = it }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                )
            }
        }
    }

    DappApprovalDialogs(state = state, viewModel = viewModel)
    pendingThirdPartyAddress?.let { target ->
        ThirdPartyWebsiteWarningDialog(
            address = target,
            onDismiss = { pendingThirdPartyAddress = null },
            onContinue = {
                pendingThirdPartyAddress = null
                webView?.navigate(target)
            }
        )
    }
}

@Composable
private fun BrowserToolbar(
    address: String,
    selectedNetwork: WalletNetwork,
    networks: List<WalletNetwork>,
    canGoForward: Boolean,
    onAddressChange: (String) -> Unit,
    onNavigate: (String) -> Boolean,
    onSelectNetwork: (WalletNetwork) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onClose: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var networkMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                stringResource(R.string.browser_back),
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(
            onClick = onForward,
            enabled = canGoForward,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                stringResource(R.string.browser_forward),
                modifier = Modifier.size(20.dp)
            )
        }
        CompactAddressField(
            value = address,
            onValueChange = onAddressChange,
            modifier = Modifier
                .weight(1f)
                .height(36.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = { navigateAndClearFocus(address, onNavigate, focusManager) }
            )
        )
        Box {
            val switchDescription = stringResource(
                R.string.browser_switch_network,
                selectedNetwork.displayName
            )
            IconButton(
                onClick = { networkMenuExpanded = true },
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = switchDescription }
            ) {
                NetworkIcon(selectedNetwork, size = 22.dp)
            }
            DropdownMenu(
                expanded = networkMenuExpanded,
                onDismissRequest = { networkMenuExpanded = false }
            ) {
                networks.forEach { network ->
                    DropdownMenuItem(
                        text = { Text(network.displayName) },
                        leadingIcon = { NetworkIcon(network, size = 24.dp) },
                        onClick = {
                            networkMenuExpanded = false
                            onSelectNetwork(network)
                        }
                    )
                }
            }
        }
        IconButton(onClick = onReload, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Rounded.Refresh,
                stringResource(R.string.browser_reload),
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Rounded.Close,
                stringResource(R.string.browser_close),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CompactAddressField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (focused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .border(if (focused) 1.5.dp else 1.dp, borderColor, shape)
            .padding(horizontal = 8.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { innerTextField ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.browser_address_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}

private fun navigateAndClearFocus(
    address: String,
    onNavigate: (String) -> Boolean,
    focusManager: FocusManager
) {
    if (onNavigate(address)) focusManager.clearFocus()
}

@Composable
private fun NetworkIcon(network: WalletNetwork, size: Dp) {
    CoilCoinSymbolIcon(
        symbol = network.symbol,
        iconUrl = network.chainId?.toString()?.let(OnchainChainIconRegistry::resolveIconUrl),
        allowSymbolLookup = false,
        size = size
    )
}
