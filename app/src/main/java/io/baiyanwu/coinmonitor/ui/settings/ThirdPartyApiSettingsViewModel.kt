package io.baiyanwu.coinmonitor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.baiyanwu.coinmonitor.data.AppContainer
import io.baiyanwu.coinmonitor.domain.model.OkxApiCredentials
import io.baiyanwu.coinmonitor.domain.repository.OkxCredentialsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ThirdPartyApiSettingsUiState(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val secretKey: String = "",
    val passphrase: String = "",
    val savedFlag: Boolean = false,
    val clearedFlag: Boolean = false,
    val secureStorageAvailable: Boolean = true,
    val errorMessage: String? = null
) {
    val isReadyToEnable: Boolean
        get() = apiKey.isNotBlank() && secretKey.isNotBlank() && passphrase.isNotBlank()
}

class ThirdPartyApiSettingsViewModel(
    private val okxCredentialsRepository: OkxCredentialsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ThirdPartyApiSettingsUiState())
    val uiState: StateFlow<ThirdPartyApiSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            okxCredentialsRepository.observeCredentials().collect { credentials ->
                _uiState.value = credentials.toUiState(
                    secureStorageAvailable = okxCredentialsRepository.isSecureStorageAvailable()
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(enabled = enabled, savedFlag = false, clearedFlag = false, errorMessage = null)
        }
    }

    fun updateApiKey(value: String) {
        _uiState.update {
            it.copy(apiKey = value, savedFlag = false, clearedFlag = false, errorMessage = null)
        }
    }

    fun updateSecretKey(value: String) {
        _uiState.update {
            it.copy(secretKey = value, savedFlag = false, clearedFlag = false, errorMessage = null)
        }
    }

    fun updatePassphrase(value: String) {
        _uiState.update {
            it.copy(passphrase = value, savedFlag = false, clearedFlag = false, errorMessage = null)
        }
    }

    fun saveCredentials() {
        val snapshot = _uiState.value
        viewModelScope.launch {
            runCatching {
                okxCredentialsRepository.saveCredentials(
                    enabled = snapshot.enabled,
                    apiKey = snapshot.apiKey,
                    secretKey = snapshot.secretKey,
                    passphrase = snapshot.passphrase
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(savedFlag = true, clearedFlag = false, errorMessage = null)
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        savedFlag = false,
                        clearedFlag = false,
                        errorMessage = "当前设备不支持安全存储，无法保存 OKX 凭证。"
                    )
                }
            }
        }
    }

    fun clearCredentials() {
        viewModelScope.launch {
            runCatching {
                okxCredentialsRepository.clearCredentials()
            }.onSuccess {
                _uiState.update {
                    it.copy(clearedFlag = true, savedFlag = false, errorMessage = null)
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        clearedFlag = false,
                        savedFlag = false,
                        errorMessage = "当前设备不支持安全存储，无法管理 OKX 凭证。"
                    )
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ThirdPartyApiSettingsViewModel(
                    okxCredentialsRepository = container.okxCredentialsRepository
                )
            }
        }
    }
}

private fun OkxApiCredentials.toUiState(
    secureStorageAvailable: Boolean
): ThirdPartyApiSettingsUiState {
    return ThirdPartyApiSettingsUiState(
        enabled = enabled,
        apiKey = apiKey,
        secretKey = secretKey,
        passphrase = passphrase,
        secureStorageAvailable = secureStorageAvailable
    )
}
