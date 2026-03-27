package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.WatchItem

interface MarketSearchRepository {
    suspend fun search(keyword: String): List<WatchItem>
}

