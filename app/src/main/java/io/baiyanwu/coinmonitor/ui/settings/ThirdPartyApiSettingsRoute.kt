package io.baiyanwu.coinmonitor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
        onEnabledChange = viewModel::setEnabled,
        onApiKeyChange = viewModel::updateApiKey,
        onSecretKeyChange = viewModel::updateSecretKey,
        onPassphraseChange = viewModel::updatePassphrase,
        onSave = viewModel::saveCredentials,
        onClear = viewModel::clearCredentials
    )
}

@Composable
private fun ThirdPartyApiSettingsScreen(
    state: ThirdPartyApiSettingsUiState,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSecretKeyChange: (String) -> Unit,
    onPassphraseChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    val colors = CoinMonitorThemeTokens.colors
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showValidationError by rememberSaveable { mutableStateOf(false) }
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

        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CoinMonitorComponentDefaults.elevatedCardColors()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.third_party_api_settings_section_okx),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.third_party_api_settings_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText
                )
                Text(
                    text = stringResource(R.string.third_party_api_settings_okx_onchain_portal),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accent,
                    modifier = Modifier.clickable {
                        uriHandler.openUri(okxOnchainPortalUrl)
                    }
                )
                Text(
                    text = okxOnchainPortalUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accent,
                    modifier = Modifier.clickable {
                        uriHandler.openUri(okxOnchainPortalUrl)
                    }
                )
                if (!state.secureStorageAvailable) {
                    Text(
                        text = stringResource(R.string.third_party_api_settings_secure_storage_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                SettingSwitchRow(
                    title = stringResource(R.string.third_party_api_settings_enable_okx),
                    checked = state.enabled,
                    onCheckedChange = onEnabledChange,
                    horizontalPadding = 0.dp,
                    verticalPadding = 0.dp
                )

                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = {
                        showValidationError = false
                        onApiKeyChange(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.third_party_api_settings_api_key)) },
                    singleLine = true
                )

                OutlinedTextField(
                    value = state.secretKey,
                    onValueChange = {
                        showValidationError = false
                        onSecretKeyChange(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.third_party_api_settings_secret_key)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = state.passphrase,
                    onValueChange = {
                        showValidationError = false
                        onPassphraseChange(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.third_party_api_settings_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )

                if (showValidationError) {
                    Text(
                        text = stringResource(R.string.third_party_api_settings_validation_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (state.savedFlag) {
                    Text(
                        text = stringResource(R.string.third_party_api_settings_saved),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.positive
                    )
                }
                if (state.clearedFlag) {
                    Text(
                        text = stringResource(R.string.third_party_api_settings_cleared),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText
                    )
                }
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val needValidate = state.enabled || state.apiKey.isNotBlank() ||
                                state.secretKey.isNotBlank() || state.passphrase.isNotBlank()
                            if (needValidate && !state.isReadyToEnable) {
                                showValidationError = true
                                return@Button
                            }
                            showValidationError = false
                            onSave()
                        },
                        colors = CoinMonitorComponentDefaults.primaryButtonColors()
                    ) {
                        Text(text = stringResource(R.string.third_party_api_settings_save))
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showValidationError = false
                            onClear()
                        }
                    ) {
                        Text(text = stringResource(R.string.third_party_api_settings_clear))
                    }
                }
            }
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
    horizontalPadding: androidx.compose.ui.unit.Dp,
    verticalPadding: androidx.compose.ui.unit.Dp
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
