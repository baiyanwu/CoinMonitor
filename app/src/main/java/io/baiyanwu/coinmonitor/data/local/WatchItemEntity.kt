package io.baiyanwu.coinmonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_items")
data class WatchItemEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val name: String,
    val source: String,
    val overlaySelected: Boolean,
    val addedAt: Long,
    val lastPrice: Double?,
    val previousPrice: Double?,
    val liveTrend: String,
    val change24hPercent: Double?,
    val lastUpdatedAt: Long?
)
