package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import io.baiyanwu.coinmonitor.domain.model.WalletCustomToken
import io.baiyanwu.coinmonitor.domain.repository.WalletCustomTokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DefaultWalletCustomTokenRepository(context: Context) : WalletCustomTokenRepository {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val mutex = Mutex()

    override fun get(walletId: String): List<WalletCustomToken> = read().filter { it.walletId == walletId }

    override suspend fun add(token: WalletCustomToken) = update { current ->
        current.filterNot {
            it.walletId == token.walletId &&
                it.networkId == token.networkId &&
                it.contractAddress.equals(token.contractAddress, ignoreCase = true)
        } + token.copy(contractAddress = token.contractAddress.lowercase())
    }

    override suspend fun remove(walletId: String, networkId: String, contractAddress: String) = update { current ->
        current.filterNot {
            it.walletId == walletId && it.networkId == networkId &&
                it.contractAddress.equals(contractAddress, ignoreCase = true)
        }
    }

    override suspend fun clear(walletIds: Collection<String>) = update { current ->
        current.filterNot { it.walletId in walletIds }
    }

    private suspend fun update(transform: (List<WalletCustomToken>) -> List<WalletCustomToken>) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val encoded = json.encodeToString(transform(read()))
                check(preferences.edit().putString(KEY_TOKENS, encoded).commit()) {
                    "自定义 Token 保存失败。"
                }
            }
        }
    }

    private fun read(): List<WalletCustomToken> = preferences.getString(KEY_TOKENS, null)?.let {
        runCatching { json.decodeFromString<List<WalletCustomToken>>(it) }.getOrDefault(emptyList())
    }.orEmpty()

    private companion object {
        const val PREFS_NAME = "wallet_custom_tokens"
        const val KEY_TOKENS = "tokens"
    }
}
