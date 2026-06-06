package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.WatchItem

interface MarketSearchRepository {
    suspend fun search(keyword: String): List<WatchItem>

    suspend fun searchExchange(keyword: String): List<WatchItem>

    suspend fun searchOnchain(keyword: String, chainIndex: String): List<WatchItem>
}
