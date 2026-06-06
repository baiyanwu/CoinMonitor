package io.baiyanwu.coinmonitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.OkxApiCredentials
import io.baiyanwu.coinmonitor.domain.model.OpenAiCompatibleConfig
import io.baiyanwu.coinmonitor.domain.repository.AiConfigRepository
import io.baiyanwu.coinmonitor.domain.repository.OkxCredentialsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OkxSettingsFormState(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val secretKey: String = "",
    val passphrase: String = "",
    val secureStorageAvailable: Boolean = true,
    val savedFlag: Boolean = false,
    val clearedFlag: Boolean = false,
    val errorMessage: String? = null
) {
    val isReadyToEnable: Boolean
        get() = apiKey.isNotBlank() && secretKey.isNotBlank() && passphrase.isNotBlank()
}

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
    val okx: OkxSettingsFormState = OkxSettingsFormState(),
    val ai: AiSettingsFormState = AiSettingsFormState()
)

class ThirdPartyApiSettingsViewModel(
    private val okxCredentialsRepository: OkxCredentialsRepository,
    private val aiConfigRepository: AiConfigRepository
) : ViewModel() {
    private val okxUiState = MutableStateFlow(OkxSettingsFormState())
    private val aiUiState = MutableStateFlow(AiSettingsFormState())
    private val _uiState = MutableStateFlow(ThirdPartyApiSettingsUiState())
    val uiState: StateFlow<ThirdPartyApiSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(okxUiState, aiUiState) { okx, ai ->
                ThirdPartyApiSettingsUiState(okx = okx, ai = ai)
            }.collect { _uiState.value = it }
        }
        viewModelScope.launch {
            okxCredentialsRepository.observeCredentials().collect { credentials ->
                okxUiState.value = credentials.toUiState(
                    secureStorageAvailable = okxCredentialsRepository.isSecureStorageAvailable()
                )
            }
        }
        viewModelScope.launch {
            aiConfigRepository.observeConfig().collect { config ->
                aiUiState.value = config.toUiState(
                    secureStorageAvailable = aiConfigRepository.isSecureStorageAvailable()
                )
            }
        }
    }

    fun setOkxEnabled(enabled: Boolean) {
        okxUiState.update {
            it.copy(enabled = enabled, savedFlag = false, clearedFlag = false, errorMessage = null)
        }
    }

    fun updateOkxApiKey(value: String) {
        okxUiState.update {
            it.copy(apiKey = value, savedFlag = false, clearedFlag = false, errorMessage = null)
        }
    }

    fun updateOkxSecretKey(value: String) {
        okxUiState.update {
            it.copy(secretKey = value, savedFlag = false, clearedFlag = false, errorMessage = null)
        }
    }

    fun updateOkxPassphrase(value: String) {
        okxUiState.update {
            it.copy(passphrase = value, savedFlag = false, clearedFlag = false, errorMessage = null)
        }
    }

    fun saveOkxCredentials() {
        val snapshot = okxUiState.value
        viewModelScope.launch {
            runCatching {
                okxCredentialsRepository.saveCredentials(
                    enabled = snapshot.enabled,
                    apiKey = snapshot.apiKey,
                    secretKey = snapshot.secretKey,
                    passphrase = snapshot.passphrase
                )
            }.onSuccess {
                okxUiState.update { it.copy(savedFlag = true, clearedFlag = false, errorMessage = null) }
            }.onFailure {
                okxUiState.update {
                    it.copy(
                        savedFlag = false,
                        clearedFlag = false,
                        errorMessage = "当前设备不支持安全存储，无法保存 OKX 凭证。"
                    )
                }
            }
        }
    }

    fun clearOkxCredentials() {
        viewModelScope.launch {
            runCatching { okxCredentialsRepository.clearCredentials() }
                .onSuccess {
                    okxUiState.update {
                        it.copy(clearedFlag = true, savedFlag = false, errorMessage = null)
                    }
                }
                .onFailure {
                    okxUiState.update {
                        it.copy(
                            savedFlag = false,
                            clearedFlag = false,
                            errorMessage = "当前设备不支持安全存储，无法管理 OKX 凭证。"
                        )
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
                    okxCredentialsRepository = container.okxCredentialsRepository,
                    aiConfigRepository = container.aiConfigRepository
                )
            }
        }
    }
}

private fun OkxApiCredentials.toUiState(
    secureStorageAvailable: Boolean
): OkxSettingsFormState {
    return OkxSettingsFormState(
        enabled = enabled,
        apiKey = apiKey,
        secretKey = secretKey,
        passphrase = passphrase,
        secureStorageAvailable = secureStorageAvailable
    )
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
