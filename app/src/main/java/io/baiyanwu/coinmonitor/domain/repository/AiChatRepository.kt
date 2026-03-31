package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.AiChatMessage
import io.baiyanwu.coinmonitor.domain.model.CandleEntry
import io.baiyanwu.coinmonitor.domain.model.KlineIndicator
import io.baiyanwu.coinmonitor.domain.model.KlineInterval
import io.baiyanwu.coinmonitor.domain.model.WatchItem

interface AiChatRepository {
    suspend fun sendMessage(
        item: WatchItem,
        interval: KlineInterval,
        indicators: Set<KlineIndicator>,
        candles: List<CandleEntry>,
        messages: List<AiChatMessage>
    ): String
}
