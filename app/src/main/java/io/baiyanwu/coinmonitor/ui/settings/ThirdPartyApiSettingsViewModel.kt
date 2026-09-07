package io.baiyanwu.coinmonitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.data.refresh.GlobalQuoteRefreshCoordinator
import io.baiyanwu.coinmonitor.domain.model.AppPreferences
import io.baiyanwu.coinmonitor.domain.model.OnchainRefreshMode
import io.baiyanwu.coinmonitor.domain.model.OpenAiCompatibleConfig
import io.baiyanwu.coinmonitor.domain.model.OkxWalletCredentials
import io.baiyanwu.coinmonitor.domain.repository.AiConfigRepository
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import io.baiyanwu.coinmonitor.domain.repository.OkxWalletCredentialsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnchainSettingsFormState(
    val refreshMode: OnchainRefreshMode = OnchainRefreshMode.SMART,
    val refreshIntervalSeconds: Int = AppPreferences.DEFAULT_ONCHAIN_REFRESH_INTERVAL_SECONDS,
    val requestBatchCount: Int = 0,
    val cycleIntervalSeconds: Int = 30,
    val requestSpacingMillis: Long = 0L,
    val failingBatchCount: Int = 0,
    val runtimeActive: Boolean = false,
    val savedFlag: Boolean = false,
    val errorMessage: String? = null
)

