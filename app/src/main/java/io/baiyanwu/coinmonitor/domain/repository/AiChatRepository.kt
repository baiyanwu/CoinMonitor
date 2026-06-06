package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.AiChatMessage
import io.baiyanwu.coinmonitor.domain.model.AiAnalysisOption
import io.baiyanwu.coinmonitor.domain.model.AiChatSession
import io.baiyanwu.coinmonitor.domain.model.AiChatSessionSummary
import io.baiyanwu.coinmonitor.domain.model.CandleEntry
import io.baiyanwu.coinmonitor.domain.model.KlineIndicator
import io.baiyanwu.coinmonitor.domain.model.KlineIndicatorSettings
import io.baiyanwu.coinmonitor.domain.model.KlineInterval
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import kotlinx.coroutines.flow.Flow

interface AiChatRepository {
    fun observeSessionSummaries(): Flow<List<AiChatSessionSummary>>
    fun observeMessages(sessionId: String): Flow<List<AiChatMessage>>
    suspend fun getSession(sessionId: String): AiChatSession?
    suspend fun getLatestSession(): AiChatSession?
    suspend fun createSession(item: WatchItem?): AiChatSession
    suspend fun updateSessionContext(sessionId: String, item: WatchItem?)
    suspend fun appendMessage(sessionId: String, message: AiChatMessage)
    suspend fun updateMessageContent(messageId: String, content: String)
    fun streamMessage(
        item: WatchItem,
        interval: KlineInterval,
        indicators: Set<KlineIndicator>,
        indicatorSettings: KlineIndicatorSettings,
        analysisOptions: Set<AiAnalysisOption>,
        candles: List<CandleEntry>,
        messages: List<AiChatMessage>
    ): Flow<String>
}
