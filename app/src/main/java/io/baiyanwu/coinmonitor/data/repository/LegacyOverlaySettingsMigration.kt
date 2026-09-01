package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 将旧 Room 单行表中的悬浮窗配置暂存到 SharedPreferences。
 * DataStore 首次读取时再通过 SharedPreferencesMigration 原子导入并清理暂存文件。
 */
internal fun migrateLegacyOverlaySettingsBeforeRoomOpen(context: Context) {
    if (isLegacyOverlayMigrationComplete(context)) return

    val databaseFile = context.getDatabasePath(COIN_MONITOR_DATABASE_NAME)
    if (!databaseFile.exists()) return

    runCatching {
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        ).use { database ->
            if (!database.hasLegacyOverlaySettingsTable()) {
                markLegacyOverlayMigrationComplete(context)
                return@use
            }
            val snapshot = database.rawQuery(LEGACY_OVERLAY_QUERY, null).use(::readLegacySnapshot)
            completeLegacyOverlayExport(
                context = context,
                snapshot = snapshot,
                deleteLegacyRow = {
                    database.delete(
                        LEGACY_OVERLAY_TABLE,
                        "id = ?",
                        arrayOf(LEGACY_OVERLAY_SETTINGS_ID.toString())
                    )
                }
            )
        }
    }.onFailure { error ->
        Log.w(LOG_TAG, "Unable to stage legacy overlay settings before Room opens", error)
    }
}

/**
 * 作为 Room 7→8 迁移的一部分执行，确保没有走应用预检的升级路径也不会丢旧配置。
 */
internal fun migrateLegacyOverlaySettings(
    context: Context,
    database: SupportSQLiteDatabase
) {
    if (!database.hasLegacyOverlaySettingsTable()) {
        markLegacyOverlayMigrationComplete(context)
        return
    }
    if (isLegacyOverlayMigrationComplete(context)) {
        database.deleteLegacyOverlayRow()
        return
    }

    val snapshot = database.query(LEGACY_OVERLAY_QUERY).use(::readLegacySnapshot)
    completeLegacyOverlayExport(
        context = context,
        snapshot = snapshot,
        deleteLegacyRow = database::deleteLegacyOverlayRow
    )
}

private fun completeLegacyOverlayExport(
    context: Context,
    snapshot: LegacyOverlaySettingsSnapshot?,
    deleteLegacyRow: () -> Unit
) {
    if (snapshot != null && !stageLegacyOverlaySettings(context, snapshot)) {
        error("Failed to stage legacy overlay settings")
    }
    deleteLegacyRow()
    markLegacyOverlayMigrationComplete(context)
}

private fun stageLegacyOverlaySettings(
    context: Context,
    snapshot: LegacyOverlaySettingsSnapshot
): Boolean {
    val editor = context.getSharedPreferences(
        OverlayPreferencesContract.LEGACY_SHARED_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    ).edit()
        .putBoolean(OverlayPreferencesContract.ENABLED, snapshot.enabled)
        .putBoolean(OverlayPreferencesContract.LOCKED, snapshot.locked)
        .putFloat(OverlayPreferencesContract.OPACITY, snapshot.opacity)
        .putInt(OverlayPreferencesContract.MAX_ITEMS, snapshot.maxItems)
        .putString(
            OverlayPreferencesContract.LEADING_DISPLAY_MODE,
            snapshot.leadingDisplayMode
        )
        .putFloat(OverlayPreferencesContract.FONT_SCALE, snapshot.fontScale)
        .putBoolean(OverlayPreferencesContract.SNAP_TO_EDGE, snapshot.snapToEdge)

    snapshot.windowX?.let { editor.putInt(OverlayPreferencesContract.WINDOW_X, it) }
        ?: editor.remove(OverlayPreferencesContract.WINDOW_X)
    snapshot.windowY?.let { editor.putInt(OverlayPreferencesContract.WINDOW_Y, it) }
        ?: editor.remove(OverlayPreferencesContract.WINDOW_Y)
    return editor.commit()
}

private fun readLegacySnapshot(cursor: Cursor): LegacyOverlaySettingsSnapshot? {
    if (!cursor.moveToFirst()) return null
    return LegacyOverlaySettingsSnapshot(
        enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) != 0,
        locked = cursor.getInt(cursor.getColumnIndexOrThrow("locked")) != 0,
        opacity = cursor.getFloat(cursor.getColumnIndexOrThrow("opacity")),
        maxItems = cursor.getInt(cursor.getColumnIndexOrThrow("maxItems")),
        leadingDisplayMode = cursor.getString(
            cursor.getColumnIndexOrThrow("leadingDisplayMode")
        ),
        fontScale = cursor.getFloat(cursor.getColumnIndexOrThrow("fontScale")),
        snapToEdge = cursor.getInt(cursor.getColumnIndexOrThrow("snapToEdge")) != 0,
        windowX = cursor.nullableInt("windowX"),
        windowY = cursor.nullableInt("windowY")
    )
}

private fun Cursor.nullableInt(columnName: String): Int? {
    val index = getColumnIndexOrThrow(columnName)
    return if (isNull(index)) null else getInt(index)
}

private fun SQLiteDatabase.hasLegacyOverlaySettingsTable(): Boolean {
    return rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
        arrayOf(LEGACY_OVERLAY_TABLE)
    ).use(Cursor::moveToFirst)
}

private fun SupportSQLiteDatabase.hasLegacyOverlaySettingsTable(): Boolean {
    return query(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
        arrayOf(LEGACY_OVERLAY_TABLE)
    ).use(Cursor::moveToFirst)
}

private fun SupportSQLiteDatabase.deleteLegacyOverlayRow() {
    execSQL(
        "DELETE FROM $LEGACY_OVERLAY_TABLE WHERE id = ?",
        arrayOf(LEGACY_OVERLAY_SETTINGS_ID)
    )
}

private fun isLegacyOverlayMigrationComplete(context: Context): Boolean {
    return context.getSharedPreferences(MIGRATION_STATE_PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(KEY_LEGACY_OVERLAY_MIGRATION_COMPLETE, false)
}

private fun markLegacyOverlayMigrationComplete(context: Context) {
    val committed = context.getSharedPreferences(
        MIGRATION_STATE_PREFERENCES,
        Context.MODE_PRIVATE
    ).edit()
        .putBoolean(KEY_LEGACY_OVERLAY_MIGRATION_COMPLETE, true)
        .commit()
    check(committed) { "Failed to persist legacy overlay migration state" }
}

private data class LegacyOverlaySettingsSnapshot(
    val enabled: Boolean,
    val locked: Boolean,
    val opacity: Float,
    val maxItems: Int,
    val leadingDisplayMode: String,
    val fontScale: Float,
    val snapToEdge: Boolean,
    val windowX: Int?,
    val windowY: Int?
)

private const val COIN_MONITOR_DATABASE_NAME = "coin_monitor.db"
private const val LEGACY_OVERLAY_TABLE = "overlay_settings"
private const val LEGACY_OVERLAY_SETTINGS_ID = 1
private const val MIGRATION_STATE_PREFERENCES = "coin_monitor_migration_state"
private const val KEY_LEGACY_OVERLAY_MIGRATION_COMPLETE =
    "legacy_overlay_room_to_datastore_complete"
private const val LOG_TAG = "OverlaySettingsMigration"
private const val LEGACY_OVERLAY_QUERY = """
    SELECT enabled, locked, opacity, maxItems, leadingDisplayMode,
           fontScale, snapToEdge, windowX, windowY
    FROM overlay_settings
    WHERE id = 1
    LIMIT 1
"""
