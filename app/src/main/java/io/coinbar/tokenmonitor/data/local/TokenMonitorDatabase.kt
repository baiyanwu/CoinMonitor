package io.coinbar.tokenmonitor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import io.coinbar.tokenmonitor.data.local.dao.OverlaySettingsDao
import io.coinbar.tokenmonitor.data.local.dao.WatchItemDao

@Database(
    entities = [WatchItemEntity::class, OverlaySettingsEntity::class],
    version = 4,
    exportSchema = false
)
abstract class TokenMonitorDatabase : RoomDatabase() {
    abstract fun watchItemDao(): WatchItemDao
    abstract fun overlaySettingsDao(): OverlaySettingsDao
}
