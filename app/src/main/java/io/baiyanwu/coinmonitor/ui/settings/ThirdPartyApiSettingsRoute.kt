package io.baiyanwu.coinmonitor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import io.baiyanwu.coinmonitor.domain.model.OnchainRefreshMode
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

private const val SHOW_AI_SETTINGS_ENTRY = false

@Composable
fun ThirdPartyApiSettingsRoute(
    container: AppContainer,
    onBack: () -> Unit
) {
    val viewModel: ThirdPartyApiSettingsViewModel = viewModel(
        factory = ThirdPartyApiSettingsViewModel.factory(container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ThirdPartyApiSettingsScreen(
        state = state,
        onBack = onBack,
        onOnchainRefreshModeChange = viewModel::updateOnchainRefreshMode,
        onOnchainRefreshIntervalChange = viewModel::updateOnchainRefreshIntervalSeconds,
        onSaveOnchain = viewModel::saveOnchainSettings,
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
    onBack: () -> Unit,
    onOnchainRefreshModeChange: (OnchainRefreshMode) -> Unit,
    onOnchainRefreshIntervalChange: (Int) -> Unit,
    onSaveOnchain: () -> Unit,
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

    Scaffold(
        containerColor = colors.pageBackground,
        topBar = {
            ThirdPartyApiTopBar(onBack = onBack)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.pageBackground)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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

            if (SHOW_AI_SETTINGS_ENTRY) {
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
