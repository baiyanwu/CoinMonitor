package io.baiyanwu.coinmonitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.AppPreferences
import io.baiyanwu.coinmonitor.domain.model.OpenAiCompatibleConfig
import io.baiyanwu.coinmonitor.domain.repository.AiConfigRepository
import io.baiyanwu.coinmonitor.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnchainSettingsFormState(
    val refreshIntervalSeconds: Int = AppPreferences.DEFAULT_ONCHAIN_REFRESH_INTERVAL_SECONDS,
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
    val ai: AiSettingsFormState = AiSettingsFormState()
)

class ThirdPartyApiSettingsViewModel(
    private val appPreferencesRepository: AppPreferencesRepository,
    private val aiConfigRepository: AiConfigRepository
) : ViewModel() {
    private val onchainUiState = MutableStateFlow(OnchainSettingsFormState())
    private val aiUiState = MutableStateFlow(AiSettingsFormState())
    private val _uiState = MutableStateFlow(ThirdPartyApiSettingsUiState())
    val uiState: StateFlow<ThirdPartyApiSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(onchainUiState, aiUiState) { onchain, ai ->
                ThirdPartyApiSettingsUiState(onchain = onchain, ai = ai)
            }.collect { _uiState.value = it }
        }
        viewModelScope.launch {
            appPreferencesRepository.observePreferences().collect { preferences ->
                onchainUiState.update { state ->
                    state.copy(refreshIntervalSeconds = preferences.onchainRefreshIntervalSeconds)
                }
            }
        }
        viewModelScope.launch {
            aiConfigRepository.observeConfig().collect { config ->
                aiUiState.value = config.toUiState(aiConfigRepository.isSecureStorageAvailable())
            }
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

    fun saveOnchainSettings() {
        val interval = onchainUiState.value.refreshIntervalSeconds
        viewModelScope.launch {
            runCatching { appPreferencesRepository.setOnchainRefreshIntervalSeconds(interval) }
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
                    aiConfigRepository = container.aiConfigRepository
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
