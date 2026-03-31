package io.baiyanwu.coinmonitor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorComponentDefaults
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

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
        onOkxEnabledChange = viewModel::setOkxEnabled,
        onOkxApiKeyChange = viewModel::updateOkxApiKey,
        onOkxSecretKeyChange = viewModel::updateOkxSecretKey,
        onOkxPassphraseChange = viewModel::updateOkxPassphrase,
        onSaveOkx = viewModel::saveOkxCredentials,
        onClearOkx = viewModel::clearOkxCredentials,
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
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showOkxValidationError by rememberSaveable { mutableStateOf(false) }
    var showAiValidationError by rememberSaveable { mutableStateOf(false) }
    val okxOnchainPortalUrl = rememberOkxOnchainPortalUrl(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ThirdPartyApiTopBar(onBack = onBack)

        ThirdPartySectionCard(title = stringResource(R.string.third_party_api_settings_section_okx)) {
            Text(
                text = stringResource(R.string.third_party_api_settings_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText
            )
            Text(
                text = stringResource(R.string.third_party_api_settings_okx_onchain_portal),
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent,
                modifier = Modifier.clickable { uriHandler.openUri(okxOnchainPortalUrl) }
            )
            Text(
                text = okxOnchainPortalUrl,
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent,
                modifier = Modifier.clickable { uriHandler.openUri(okxOnchainPortalUrl) }
            )
            if (!state.okx.secureStorageAvailable) {
                Text(
                    text = stringResource(R.string.third_party_api_settings_secure_storage_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            SettingSwitchRow(
                title = stringResource(R.string.third_party_api_settings_enable_okx),
                checked = state.okx.enabled,
                onCheckedChange = onOkxEnabledChange,
                horizontalPadding = 0.dp,
                verticalPadding = 0.dp
            )
            OutlinedTextField(
                value = state.okx.apiKey,
                onValueChange = {
                    showOkxValidationError = false
                    onOkxApiKeyChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.third_party_api_settings_api_key)) },
                singleLine = true
            )
            OutlinedTextField(
                value = state.okx.secretKey,
                onValueChange = {
                    showOkxValidationError = false
                    onOkxSecretKeyChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.third_party_api_settings_secret_key)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.okx.passphrase,
                onValueChange = {
                    showOkxValidationError = false
                    onOkxPassphraseChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.third_party_api_settings_passphrase)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            if (showOkxValidationError) {
                ValidationText(R.string.third_party_api_settings_validation_required)
            }
            FeedbackText(
                savedFlag = state.okx.savedFlag,
                clearedFlag = state.okx.clearedFlag,
                errorMessage = state.okx.errorMessage
            )
            SaveClearButtons(
                onSave = {
                    val needValidate = state.okx.enabled || state.okx.apiKey.isNotBlank() ||
                        state.okx.secretKey.isNotBlank() || state.okx.passphrase.isNotBlank()
                    if (needValidate && !state.okx.isReadyToEnable) {
                        showOkxValidationError = true
                        return@SaveClearButtons
                    }
                    showOkxValidationError = false
                    onSaveOkx()
                },
                onClear = {
                    showOkxValidationError = false
                    onClearOkx()
                }
            )
        }

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
private fun ThirdPartyApiTopBar(
    onBack: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = stringResource(R.string.third_party_api_settings_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.primaryText,
            modifier = Modifier.padding(start = 34.dp)
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .padding(end = 8.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                modifier = Modifier.size(26.dp),
                tint = colors.primaryText
            )
        }
    }
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

@Composable
private fun rememberOkxOnchainPortalUrl(
    context: android.content.Context
): String {
    val languageTag = context.resources.configuration.locales
        .takeIf { !it.isEmpty }
        ?.get(0)
        ?.toLanguageTag()
        .orEmpty()

    return if (languageTag.startsWith("zh", ignoreCase = true)) {
        "https://web3.okx.com/zh-hans/onchainos/dev-portal"
    } else {
        "https://web3.okx.com/onchainos/dev-portal"
    }
}
