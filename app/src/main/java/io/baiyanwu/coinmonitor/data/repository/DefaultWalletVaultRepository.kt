package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.baiyanwu.coinmonitor.data.wallet.WalletCoreKeyService
import io.baiyanwu.coinmonitor.domain.model.WalletKind
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.model.WalletPasswordPolicy
import io.baiyanwu.coinmonitor.domain.model.WalletPrivateKeyType
import io.baiyanwu.coinmonitor.domain.model.WalletProfile
import io.baiyanwu.coinmonitor.domain.repository.WalletVaultRepository
import io.baiyanwu.coinmonitor.domain.repository.WalletVaultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class DefaultWalletVaultRepository(context: Context) : WalletVaultRepository {
    private val securePreferences = createSecurePreferences(context.applicationContext)
    private val keyService = WalletCoreKeyService()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val random = SecureRandom()
    private val state = MutableStateFlow(
        WalletVaultState(
            initialized = securePreferences.preferences?.contains(KEY_ENVELOPE) == true,
            secureStorageAvailable = securePreferences.available
        )
    )
    private var payload: VaultPayload? = null
    private var derivedKey: ByteArray? = null
    private val mutationMutex = Mutex()

    override fun observeState(): Flow<WalletVaultState> = state.asStateFlow()
    override fun currentState(): WalletVaultState = state.value

    override suspend fun createVault(password: CharArray) = withContext(Dispatchers.IO) {
        require(!state.value.initialized) { "钱包保险库已经存在。" }
        validatePassword(password)
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val key = deriveKey(password, salt)
        val newPayload = VaultPayload()
        payload = newPayload
        replaceDerivedKey(key)
        persist(newPayload, salt)
        publish(newPayload, initialized = true)
        password.fill('\u0000')
    }

    override suspend fun unlock(password: CharArray): Boolean = withContext(Dispatchers.IO) {
        val envelope = readEnvelope() ?: return@withContext false
        val key = deriveKey(password, envelope.salt.decodeBase64())
        password.fill('\u0000')
        val decoded = runCatching { decryptPayload(envelope, key) }.getOrNull()
        if (decoded == null) {
            key.fill(0)
            false
        } else {
            payload = decoded
            replaceDerivedKey(key)
            publish(decoded, initialized = true)
            true
        }
    }

    override suspend fun unlockWithDerivedKey(derivedKey: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val envelope = readEnvelope() ?: return@withContext false
        val decoded = runCatching { decryptPayload(envelope, derivedKey) }.getOrNull()
        if (decoded == null) {
            false
        } else {
            payload = decoded
            replaceDerivedKey(derivedKey.copyOf())
            publish(decoded, initialized = true)
            true
        }
    }

    override fun lock() {
        payload = null
        replaceDerivedKey(null)
        state.value = state.value.copy(unlocked = false, wallets = emptyList(), activeWalletId = null)
    }

    override fun currentDerivedKeyCopy(): ByteArray? = derivedKey?.copyOf()

    override suspend fun createMnemonicWallet(name: String?): Pair<WalletProfile, String> = mutate { current ->
        val derived = keyService.createMnemonicWallet()
        ensureNotDuplicate(current, derived.evmAddress, derived.solanaAddress)
        val profile = WalletProfile(
            id = UUID.randomUUID().toString(),
            name = normalizedUniqueName(current, name),
            kind = WalletKind.MNEMONIC,
            evmAddress = derived.evmAddress,
            solanaAddress = derived.solanaAddress,
            backedUp = false,
            createdAtMillis = System.currentTimeMillis()
        )
        val updated = current.copy(
            wallets = current.wallets + StoredWallet(profile, WalletSecret(mnemonic = derived.mnemonic)),
            activeWalletId = profile.id
        )
        updated to (profile to derived.mnemonic)
    }

    override suspend fun importMnemonicWallet(mnemonic: String, name: String?): WalletProfile = mutate { current ->
        val derived = keyService.deriveMnemonicWallet(mnemonic)
        ensureNotDuplicate(current, derived.evmAddress, derived.solanaAddress)
        val profile = WalletProfile(
            id = UUID.randomUUID().toString(),
            name = normalizedUniqueName(current, name),
            kind = WalletKind.MNEMONIC,
            evmAddress = derived.evmAddress,
            solanaAddress = derived.solanaAddress,
            backedUp = true,
            createdAtMillis = System.currentTimeMillis()
        )
        current.copy(
            wallets = current.wallets + StoredWallet(profile, WalletSecret(mnemonic = derived.mnemonic)),
            activeWalletId = profile.id
        ) to profile
    }

    override suspend fun importPrivateKeyWallet(
        privateKey: String,
        type: WalletPrivateKeyType,
        name: String?
    ): WalletProfile = mutate { current ->
        val imported = keyService.importPrivateKey(privateKey, type)
        val evmAddress = imported.address.takeIf { type == WalletPrivateKeyType.EVM }
        val solanaAddress = imported.address.takeIf { type == WalletPrivateKeyType.SOLANA }
        ensureNotDuplicate(current, evmAddress, solanaAddress)
        val profile = WalletProfile(
            id = UUID.randomUUID().toString(),
            name = normalizedUniqueName(current, name),
            kind = WalletKind.PRIVATE_KEY,
            evmAddress = evmAddress,
            solanaAddress = solanaAddress,
            privateKeyType = type,
            backedUp = true,
            createdAtMillis = System.currentTimeMillis()
        )
        current.copy(
            wallets = current.wallets + StoredWallet(profile, WalletSecret(privateKeyHex = imported.normalizedHex)),
            activeWalletId = profile.id
        ) to profile
    }

    override suspend fun renameWallet(walletId: String, name: String) = mutate<Unit> { current ->
        val normalized = name.trim()
        require(normalized.isNotBlank()) { "钱包名称不能为空。" }
        require(current.wallets.none { it.profile.id != walletId && it.profile.name.equals(normalized, true) }) {
            "钱包名称已经存在。"
        }
        current.copy(wallets = current.wallets.map {
            if (it.profile.id == walletId) it.copy(profile = it.profile.copy(name = normalized)) else it
        }) to Unit
    }

    override suspend fun deleteWallet(walletId: String) = mutate<Unit> { current ->
        require(current.wallets.any { it.profile.id == walletId }) { "钱包不存在。" }
        val remaining = current.wallets.filterNot { it.profile.id == walletId }
        current.copy(
            wallets = remaining,
            activeWalletId = if (current.activeWalletId == walletId) remaining.firstOrNull()?.profile?.id else current.activeWalletId
        ) to Unit
    }

    override suspend fun selectWallet(walletId: String) = mutate<Unit> { current ->
        require(current.wallets.any { it.profile.id == walletId }) { "钱包不存在。" }
        current.copy(activeWalletId = walletId) to Unit
    }

    override suspend fun markBackedUp(walletId: String) = mutate<Unit> { current ->
        current.copy(wallets = current.wallets.map {
            if (it.profile.id == walletId) it.copy(profile = it.profile.copy(backedUp = true)) else it
        }) to Unit
    }

    override suspend fun revealSecret(walletId: String): String = withContext(Dispatchers.IO) {
        val wallet = requirePayload().wallets.firstOrNull { it.profile.id == walletId } ?: error("钱包不存在。")
        wallet.secret.mnemonic ?: wallet.secret.privateKeyHex ?: error("钱包密钥不存在。")
    }

    override suspend fun privateKeyFor(walletId: String, network: WalletNetwork): ByteArray = withContext(Dispatchers.IO) {
        val wallet = requirePayload().wallets.firstOrNull { it.profile.id == walletId } ?: error("钱包不存在。")
        require(wallet.profile.supports(network)) { "该钱包不支持 ${network.displayName}。" }
        wallet.secret.mnemonic?.let { keyService.privateKeyFromMnemonic(it, network) }
            ?: wallet.secret.privateKeyHex?.let(keyService::decodeStoredPrivateKey)
            ?: error("钱包密钥不存在。")
    }

    override suspend fun resetVault() = withContext(Dispatchers.IO) {
        requirePreferences().edit().remove(KEY_ENVELOPE).apply()
        lock()
        state.value = WalletVaultState(initialized = false, secureStorageAvailable = securePreferences.available)
    }

    private suspend fun <T> mutate(block: (VaultPayload) -> Pair<VaultPayload, T>): T = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val current = requirePayload()
            val (updated, result) = block(current)
            payload = updated
            persist(updated)
            publish(updated, initialized = true)
            result
        }
    }

    private fun publish(payload: VaultPayload, initialized: Boolean) {
        state.value = WalletVaultState(
            initialized = initialized,
            secureStorageAvailable = securePreferences.available,
            unlocked = true,
            wallets = payload.wallets.map(StoredWallet::profile),
            activeWalletId = payload.activeWalletId
        )
    }

    private fun normalizedUniqueName(payload: VaultPayload, requested: String?): String {
        requested?.trim()?.takeIf(String::isNotBlank)?.let { candidate ->
            require(payload.wallets.none { it.profile.name.equals(candidate, true) }) { "钱包名称已经存在。" }
            return candidate
        }
        var index = payload.wallets.size + 1
        while (payload.wallets.any { it.profile.name.equals("钱包 $index", true) }) index++
        return "钱包 $index"
    }

    private fun ensureNotDuplicate(
        payload: VaultPayload,
        evmAddress: String?,
        solanaAddress: String?
    ) {
        val duplicate = payload.wallets.any { stored ->
            val profile = stored.profile
            (evmAddress != null && profile.evmAddress.equals(evmAddress, true)) ||
                (solanaAddress != null && profile.solanaAddress == solanaAddress)
        }
        require(!duplicate) { "该钱包已经导入。" }
    }

    private fun requirePayload(): VaultPayload = payload ?: error("钱包保险库尚未解锁。")
    private fun requirePreferences(): SharedPreferences = securePreferences.preferences
        ?: error("当前设备的安全存储不可用，已拒绝保存钱包。")

    private fun persist(payload: VaultPayload, newSalt: ByteArray? = null) {
        val currentKey = derivedKey ?: error("钱包保险库尚未解锁。")
        val previous = readEnvelope()
        val salt = newSalt ?: previous?.salt?.decodeBase64() ?: error("钱包盐值缺失。")
        val iv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(currentKey, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
        val envelope = VaultEnvelope(salt.encodeBase64(), iv.encodeBase64(), ciphertext.encodeBase64())
        requirePreferences().edit().putString(KEY_ENVELOPE, json.encodeToString(envelope)).commit()
        if (newSalt == null) salt.fill(0)
        iv.fill(0)
        ciphertext.fill(0)
    }

    private fun readEnvelope(): VaultEnvelope? = requirePreferences().getString(KEY_ENVELOPE, null)?.let {
        runCatching { json.decodeFromString<VaultEnvelope>(it) }.getOrNull()
    }

    private fun decryptPayload(envelope: VaultEnvelope, key: ByteArray): VaultPayload {
        val iv = envelope.iv.decodeBase64()
        val ciphertext = envelope.ciphertext.decodeBase64()
        return try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ciphertext)
            try {
                json.decodeFromString<VaultPayload>(plaintext.toString(Charsets.UTF_8))
            } finally {
                plaintext.fill(0)
            }
        } finally {
            iv.fill(0)
            ciphertext.fill(0)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun validatePassword(password: CharArray) {
        require(WalletPasswordPolicy.isStrong(password)) {
            "钱包密码至少 10 位，且须包含大写字母、小写字母、数字、符号中的至少 3 类，不得包含空格。"
        }
    }

    private fun replaceDerivedKey(newKey: ByteArray?) {
        derivedKey?.fill(0)
        derivedKey = newKey
    }

    private fun createSecurePreferences(context: Context): SecurePreferencesResult = try {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
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
    } catch (error: Throwable) {
        Log.e("WalletVault", "Unable to create encrypted wallet storage", error)
        SecurePreferencesResult(false, null)
    }

    @Serializable
    private data class VaultEnvelope(val salt: String, val iv: String, val ciphertext: String)

    @Serializable
    private data class VaultPayload(
        val wallets: List<StoredWallet> = emptyList(),
        val activeWalletId: String? = null
    )

    @Serializable
    private data class StoredWallet(val profile: WalletProfile, val secret: WalletSecret)

    @Serializable
    private data class WalletSecret(val mnemonic: String? = null, val privateKeyHex: String? = null)

    private data class SecurePreferencesResult(val available: Boolean, val preferences: SharedPreferences?)

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        internal const val PREFS_NAME = "self_custody_wallet_vault_secure"
        private const val KEY_ENVELOPE = "vault_envelope"
        private const val SALT_BYTES = 32
        private const val GCM_IV_BYTES = 12
        private const val PBKDF2_ITERATIONS = 210_000
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
