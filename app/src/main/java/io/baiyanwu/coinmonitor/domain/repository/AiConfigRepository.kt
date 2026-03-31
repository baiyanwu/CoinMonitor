package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.OpenAiCompatibleConfig
import kotlinx.coroutines.flow.Flow

interface AiConfigRepository {
    fun observeConfig(): Flow<OpenAiCompatibleConfig>

    fun getConfig(): OpenAiCompatibleConfig

    fun isSecureStorageAvailable(): Boolean

    suspend fun saveConfig(
        enabled: Boolean,
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String
    )

    suspend fun clearConfig()
}
