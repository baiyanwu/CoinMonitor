package io.baiyanwu.coinmonitor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.AppPreferences
import io.baiyanwu.coinmonitor.domain.model.OnchainDataProvider
import io.baiyanwu.coinmonitor.domain.model.OnchainRefreshMode
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.model.WalletProviderMode
import io.baiyanwu.coinmonitor.domain.model.modeFor
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

private const val SHOW_AI_SETTINGS_ENTRY = false

enum class ThirdPartyApiSettingsSection { TOP, WALLET_NETWORK }

@Composable
fun ThirdPartyApiSettingsRoute(
    container: AppContainer,
    initialSection: ThirdPartyApiSettingsSection = ThirdPartyApiSettingsSection.TOP,
    onBack: () -> Unit
) {
    val viewModel: ThirdPartyApiSettingsViewModel = viewModel(
        factory = ThirdPartyApiSettingsViewModel.factory(container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ThirdPartyApiSettingsScreen(
        state = state,
        initialSection = initialSection,
        onBack = onBack,
        onOnchainRefreshModeChange = viewModel::updateOnchainRefreshMode,
        onOnchainRefreshIntervalChange = viewModel::updateOnchainRefreshIntervalSeconds,
        onOnchainProviderPriorityChange = viewModel::updateOnchainProviderPriority,
        onSaveOnchain = viewModel::saveOnchainSettings,
        onAlchemyApiKeyChange = viewModel::updateAlchemyApiKey,
        onSaveWalletNetwork = viewModel::saveWalletNetworkSettings,
        onRefreshWalletNetworks = viewModel::refreshWalletNetworkCatalog,
        onSetWalletNetworkEnabled = viewModel::setWalletNetworkEnabled,
        onSaveWalletCustomRpc = viewModel::saveWalletCustomRpc,
        onClearWalletCustomRpc = viewModel::clearWalletCustomRpc,
        onAddCustomEvmNetwork = viewModel::addCustomEvmNetwork,
        onRemoveCustomEvmNetwork = viewModel::removeCustomEvmNetwork,
        onOkxEnabledChange = viewModel::setOkxWalletEnabled,
        onOkxApiKeyChange = viewModel::updateOkxWalletApiKey,
        onOkxSecretKeyChange = viewModel::updateOkxWalletSecretKey,
        onOkxPassphraseChange = viewModel::updateOkxWalletPassphrase,
        onSaveOkx = viewModel::saveOkxWalletCredentials,
        onClearOkx = viewModel::clearOkxWalletCredentials,
        onAiEnabledChange = viewModel::setAiEnabled,
        onAiBaseUrlChange = viewModel::updateAiBaseUrl,
        onAiApiKeyChange = viewModel::updateAiApiKey,
        onAiModelChange = viewModel::updateAiModel,
        onAiSystemPromptChange = viewModel::updateAiSystemPrompt,
        onSaveAi = viewModel::saveAiConfig,
        onClearAi = viewModel::clearAiConfig
    )
}

@Composable
private fun ThirdPartyApiSettingsScreen(
    state: ThirdPartyApiSettingsUiState,
    initialSection: ThirdPartyApiSettingsSection,
    onBack: () -> Unit,
    onOnchainRefreshModeChange: (OnchainRefreshMode) -> Unit,
    onOnchainRefreshIntervalChange: (Int) -> Unit,
    onOnchainProviderPriorityChange: (OnchainDataProvider) -> Unit,
    onSaveOnchain: () -> Unit,
    onAlchemyApiKeyChange: (String) -> Unit,
    onSaveWalletNetwork: () -> Unit,
    onRefreshWalletNetworks: () -> Unit,
    onSetWalletNetworkEnabled: (WalletNetwork, Boolean) -> Unit,
    onSaveWalletCustomRpc: (WalletNetwork, String) -> Unit,
    onClearWalletCustomRpc: (WalletNetwork) -> Unit,
    onAddCustomEvmNetwork: (String, String, String, String, String) -> Unit,
    onRemoveCustomEvmNetwork: (WalletNetwork) -> Unit,
    onOkxEnabledChange: (Boolean) -> Unit,
    onOkxApiKeyChange: (String) -> Unit,
    onOkxSecretKeyChange: (String) -> Unit,
    onOkxPassphraseChange: (String) -> Unit,
    onSaveOkx: () -> Unit,
    onClearOkx: () -> Unit,
    onAiEnabledChange: (Boolean) -> Unit,
    onAiBaseUrlChange: (String) -> Unit,
    onAiApiKeyChange: (String) -> Unit,
    onAiModelChange: (String) -> Unit,
    onAiSystemPromptChange: (String) -> Unit,
    onSaveAi: () -> Unit,
    onClearAi: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val uriHandler = LocalUriHandler.current
    var showAiValidationError by rememberSaveable { mutableStateOf(false) }
    var showOkxValidationError by rememberSaveable { mutableStateOf(false) }
    var showWalletNetworkManager by rememberSaveable { mutableStateOf(false) }
    var showAddCustomEvm by rememberSaveable { mutableStateOf(false) }
    var customRpcNetworkId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (initialSection == ThirdPartyApiSettingsSection.WALLET_NETWORK) 2 else 0
    )

    Scaffold(
        containerColor = colors.pageBackground,
        topBar = {
            ThirdPartyApiTopBar(onBack = onBack)
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.pageBackground)
                .padding(innerPadding),
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ThirdPartySectionCard(title = stringResource(R.string.third_party_api_settings_section_onchain)) {
                Text(
                    text = stringResource(R.string.third_party_api_settings_onchain_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
                Text(
                    text = stringResource(R.string.third_party_api_settings_onchain_providers),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accent
                )
                OnchainProviderPrioritySetting(
                    providerOrder = state.onchain.providerOrder,
                    onPriorityChange = onOnchainProviderPriorityChange
                )
                DexPollingIntervalSetting(
                    state = state.onchain,
                    onModeChange = onOnchainRefreshModeChange,
                    onIntervalChange = onOnchainRefreshIntervalChange
                )
                FeedbackText(
                    savedFlag = state.onchain.savedFlag,
                    clearedFlag = false,
                    errorMessage = state.onchain.errorMessage
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSaveOnchain,
                    colors = CoinMonitorComponentDefaults.primaryButtonColors()
                ) {
                    Text(text = stringResource(R.string.third_party_api_settings_save))
                }
                }
            }

            item {
                ThirdPartySectionCard(title = stringResource(R.string.okx_wallet_settings_title)) {
                Text(
                    text = stringResource(R.string.okx_wallet_settings_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
                if (!state.okxWallet.secureStorageAvailable) {
                    Text(
                        text = stringResource(R.string.okx_wallet_secure_storage_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                SettingSwitchRow(
                    title = stringResource(R.string.okx_wallet_settings_enabled),
                    checked = state.okxWallet.enabled,
                    onCheckedChange = {
                        if (it && !state.okxWallet.isComplete) {
                            showOkxValidationError = true
                        } else {
                            showOkxValidationError = false
                            onOkxEnabledChange(it)
                        }
                    },
                    horizontalPadding = 0.dp,
                    verticalPadding = 0.dp
                )
                OutlinedTextField(
                    value = state.okxWallet.apiKey,
                    onValueChange = { showOkxValidationError = false; onOkxApiKeyChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.okx_wallet_api_key)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.okxWallet.secretKey,
                    onValueChange = { showOkxValidationError = false; onOkxSecretKeyChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.okx_wallet_secret_key)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.okxWallet.passphrase,
                    onValueChange = { showOkxValidationError = false; onOkxPassphraseChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.okx_wallet_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Text(
                    text = stringResource(R.string.okx_wallet_developer_portal),
                    modifier = Modifier.clickable { uriHandler.openUri("https://web3.okx.com/onchainos/dev-portal") },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accent
                )
                if (showOkxValidationError) ValidationText(R.string.okx_wallet_credentials_incomplete)
                FeedbackText(
                    savedFlag = state.okxWallet.savedFlag,
                    clearedFlag = state.okxWallet.clearedFlag,
                    errorMessage = state.okxWallet.errorMessage
                )
                SaveClearButtons(
                    onSave = {
                        if ((state.okxWallet.enabled || state.okxWallet.apiKey.isNotBlank() || state.okxWallet.secretKey.isNotBlank() || state.okxWallet.passphrase.isNotBlank()) && !state.okxWallet.isComplete) {
                            showOkxValidationError = true
                        } else {
                            showOkxValidationError = false
                            onSaveOkx()
                        }
                    },
                    onClear = { showOkxValidationError = false; onClearOkx() }
                )
                }
            }

            item {
                ThirdPartySectionCard(title = stringResource(R.string.wallet_network_settings_title)) {
                Text(
                    text = stringResource(R.string.wallet_network_settings_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
                if (!state.walletNetwork.secureStorageAvailable) {
                    Text(
                        text = stringResource(R.string.wallet_network_secure_storage_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = state.walletNetwork.alchemyApiKey,
                    onValueChange = onAlchemyApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.wallet_network_alchemy_api_key)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Text(
                    text = stringResource(
                        R.string.wallet_network_enabled_count,
                        state.walletNetwork.enabledNetworkIds.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
                OutlinedButton(
                    onClick = { showWalletNetworkManager = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.wallet_network_manage))
                }
                OutlinedButton(
                    onClick = { showAddCustomEvm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.wallet_network_add_custom_evm))
                }
                FeedbackText(
                    savedFlag = state.walletNetwork.savedFlag,
                    clearedFlag = false,
                    errorMessage = state.walletNetwork.errorMessage
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSaveWalletNetwork,
                    enabled = !state.walletNetwork.saving && state.walletNetwork.secureStorageAvailable,
                    colors = CoinMonitorComponentDefaults.primaryButtonColors()
                ) {
                    Text(
                        if (state.walletNetwork.saving) stringResource(R.string.wallet_network_validating)
                        else stringResource(R.string.third_party_api_settings_save)
                    )
                }
                }
            }

            if (SHOW_AI_SETTINGS_ENTRY) {
                item {
                    ThirdPartySectionCard(title = stringResource(R.string.third_party_api_settings_section_ai)) {
                    Text(
                        text = stringResource(R.string.third_party_api_settings_ai_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                    if (!state.ai.secureStorageAvailable) {
                        Text(
                            text = stringResource(R.string.third_party_api_settings_secure_storage_unavailable_ai),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    SettingSwitchRow(
                        title = stringResource(R.string.third_party_api_settings_enable_ai),
                        checked = state.ai.enabled,
                        onCheckedChange = onAiEnabledChange,
                        horizontalPadding = 0.dp,
                        verticalPadding = 0.dp
                    )
                    OutlinedTextField(
                        value = state.ai.baseUrl,
                        onValueChange = {
                            showAiValidationError = false
                            onAiBaseUrlChange(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.third_party_api_settings_base_url)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.ai.apiKey,
                        onValueChange = {
                            showAiValidationError = false
                            onAiApiKeyChange(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.third_party_api_settings_api_key)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.ai.model,
                        onValueChange = {
                            showAiValidationError = false
                            onAiModelChange(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.third_party_api_settings_model)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.ai.systemPrompt,
                        onValueChange = {
                            showAiValidationError = false
                            onAiSystemPromptChange(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        label = { Text(stringResource(R.string.third_party_api_settings_system_prompt)) }
                    )
                    if (showAiValidationError) {
                        ValidationText(R.string.third_party_api_settings_validation_required_ai)
                    }
                    FeedbackText(
                        savedFlag = state.ai.savedFlag,
                        clearedFlag = state.ai.clearedFlag,
                        errorMessage = state.ai.errorMessage
                    )
                    SaveClearButtons(
                        onSave = {
                            val needValidate = state.ai.enabled || state.ai.baseUrl.isNotBlank() ||
                                state.ai.apiKey.isNotBlank() || state.ai.model.isNotBlank()
                            if (needValidate && !state.ai.isReadyToEnable) {
                                showAiValidationError = true
                                return@SaveClearButtons
                            }
                            showAiValidationError = false
                            onSaveAi()
                        },
                        onClear = {
                            showAiValidationError = false
                            onClearAi()
                        }
                    )
                    }
                }
            }
        }
    }

    if (showWalletNetworkManager) {
        WalletNetworkManagerDialog(
            state = state.walletNetwork,
            onDismiss = { showWalletNetworkManager = false },
            onRefresh = onRefreshWalletNetworks,
            onToggle = onSetWalletNetworkEnabled,
            onConfigureRpc = { customRpcNetworkId = it.id },
            onRemove = onRemoveCustomEvmNetwork
        )
    }
    state.walletNetwork.availableNetworks.firstOrNull { it.id == customRpcNetworkId }?.let { network ->
        WalletCustomRpcDialog(
            network = network,
            initialUrl = state.walletNetwork.customRpcUrls[network.id].orEmpty(),
            saving = state.walletNetwork.saving,
            onDismiss = { customRpcNetworkId = null },
            onSave = {
                onSaveWalletCustomRpc(network, it)
                customRpcNetworkId = null
            },
            onClear = {
                onClearWalletCustomRpc(network)
                customRpcNetworkId = null
            }
        )
    }
    if (showAddCustomEvm) {
        AddCustomEvmNetworkDialog(
            saving = state.walletNetwork.saving,
            onDismiss = { showAddCustomEvm = false },
            onAdd = { name, chainId, symbol, explorer, rpc ->
                onAddCustomEvmNetwork(name, chainId, symbol, explorer, rpc)
                showAddCustomEvm = false
            }
        )
    }
}

@Composable
private fun WalletNetworkManagerDialog(
    state: WalletNetworkSettingsFormState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (WalletNetwork, Boolean) -> Unit,
    onConfigureRpc: (WalletNetwork) -> Unit,
    onRemove: (WalletNetwork) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleNetworks = state.availableNetworks.filter {
        query.isBlank() || it.displayName.contains(query, ignoreCase = true) ||
            it.chainId?.toString()?.contains(query) == true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wallet_network_manage)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.wallet_network_search)) },
                    singleLine = true
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(visibleNetworks, key = WalletNetwork::id) { network ->
                        val enabled = network.id in state.enabledNetworkIds
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(network.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    network.chainId?.let { "Chain ID $it · ${network.symbol}" }
                                        ?: network.symbol,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CoinMonitorThemeTokens.colors.secondaryText
                                )
                                state.customRpcUrls[network.id]?.takeIf(String::isNotBlank)?.let {
                                    Text(
                                        stringResource(R.string.wallet_provider_custom_only),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CoinMonitorThemeTokens.colors.accent
                                    )
                                }
                            }
                            TextButton(onClick = { onConfigureRpc(network) }) {
                                Text(stringResource(R.string.wallet_network_custom_rpc_short))
                            }
                            if (network.userDefined) {
                                TextButton(onClick = { onRemove(network) }) {
                                    Text(stringResource(R.string.wallet_network_remove))
                                }
                            }
                            Switch(checked = enabled, onCheckedChange = { onToggle(network, it) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wallet_network_done)) } },
        dismissButton = {
            TextButton(onClick = onRefresh, enabled = !state.refreshingCatalog) {
                if (state.refreshingCatalog) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text(stringResource(R.string.wallet_network_refresh_catalog))
            }
        }
    )
}

@Composable
private fun WalletCustomRpcDialog(
    network: WalletNetwork,
    initialUrl: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var url by rememberSaveable(network.id) { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wallet_network_custom_rpc_title, network.displayName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.wallet_network_custom_rpc_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoinMonitorThemeTokens.colors.secondaryText
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.wallet_network_rpc_url)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(url) }, enabled = url.isNotBlank() && !saving) {
                Text(stringResource(R.string.third_party_api_settings_save))
            }
        },
        dismissButton = {
            Row {
                if (initialUrl.isNotBlank()) {
                    TextButton(onClick = onClear, enabled = !saving) {
                        Text(stringResource(R.string.wallet_network_clear_rpc))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    )
}

@Composable
private fun AddCustomEvmNetworkDialog(
    saving: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var chainId by rememberSaveable { mutableStateOf("") }
    var symbol by rememberSaveable { mutableStateOf("") }
    var explorer by rememberSaveable { mutableStateOf("") }
    var rpc by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wallet_network_add_custom_evm)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.wallet_network_add_custom_evm_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoinMonitorThemeTokens.colors.secondaryText
                )
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.wallet_network_name)) }, singleLine = true)
                OutlinedTextField(chainId, { chainId = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.wallet_network_chain_id)) }, singleLine = true)
                OutlinedTextField(symbol, { symbol = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.wallet_network_native_symbol)) }, singleLine = true)
                OutlinedTextField(rpc, { rpc = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.wallet_network_rpc_url)) }, singleLine = true)
                OutlinedTextField(explorer, { explorer = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.wallet_network_explorer_optional)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, chainId, symbol, explorer, rpc) },
                enabled = name.isNotBlank() && chainId.isNotBlank() && symbol.isNotBlank() && rpc.isNotBlank() && !saving
            ) { Text(stringResource(R.string.wallet_network_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun OnchainProviderPrioritySetting(
    providerOrder: List<OnchainDataProvider>,
    onPriorityChange: (OnchainDataProvider) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val primaryProvider = providerOrder.firstOrNull()
        ?: AppPreferences.DEFAULT_ONCHAIN_PROVIDER_ORDER.first()
    val providers = OnchainDataProvider.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.third_party_api_settings_provider_priority_title),
            style = MaterialTheme.typography.titleSmall
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            providers.forEachIndexed { index, provider ->
                SegmentedButton(
                    selected = primaryProvider == provider,
                    onClick = { onPriorityChange(provider) },
                    shape = SegmentedButtonDefaults.itemShape(index, providers.size),
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            when (provider) {
                                OnchainDataProvider.DEX_SCREENER -> stringResource(
                                    R.string.onchain_provider_dex_screener
                                )
                                OnchainDataProvider.OKX_DEX -> stringResource(
                                    R.string.onchain_provider_okx_dex
                                )
                            }
                        )
                    }
                )
            }
        }
        Text(
            text = stringResource(R.string.third_party_api_settings_provider_priority_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun DexPollingIntervalSetting(
    state: OnchainSettingsFormState,
    onModeChange: (OnchainRefreshMode) -> Unit,
    onIntervalChange: (Int) -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val modes = listOf(
        OnchainRefreshMode.SMART to stringResource(R.string.third_party_api_settings_refresh_mode_smart),
        OnchainRefreshMode.FIXED to stringResource(R.string.third_party_api_settings_refresh_mode_fixed)
    )
    val requestSpacingSeconds = state.requestSpacingMillis / 1_000.0
    val runtimeText = when {
        state.requestBatchCount == 0 -> {
            stringResource(R.string.third_party_api_settings_refresh_status_empty)
        }

        state.runtimeActive -> {
            stringResource(
                R.string.third_party_api_settings_refresh_status_active,
                state.requestBatchCount,
                requestSpacingSeconds,
                state.cycleIntervalSeconds
            )
        }

        else -> {
            stringResource(
                R.string.third_party_api_settings_refresh_status_idle,
                state.requestBatchCount,
                requestSpacingSeconds,
                state.cycleIntervalSeconds
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.third_party_api_settings_refresh_mode_title),
            style = MaterialTheme.typography.titleSmall
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = state.refreshMode == mode,
                    onClick = { onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = modes.size
                    ),
                    modifier = Modifier.weight(1f),
                    label = { Text(label) }
                )
            }
        }
        Text(
            text = runtimeText,
            style = MaterialTheme.typography.bodySmall,
            color = colors.positive
        )
        if (state.failingBatchCount > 0) {
            Text(
                text = stringResource(
                    R.string.third_party_api_settings_refresh_status_failures,
                    state.failingBatchCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (state.refreshMode == OnchainRefreshMode.SMART) {
            Text(
                text = stringResource(R.string.third_party_api_settings_refresh_smart_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText
            )
        } else {
            Text(
                text = stringResource(R.string.third_party_api_settings_fixed_interval_title),
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppPreferences.ONCHAIN_FIXED_INTERVAL_OPTIONS_SECONDS.forEach { seconds ->
                    FilterChip(
                        selected = state.refreshIntervalSeconds == seconds,
                        onClick = { onIntervalChange(seconds) },
                        label = {
                            Text(
                                stringResource(
                                    R.string.third_party_api_settings_interval_seconds,
                                    seconds
                                )
                            )
                        },
                        colors = CoinMonitorComponentDefaults.filterChipColors()
                    )
                }
            }
            Text(
                text = stringResource(R.string.third_party_api_settings_refresh_fixed_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText
            )
        }
    }
}

@Composable
private fun ThirdPartySectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CoinMonitorComponentDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                content()
            }
        )
    }
}

@Composable
private fun FeedbackText(
    savedFlag: Boolean,
    clearedFlag: Boolean,
    errorMessage: String?
) {
    val colors = CoinMonitorThemeTokens.colors
    if (savedFlag) {
        Text(
            text = stringResource(R.string.third_party_api_settings_saved),
            style = MaterialTheme.typography.bodySmall,
            color = colors.positive
        )
    }
    if (clearedFlag) {
        Text(
            text = stringResource(R.string.third_party_api_settings_cleared),
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryText
        )
    }
    errorMessage?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ValidationText(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun SaveClearButtons(
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            modifier = Modifier.weight(1f),
            onClick = onSave,
            colors = CoinMonitorComponentDefaults.primaryButtonColors()
        ) {
            Text(text = stringResource(R.string.third_party_api_settings_save))
        }
        Button(
            modifier = Modifier.weight(1f),
            onClick = onClear
        ) {
            Text(text = stringResource(R.string.third_party_api_settings_clear))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ThirdPartyApiTopBar(
    onBack: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = colors.pageBackground
        ),
        title = {
            Text(text = stringResource(R.string.third_party_api_settings_title))
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back)
                )
            }
        }
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    horizontalPadding: Dp,
    verticalPadding: Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CoinMonitorComponentDefaults.switchColors()
        )
    }
}
