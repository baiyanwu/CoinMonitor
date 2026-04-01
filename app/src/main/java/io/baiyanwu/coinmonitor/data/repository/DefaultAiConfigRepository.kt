package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.baiyanwu.coinmonitor.domain.model.OpenAiCompatibleConfig
import io.baiyanwu.coinmonitor.domain.repository.AiConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DefaultAiConfigRepository(
    context: Context
) : AiConfigRepository {
    private val securePreferencesResult = createPreferences(context)
    private val sharedPreferences = securePreferencesResult.preferences
    private val configFlow = MutableStateFlow(readConfig())

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        configFlow.value = readConfig()
    }

    init {
        sharedPreferences?.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun observeConfig(): Flow<OpenAiCompatibleConfig> = configFlow.asStateFlow()

    override fun getConfig(): OpenAiCompatibleConfig = configFlow.value

    override fun isSecureStorageAvailable(): Boolean = securePreferencesResult.available

    override suspend fun saveConfig(
        enabled: Boolean,
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String
    ) {
        withContext(Dispatchers.IO) {
            requirePreferences().edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putString(KEY_BASE_URL, baseUrl.trim())
                .putString(KEY_API_KEY, apiKey.trim())
                .putString(KEY_MODEL, model.trim())
                .putString(KEY_SYSTEM_PROMPT, systemPrompt)
                .apply()
        }
    }

    override suspend fun clearConfig() {
        withContext(Dispatchers.IO) {
            requirePreferences().edit()
                .putBoolean(KEY_ENABLED, false)
                .remove(KEY_BASE_URL)
                .remove(KEY_API_KEY)
                .remove(KEY_MODEL)
                .remove(KEY_SYSTEM_PROMPT)
                .apply()
        }
    }

    private fun readConfig(): OpenAiCompatibleConfig {
        val preferences = sharedPreferences ?: return OpenAiCompatibleConfig()
        return OpenAiCompatibleConfig(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty(),
            apiKey = preferences.getString(KEY_API_KEY, "").orEmpty(),
            model = preferences.getString(KEY_MODEL, "").orEmpty(),
            systemPrompt = preferences.getString(
                KEY_SYSTEM_PROMPT,
                OpenAiCompatibleConfig.DEFAULT_SYSTEM_PROMPT
            ).orEmpty()
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
            SecurePreferencesResult(available = false, preferences = null)
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
        private const val PREFS_NAME = "openai_compatible_config_secure"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
    }
}
