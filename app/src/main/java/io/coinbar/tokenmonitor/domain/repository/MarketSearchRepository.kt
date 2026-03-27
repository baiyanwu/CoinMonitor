package io.coinbar.tokenmonitor.domain.repository

import io.coinbar.tokenmonitor.domain.model.WatchItem

interface MarketSearchRepository {
    suspend fun search(keyword: String): List<WatchItem>
}

