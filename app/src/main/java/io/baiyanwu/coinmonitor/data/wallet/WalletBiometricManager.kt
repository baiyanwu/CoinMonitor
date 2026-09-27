package io.baiyanwu.coinmonitor.data.wallet

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps the password-derived vault key with an authentication-bound Android Keystore key.
 * The stored preference contains ciphertext only and is excluded from device backup.
 */
class WalletBiometricManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun canAuthenticate(): Boolean = BiometricManager.from(appContext).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    ) == BiometricManager.BIOMETRIC_SUCCESS

    fun isEnabled(): Boolean = preferences.contains(KEY_CIPHERTEXT) && preferences.contains(KEY_IV) &&
        keyStore.containsAlias(KEY_ALIAS)

    fun prepareEnableCipher(): Cipher {
        clear()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        generator.init(builder.build())
        val key = generator.generateKey()
        return newCipher().apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    fun finishEnable(cipher: Cipher, derivedKey: ByteArray) {
        val wrapped = cipher.doFinal(derivedKey)
        try {
            check(preferences.edit()
                .putString(KEY_IV, cipher.iv.encode())
                .putString(KEY_CIPHERTEXT, wrapped.encode())
                .commit()) { "无法保存生物识别钱包凭证。" }
        } finally {
            wrapped.fill(0)
        }
    }

    fun prepareUnlockCipher(): Cipher {
        check(isEnabled()) { "生物识别解锁尚未启用。" }
        val key = keyStore.getKey(KEY_ALIAS, null) as SecretKey
        val iv = preferences.getString(KEY_IV, null)?.decode() ?: error("生物识别凭证已损坏。")
        return newCipher().apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
    }

    fun unwrapDerivedKey(cipher: Cipher): ByteArray {
        val wrapped = preferences.getString(KEY_CIPHERTEXT, null)?.decode() ?: error("生物识别凭证已损坏。")
        return try {
            cipher.doFinal(wrapped)
        } finally {
            wrapped.fill(0)
        }
    }

    fun clear() {
        preferences.edit().clear().commit()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun newCipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "coinmonitor.wallet.biometric.v1"
        private const val PREFERENCES = "self_custody_wallet_biometric"
        private const val KEY_IV = "iv"
        private const val KEY_CIPHERTEXT = "ciphertext"
    }
}
