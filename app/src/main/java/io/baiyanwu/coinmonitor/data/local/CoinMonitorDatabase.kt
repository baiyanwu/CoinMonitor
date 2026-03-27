package io.baiyanwu.coinmonitor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import io.baiyanwu.coinmonitor.data.local.dao.OverlaySettingsDao
import io.baiyanwu.coinmonitor.data.local.dao.WatchItemDao

@Database(
    entities = [WatchItemEntity::class, OverlaySettingsEntity::class],
    version = 4,
    exportSchema = true
)
abstract class CoinMonitorDatabase : RoomDatabase() {
    abstract fun watchItemDao(): WatchItemDao
    abstract fun overlaySettingsDao(): OverlaySettingsDao
}
