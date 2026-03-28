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
    private val sharedPreferences = createPreferences(context)
    private val credentialsFlow = MutableStateFlow(readCredentials())

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        credentialsFlow.value = readCredentials()
    }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun observeCredentials(): Flow<OkxApiCredentials> = credentialsFlow.asStateFlow()

    override fun getCredentials(): OkxApiCredentials = credentialsFlow.value

    override suspend fun saveCredentials(
        enabled: Boolean,
        apiKey: String,
        secretKey: String,
        passphrase: String
    ) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putString(KEY_API_KEY, apiKey.trim())
                .putString(KEY_SECRET_KEY, secretKey.trim())
                .putString(KEY_PASSPHRASE, passphrase.trim())
                .apply()
        }
    }

    override suspend fun clearCredentials() {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putBoolean(KEY_ENABLED, false)
                .remove(KEY_API_KEY)
                .remove(KEY_SECRET_KEY)
                .remove(KEY_PASSPHRASE)
                .apply()
        }
    }

    private fun readCredentials(): OkxApiCredentials {
        return OkxApiCredentials(
            enabled = sharedPreferences.getBoolean(KEY_ENABLED, false),
            apiKey = sharedPreferences.getString(KEY_API_KEY, "").orEmpty(),
            secretKey = sharedPreferences.getString(KEY_SECRET_KEY, "").orEmpty(),
            passphrase = sharedPreferences.getString(KEY_PASSPHRASE, "").orEmpty()
        )
    }

    private fun createPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Throwable) {
            // 极少数机型在 Keystore 初始化阶段可能失败，这里降级为普通存储保证页面可用。
            context.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    private companion object {
        private const val PREFS_NAME = "okx_api_credentials_secure"
        private const val PREFS_NAME_FALLBACK = "okx_api_credentials_fallback"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SECRET_KEY = "secret_key"
        private const val KEY_PASSPHRASE = "passphrase"
    }
}

