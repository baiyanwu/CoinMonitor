package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.baiyanwu.coinmonitor.domain.model.AppPreferences

/**
 * 只读取旧加密设置中的轮询周期，写入普通应用偏好后立即删除整份旧凭证文件。
 */
internal fun migrateLegacyOnchainSettings(context: Context) {
    val target = context.getSharedPreferences(
        DefaultAppPreferencesRepository.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    if (target.getBoolean(KEY_MIGRATION_COMPLETED, false)) return

    val legacyInterval = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            LEGACY_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).getInt(
            LEGACY_INTERVAL_KEY,
            AppPreferences.DEFAULT_ONCHAIN_REFRESH_INTERVAL_SECONDS
        )
    }.getOrNull()

    target.edit().apply {
        if (!target.contains(DefaultAppPreferencesRepository.KEY_ONCHAIN_REFRESH_INTERVAL_SECONDS)) {
            putInt(
                DefaultAppPreferencesRepository.KEY_ONCHAIN_REFRESH_INTERVAL_SECONDS,
                AppPreferences.normalizeOnchainRefreshIntervalSeconds(
                    legacyInterval ?: AppPreferences.DEFAULT_ONCHAIN_REFRESH_INTERVAL_SECONDS
                )
            )
        }
        putBoolean(KEY_MIGRATION_COMPLETED, true)
    }.apply()
    context.deleteSharedPreferences(LEGACY_PREFS_NAME)
}

private const val LEGACY_PREFS_NAME = "okx_api_credentials_secure"
private const val LEGACY_INTERVAL_KEY = "dex_polling_interval_seconds"
private const val KEY_MIGRATION_COMPLETED = "public_onchain_settings_migrated"
