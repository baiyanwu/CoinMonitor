package io.baiyanwu.coinmonitor.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.baiyanwu.coinmonitor.data.local.dao.AiChatDao
import io.baiyanwu.coinmonitor.data.local.dao.WatchItemDao

@Database(
    entities = [
        WatchItemEntity::class,
        OverlaySettingsEntity::class,
        AiChatSessionEntity::class,
        AiChatMessageEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class CoinMonitorDatabase : RoomDatabase() {
    abstract fun watchItemDao(): WatchItemDao
    abstract fun aiChatDao(): AiChatDao

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
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_chat_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT,
                        itemId TEXT,
                        symbol TEXT,
                        sourceTitle TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_chat_messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        sessionId TEXT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestampMillis INTEGER NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES ai_chat_sessions(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ai_chat_messages_sessionId ON ai_chat_messages(sessionId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_ai_chat_messages_timestampMillis ON ai_chat_messages(timestampMillis)"
                )
            }
        }

        fun migration7To8(
            context: Context,
            migrateOverlaySettings: (Context, SupportSQLiteDatabase) -> Unit
        ): Migration = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 与 watch_items 的 v8 结构升级合并执行，不再创建并行的 8→9 迁移。
                migrateOverlaySettings(context, database)
                database.execSQL("ALTER TABLE watch_items ADD COLUMN poolAddress TEXT")
                database.execSQL("ALTER TABLE watch_items ADD COLUMN poolTokenSide TEXT")
                database.execSQL(
                    "UPDATE watch_items SET source = 'ONCHAIN' WHERE marketType = 'ONCHAIN_TOKEN'"
                )
                database.execSQL(
                    """
                    UPDATE watch_items
                    SET lastPrice = NULL,
                        previousPrice = NULL,
                        liveTrend = 'NEUTRAL',
                        change24hPercent = NULL,
                        lastUpdatedAt = NULL
                    WHERE marketType = 'ONCHAIN_TOKEN'
                    """.trimIndent()
                )
            }
        }

    }
}
