package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.baiyanwu.coinmonitor.domain.model.OkxApiCredentials
import io.baiyanwu.coinmonitor.domain.repository.OkxCredentialsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DefaultOkxCredentialsRepository(
    context: Context
) : OkxCredentialsRepository {
    private val securePreferencesResult = createPreferences(context)
    private val sharedPreferences = securePreferencesResult.preferences
    private val credentialsFlow = MutableStateFlow(readCredentials())

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        credentialsFlow.value = readCredentials()
    }

    init {
        sharedPreferences?.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun observeCredentials(): Flow<OkxApiCredentials> = credentialsFlow.asStateFlow()

    override fun getCredentials(): OkxApiCredentials = credentialsFlow.value

    override fun isSecureStorageAvailable(): Boolean = securePreferencesResult.available

    override suspend fun saveCredentials(
        enabled: Boolean,
        apiKey: String,
        secretKey: String,
        passphrase: String
    ) {
        withContext(Dispatchers.IO) {
            requirePreferences().edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putString(KEY_API_KEY, apiKey.trim())
                .putString(KEY_SECRET_KEY, secretKey.trim())
                .putString(KEY_PASSPHRASE, passphrase.trim())
                .apply()
        }
    }

    override suspend fun clearCredentials() {
        withContext(Dispatchers.IO) {
            requirePreferences().edit()
                .putBoolean(KEY_ENABLED, false)
                .remove(KEY_API_KEY)
                .remove(KEY_SECRET_KEY)
                .remove(KEY_PASSPHRASE)
                .apply()
        }
    }

    private fun readCredentials(): OkxApiCredentials {
        val preferences = sharedPreferences ?: return OkxApiCredentials()
        return OkxApiCredentials(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            apiKey = preferences.getString(KEY_API_KEY, "").orEmpty(),
            secretKey = preferences.getString(KEY_SECRET_KEY, "").orEmpty(),
            passphrase = preferences.getString(KEY_PASSPHRASE, "").orEmpty()
        )
    }

    private fun createPreferences(context: Context): SecurePreferencesResult {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            SecurePreferencesResult(
                available = true,
                preferences = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            )
        } catch (_: Throwable) {
            SecurePreferencesResult(
                available = false,
                preferences = null
            )
        }
    }

    private fun requirePreferences(): SharedPreferences {
        return sharedPreferences ?: error(
            "当前设备的 Android Keystore / EncryptedSharedPreferences 不可用，已拒绝降级到明文存储。"
        )
    }

    private data class SecurePreferencesResult(
        val available: Boolean,
        val preferences: SharedPreferences?
    )

    private companion object {
        private const val PREFS_NAME = "okx_api_credentials_secure"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SECRET_KEY = "secret_key"
        private const val KEY_PASSPHRASE = "passphrase"
    }
}
