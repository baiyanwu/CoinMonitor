package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.OkxApiCredentials
import kotlinx.coroutines.flow.Flow

interface OkxCredentialsRepository {
    fun observeCredentials(): Flow<OkxApiCredentials>

    fun getCredentials(): OkxApiCredentials

    fun isSecureStorageAvailable(): Boolean

    suspend fun saveCredentials(
        enabled: Boolean,
        apiKey: String,
        secretKey: String,
        passphrase: String
    )

    suspend fun clearCredentials()
}