data class AiSettingsFormState(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val systemPrompt: String = OpenAiCompatibleConfig.DEFAULT_SYSTEM_PROMPT,
    val secureStorageAvailable: Boolean = true,
    val savedFlag: Boolean = false,
    val clearedFlag: Boolean = false,
    val errorMessage: String? = null
) {
    val isReadyToEnable: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

data class ThirdPartyApiSettingsUiState(
    val onchain: OnchainSettingsFormState = OnchainSettingsFormState(),
    val okxWallet: OkxWalletSettingsFormState = OkxWalletSettingsFormState(),
    val ai: AiSettingsFormState = AiSettingsFormState()
)

data class OkxWalletSettingsFormState(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val secretKey: String = "",
    val passphrase: String = "",
    val secureStorageAvailable: Boolean = true,
    val savedFlag: Boolean = false,
    val clearedFlag: Boolean = false,
    val errorMessage: String? = null
) {
    val isComplete: Boolean get() = apiKey.isNotBlank() && secretKey.isNotBlank() && passphrase.isNotBlank()
}

class ThirdPartyApiSettingsViewModel(
    private val appPreferencesRepository: AppPreferencesRepository,
    private val aiConfigRepository: AiConfigRepository,
    private val okxWalletCredentialsRepository: OkxWalletCredentialsRepository,
    private val quoteRefreshCoordinator: GlobalQuoteRefreshCoordinator
) : ViewModel() {
    private val onchainUiState = MutableStateFlow(OnchainSettingsFormState())
    private val aiUiState = MutableStateFlow(AiSettingsFormState())
    private val okxWalletUiState = MutableStateFlow(OkxWalletSettingsFormState())
    private val _uiState = MutableStateFlow(ThirdPartyApiSettingsUiState())
    val uiState: StateFlow<ThirdPartyApiSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(onchainUiState, okxWalletUiState, aiUiState) { onchain, okxWallet, ai ->
                ThirdPartyApiSettingsUiState(onchain = onchain, okxWallet = okxWallet, ai = ai)
            }.collect { _uiState.value = it }
        }
        viewModelScope.launch {
            appPreferencesRepository.observePreferences().collect { preferences ->
                onchainUiState.update { state ->
                    state.copy(
                        refreshMode = preferences.onchainRefreshMode,
                        refreshIntervalSeconds = preferences.onchainRefreshIntervalSeconds
                    )
                }
            }
        }
        viewModelScope.launch {
            quoteRefreshCoordinator.onchainRefreshRuntimeState.collect { runtime ->
                onchainUiState.update { state ->
                    state.copy(
                        requestBatchCount = runtime.requestBatchCount,
                        cycleIntervalSeconds = runtime.cycleIntervalSeconds,
                        requestSpacingMillis = runtime.requestSpacingMillis,
                        failingBatchCount = runtime.failingBatchCount,
                        runtimeActive = runtime.active,
                    )
                }
            }
        }
        viewModelScope.launch {
            okxWalletCredentialsRepository.observeCredentials().collect { value ->
                okxWalletUiState.value = OkxWalletSettingsFormState(
                    enabled = value.enabled,
                    apiKey = value.apiKey,
                    secretKey = value.secretKey,
                    passphrase = value.passphrase,
                    secureStorageAvailable = okxWalletCredentialsRepository.isSecureStorageAvailable()
                )
            }
        }
        viewModelScope.launch {
            aiConfigRepository.observeConfig().collect { config ->
                aiUiState.value = config.toUiState(aiConfigRepository.isSecureStorageAvailable())
            }
        }
    }

    fun setOkxWalletEnabled(value: Boolean) = updateOkx { it.copy(enabled = value) }
    fun updateOkxWalletApiKey(value: String) = updateOkx { it.copy(apiKey = value) }
    fun updateOkxWalletSecretKey(value: String) = updateOkx { it.copy(secretKey = value) }
    fun updateOkxWalletPassphrase(value: String) = updateOkx { it.copy(passphrase = value) }

    private fun updateOkx(block: (OkxWalletSettingsFormState) -> OkxWalletSettingsFormState) {
        okxWalletUiState.update { block(it).copy(savedFlag = false, clearedFlag = false, errorMessage = null) }
    }

    fun saveOkxWalletCredentials() {
        val state = okxWalletUiState.value
        viewModelScope.launch {
            runCatching {
                okxWalletCredentialsRepository.save(
                    OkxWalletCredentials(state.enabled, state.apiKey, state.secretKey, state.passphrase)
                )
            }.onSuccess {
                okxWalletUiState.update { it.copy(savedFlag = true, clearedFlag = false, errorMessage = null) }
            }.onFailure { error ->
                okxWalletUiState.update { it.copy(savedFlag = false, errorMessage = error.message ?: "OKX 凭证保存失败") }
            }
        }
    }

    fun clearOkxWalletCredentials() {
        viewModelScope.launch {
            runCatching { okxWalletCredentialsRepository.clear() }
                .onSuccess { okxWalletUiState.update { OkxWalletSettingsFormState(secureStorageAvailable = it.secureStorageAvailable, clearedFlag = true) } }
                .onFailure { error -> okxWalletUiState.update { it.copy(errorMessage = error.message ?: "OKX 凭证清除失败") } }
        }
    }

    fun updateOnchainRefreshIntervalSeconds(value: Int) {
        onchainUiState.update {
            it.copy(
                refreshIntervalSeconds = AppPreferences.normalizeOnchainRefreshIntervalSeconds(value),
                savedFlag = false,
                errorMessage = null
            )
        }
    }

    fun updateOnchainRefreshMode(mode: OnchainRefreshMode) {
        onchainUiState.update {
            it.copy(
                refreshMode = mode,
                savedFlag = false,
                errorMessage = null
            )
        }
    }

    fun saveOnchainSettings() {
        val snapshot = onchainUiState.value
        viewModelScope.launch {
            runCatching {
                appPreferencesRepository.setOnchainRefreshSettings(
                    mode = snapshot.refreshMode,
                    seconds = snapshot.refreshIntervalSeconds
                )
            }
                .onSuccess {
                    onchainUiState.update { it.copy(savedFlag = true, errorMessage = null) }
                }
                .onFailure { error ->
                    onchainUiState.update {
                        it.copy(savedFlag = false, errorMessage = error.message ?: "链上刷新设置保存失败")
                    }
                }
        }
    }

    fun setAiEnabled(enabled: Boolean) {
        aiUiState.update {
            it.copy(enabled = enabled, savedFlag = false, clearedFlag = false, errorMessage = null)
        }
    }

    fun updateAiBaseUrl(value: String) {
        aiUiState.update {
            it.copy(baseUrl = value, savedFlag = false, clearedFlag = false, errorMessage = null)
        }
    }

    fun updateAiApiKey(value: String) {
        aiUiState.update {
            it.copy(apiKey = value, savedFlag = false, clearedFlag = false, errorMessage = null)
        }
    }

    fun updateAiModel(value: String) {
        aiUiState.update {
            it.copy(model = value, savedFlag = false, clearedFlag = false, errorMessage = null)
        }
    }

    fun updateAiSystemPrompt(value: String) {
        aiUiState.update {
            it.copy(systemPrompt = value, savedFlag = false, clearedFlag = false, errorMessage = null)
        }
    }

    fun saveAiConfig() {
        val snapshot = aiUiState.value
        viewModelScope.launch {
            runCatching {
                aiConfigRepository.saveConfig(
                    enabled = snapshot.enabled,
                    baseUrl = snapshot.baseUrl,
                    apiKey = snapshot.apiKey,
                    model = snapshot.model,
                    systemPrompt = snapshot.systemPrompt
                )
            }.onSuccess {
                aiUiState.update { it.copy(savedFlag = true, clearedFlag = false, errorMessage = null) }
            }.onFailure {
                aiUiState.update {
                    it.copy(
                        savedFlag = false,
                        clearedFlag = false,
                        errorMessage = "当前设备不支持安全存储，无法保存 AI 配置。"
                    )
                }
            }
        }
    }

    fun clearAiConfig() {
        viewModelScope.launch {
            runCatching { aiConfigRepository.clearConfig() }
                .onSuccess {
                    aiUiState.update {
                        it.copy(clearedFlag = true, savedFlag = false, errorMessage = null)
                    }
                }
                .onFailure {
                    aiUiState.update {
                        it.copy(
                            savedFlag = false,
                            clearedFlag = false,
                            errorMessage = "当前设备不支持安全存储，无法管理 AI 配置。"
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ThirdPartyApiSettingsViewModel(
                    appPreferencesRepository = container.appPreferencesRepository,
                    aiConfigRepository = container.aiConfigRepository,
                    okxWalletCredentialsRepository = container.okxWalletCredentialsRepository,
                    quoteRefreshCoordinator = container.globalQuoteRefreshCoordinator
                )
            }
        }
    }
}

private fun OpenAiCompatibleConfig.toUiState(
    secureStorageAvailable: Boolean
): AiSettingsFormState {
    return AiSettingsFormState(
        enabled = enabled,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        systemPrompt = systemPrompt,
        secureStorageAvailable = secureStorageAvailable
    )
}
