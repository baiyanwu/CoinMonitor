package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.CandleEntry
import io.baiyanwu.coinmonitor.domain.model.KlineInterval
import io.baiyanwu.coinmonitor.domain.model.WatchItem

interface MarketKlineRepository {
    suspend fun fetchCandles(
        item: WatchItem,
        interval: KlineInterval,
        limit: Int = 240
    ): List<CandleEntry>
}
