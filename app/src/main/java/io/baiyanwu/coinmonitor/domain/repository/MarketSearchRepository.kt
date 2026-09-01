package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.WatchItem
import kotlinx.coroutines.flow.Flow

interface MarketSearchRepository {
    suspend fun search(keyword: String): List<WatchItem>

    fun searchExchange(keyword: String): Flow<List<WatchItem>>

    suspend fun searchOnchain(keyword: String): List<WatchItem>
}
