package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.AiChatMessage
import io.baiyanwu.coinmonitor.domain.model.AiAnalysisOption
import io.baiyanwu.coinmonitor.domain.model.CandleEntry
import io.baiyanwu.coinmonitor.domain.model.KlineIndicator
import io.baiyanwu.coinmonitor.domain.model.KlineIndicatorSettings
import io.baiyanwu.coinmonitor.domain.model.KlineInterval
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import kotlinx.coroutines.flow.Flow

interface AiChatRepository {
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
