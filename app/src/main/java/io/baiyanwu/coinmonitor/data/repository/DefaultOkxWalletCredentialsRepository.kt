package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.baiyanwu.coinmonitor.domain.model.OkxWalletCredentials
import io.baiyanwu.coinmonitor.domain.repository.OkxWalletCredentialsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DefaultOkxWalletCredentialsRepository(context: Context) : OkxWalletCredentialsRepository {
    private val secure = createPreferences(context.applicationContext)
    private val credentials = MutableStateFlow(read())
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> credentials.value = read() }

    init { secure.preferences?.registerOnSharedPreferenceChangeListener(listener) }

    override fun observeCredentials(): Flow<OkxWalletCredentials> = credentials.asStateFlow()
    override fun getCredentials(): OkxWalletCredentials = credentials.value
    override fun isSecureStorageAvailable(): Boolean = secure.available

    override suspend fun save(credentials: OkxWalletCredentials) = withContext(Dispatchers.IO) {
        require(credentials.isComplete || !credentials.enabled) { "启用 OKX 钱包资产 API 前请完整填写三项凭证。" }
        requirePreferences().edit()
            .putBoolean(KEY_ENABLED, credentials.enabled)
            .putString(KEY_API_KEY, credentials.apiKey.trim())
            .putString(KEY_SECRET_KEY, credentials.secretKey.trim())
            .putString(KEY_PASSPHRASE, credentials.passphrase.trim())
            .apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        requirePreferences().edit().clear().apply()
    }

    private fun read(): OkxWalletCredentials {
        val prefs = secure.preferences ?: return OkxWalletCredentials()
        return OkxWalletCredentials(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            secretKey = prefs.getString(KEY_SECRET_KEY, "").orEmpty(),
            passphrase = prefs.getString(KEY_PASSPHRASE, "").orEmpty()
        )
    }

    private fun requirePreferences(): SharedPreferences = secure.preferences
        ?: error("当前设备的 Android Keystore / EncryptedSharedPreferences 不可用，已拒绝降级到明文存储。")

    private fun createPreferences(context: Context): SecurePreferencesResult = try {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        SecurePreferencesResult(true, EncryptedSharedPreferences.create(
            context, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ))
    } catch (error: Throwable) {
        Log.e("SecureStorage", "Failed to create encrypted OKX wallet credential storage", error)
        SecurePreferencesResult(false, null)
    }

    private data class SecurePreferencesResult(val available: Boolean, val preferences: SharedPreferences?)

    companion object {
        internal const val PREFS_NAME = "okx_wallet_credentials_secure"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SECRET_KEY = "secret_key"
        private const val KEY_PASSPHRASE = "passphrase"
    }
}

class DefaultWalletWatchPreferencesRepository(context: Context) : io.baiyanwu.coinmonitor.domain.repository.WalletWatchPreferencesRepository {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    override fun getLastAddress(): String? = preferences.getString(KEY_LAST_ADDRESS, null)?.takeIf(String::isNotBlank)
    override suspend fun saveLastAddress(address: String) = withContext(Dispatchers.IO) {
        preferences.edit().putString(KEY_LAST_ADDRESS, address.trim()).apply()
    }
    override fun getHiddenAssetIds(address: String): Set<String> =
        preferences.getStringSet(hiddenAssetsKey(address), emptySet()).orEmpty().toSet()

    override suspend fun saveHiddenAssetIds(address: String, assetIds: Set<String>) = withContext(Dispatchers.IO) {
        preferences.edit().putStringSet(hiddenAssetsKey(address), assetIds.toSet()).apply()
    }
    override fun getHiddenChainIndexes(address: String): Set<String> =
        preferences.getStringSet(hiddenChainsKey(address), emptySet()).orEmpty().toSet()

    override suspend fun saveHiddenChainIndexes(address: String, chainIndexes: Set<String>) = withContext(Dispatchers.IO) {
        preferences.edit().putStringSet(hiddenChainsKey(address), chainIndexes.toSet()).apply()
    }
    override fun getHideSmallAssets(address: String): Boolean =
        preferences.getBoolean(hideSmallAssetsKey(address), true)

    override suspend fun saveHideSmallAssets(address: String, hide: Boolean) = withContext(Dispatchers.IO) {
        preferences.edit().putBoolean(hideSmallAssetsKey(address), hide).apply()
    }

    override fun getIncludeRiskAssets(address: String): Boolean =
        preferences.getBoolean(includeRiskAssetsKey(address), false)

    override suspend fun saveIncludeRiskAssets(address: String, include: Boolean) = withContext(Dispatchers.IO) {
        preferences.edit().putBoolean(includeRiskAssetsKey(address), include).apply()
    }

    private fun hiddenAssetsKey(address: String): String {
        val normalized = address.trim().let { if (it.startsWith("0x", ignoreCase = true)) it.lowercase() else it }
        return "hidden_assets_$normalized"
    }

    private fun hiddenChainsKey(address: String): String {
        val normalized = address.trim().let { if (it.startsWith("0x", ignoreCase = true)) it.lowercase() else it }
        return "hidden_chains_$normalized"
    }

    private fun hideSmallAssetsKey(address: String): String = "hide_small_assets_${normalizedAddress(address)}"

    private fun includeRiskAssetsKey(address: String): String = "include_risk_assets_${normalizedAddress(address)}"

    private fun normalizedAddress(address: String): String =
        address.trim().let { if (it.startsWith("0x", ignoreCase = true)) it.lowercase() else it }

    private companion object {
        const val PREFS_NAME = "wallet_watch_preferences"
        const val KEY_LAST_ADDRESS = "last_address"
    }
}
