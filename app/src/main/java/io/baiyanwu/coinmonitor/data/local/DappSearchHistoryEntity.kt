package io.baiyanwu.coinmonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dapp_search_history")
data class DappSearchHistoryEntity(
    @PrimaryKey val normalizedQuery: String,
    val query: String,
    val searchedAt: Long
)
