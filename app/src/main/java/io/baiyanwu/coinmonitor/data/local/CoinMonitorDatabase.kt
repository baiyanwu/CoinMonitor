package io.baiyanwu.coinmonitor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.baiyanwu.coinmonitor.data.local.dao.OverlaySettingsDao
import io.baiyanwu.coinmonitor.data.local.dao.WatchItemDao

@Database(
    entities = [WatchItemEntity::class, OverlaySettingsEntity::class],
    version = 7,
    exportSchema = true
)
abstract class CoinMonitorDatabase : RoomDatabase() {
    abstract fun watchItemDao(): WatchItemDao
    abstract fun overlaySettingsDao(): OverlaySettingsDao

    companion object {
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE overlay_settings ADD COLUMN fontScale REAL NOT NULL DEFAULT 1.0"
                )
                database.execSQL(
                    "ALTER TABLE overlay_settings ADD COLUMN snapToEdge INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE watch_items ADD COLUMN marketType TEXT NOT NULL DEFAULT 'CEX_SPOT'"
                )
                database.execSQL(
                    "ALTER TABLE watch_items ADD COLUMN chainFamily TEXT"
                )
                database.execSQL(
                    "ALTER TABLE watch_items ADD COLUMN chainIndex TEXT"
                )
                database.execSQL(
                    "ALTER TABLE watch_items ADD COLUMN tokenAddress TEXT"
                )
                database.execSQL(
                    "ALTER TABLE watch_items ADD COLUMN iconUrl TEXT"
                )
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE watch_items ADD COLUMN homePinned INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE watch_items ADD COLUMN homeOrder INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE watch_items ADD COLUMN homePinnedOrder INTEGER"
                )
                database.execSQL(
                    "UPDATE watch_items SET homeOrder = addedAt"
                )
            }
        }
    }
}
