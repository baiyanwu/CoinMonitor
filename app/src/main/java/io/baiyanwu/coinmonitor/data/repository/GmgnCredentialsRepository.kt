package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class GmgnCredentialsRepository(context: Context) {
    private val preferences = runCatching {
        val app = context.applicationContext
        val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(app, PREFS_NAME, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }.getOrNull()
    val secureStorageAvailable: Boolean get() = preferences != null
    private val key = MutableStateFlow(preferences?.getString("api_key", "").orEmpty())
    val apiKey = key.asStateFlow()
    suspend fun save(value: String) = withContext(Dispatchers.IO) {
        val clean = value.trim()
        require(clean.isNotEmpty() && clean.none { it == '\r' || it == '\n' })
        check(requireNotNull(preferences).edit().putString("api_key", clean).commit())
        key.value = clean
    }
    suspend fun clear() = withContext(Dispatchers.IO) {
        check(requireNotNull(preferences).edit().clear().commit())
        key.value = ""
    }
    companion object { const val PREFS_NAME = "gmgn_market_credentials_secure" }
}
