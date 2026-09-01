package io.baiyanwu.coinmonitor.data.local

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.baiyanwu.coinmonitor.data.repository.OverlayPreferencesContract
import io.baiyanwu.coinmonitor.data.repository.createOverlayPreferencesDataStore
import io.baiyanwu.coinmonitor.data.repository.migrateLegacyOverlaySettings
import io.baiyanwu.coinmonitor.data.repository.migrateLegacyOverlaySettingsBeforeRoomOpen
import io.baiyanwu.coinmonitor.data.repository.toOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.OverlayEdgeDisplayMode
import io.baiyanwu.coinmonitor.domain.model.OverlayLeadingDisplayMode
import io.baiyanwu.coinmonitor.domain.model.OverlaySettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoinMonitorDatabaseMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation,
        CoinMonitorDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Before
    fun setUp() {
        clearMigrationState()
    }

    @After
    fun tearDown() {
        clearMigrationState()
    }

    @Test
    fun migration7To8_preservesOnchainIdentityAndClearsStaleQuoteState() {
        val testContext = isolatedMigrationContext(WATCH_ITEM_DATABASE)
        migrationHelper.createDatabase(WATCH_ITEM_DATABASE, 7).apply {
            insertLegacyOnchainWatchItem()
            close()
        }

        val database = migrationHelper.runMigrationsAndValidate(
            WATCH_ITEM_DATABASE,
            8,
            true,
            migration7To8(testContext)
        )

        database.query(
            """
            SELECT id, source, marketType, poolAddress, poolTokenSide,
                   lastPrice, previousPrice, liveTrend, change24hPercent, lastUpdatedAt
            FROM watch_items
            WHERE id = ?
            """.trimIndent(),
            arrayOf(LEGACY_ONCHAIN_ID)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(LEGACY_ONCHAIN_ID, cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("ONCHAIN", cursor.getString(cursor.getColumnIndexOrThrow("source")))
            assertEquals(
                "ONCHAIN_TOKEN",
                cursor.getString(cursor.getColumnIndexOrThrow("marketType"))
            )
            assertNull(cursor.nullableString("poolAddress"))
            assertNull(cursor.nullableString("poolTokenSide"))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("lastPrice")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("previousPrice")))
            assertEquals("NEUTRAL", cursor.getString(cursor.getColumnIndexOrThrow("liveTrend")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("change24hPercent")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("lastUpdatedAt")))
        }
        database.close()
    }

    @Test
    fun startupMigration_exportsRoomSettingsAndImportsThemIntoDataStore() = runBlocking {
        val testContext = isolatedMigrationContext(PREOPEN_DATABASE)
        migrationHelper.createDatabase(PREOPEN_DATABASE, 7).apply {
            insertLegacyOverlaySettings()
            close()
        }

        migrateLegacyOverlaySettingsBeforeRoomOpen(testContext)

        val database = migrationHelper.runMigrationsAndValidate(
            PREOPEN_DATABASE,
            8,
            true,
            migration7To8(testContext)
        )
        assertOverlayTableIsEmpty(database)
        database.close()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val settings = createOverlayPreferencesDataStore(testContext, scope)
                .data
                .first()
                .toOverlaySettings()

            assertMigratedOverlaySettings(settings)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun roomMigration_exportsOverlaySettingsWhenPreOpenStepWasSkipped() {
        val testContext = isolatedMigrationContext(ROOM_FALLBACK_DATABASE)
        migrationHelper.createDatabase(ROOM_FALLBACK_DATABASE, 7).apply {
            insertLegacyOverlaySettings()
            close()
        }

        val database = migrationHelper.runMigrationsAndValidate(
            ROOM_FALLBACK_DATABASE,
            8,
            true,
            migration7To8(testContext)
        )
        assertOverlayTableIsEmpty(database)
        database.close()

        val staged = testContext.getSharedPreferences(
            OverlayPreferencesContract.LEGACY_SHARED_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        assertTrue(staged.getBoolean(OverlayPreferencesContract.ENABLED, false))
        assertTrue(staged.getBoolean(OverlayPreferencesContract.LOCKED, false))
        assertEquals(0.64f, staged.getFloat(OverlayPreferencesContract.OPACITY, 0f))
        assertEquals(7, staged.getInt(OverlayPreferencesContract.MAX_ITEMS, 0))
        assertEquals(
            OverlayLeadingDisplayMode.PAIR_NAME.name,
            staged.getString(OverlayPreferencesContract.LEADING_DISPLAY_MODE, null)
        )
        assertEquals(1.25f, staged.getFloat(OverlayPreferencesContract.FONT_SCALE, 0f))
        assertTrue(staged.getBoolean(OverlayPreferencesContract.SNAP_TO_EDGE, false))
        assertEquals(123, staged.getInt(OverlayPreferencesContract.WINDOW_X, 0))
        assertEquals(456, staged.getInt(OverlayPreferencesContract.WINDOW_Y, 0))
    }

    private fun migration7To8(context: Context) = CoinMonitorDatabase.migration7To8(
        context = context,
        migrateOverlaySettings = ::migrateLegacyOverlaySettings
    )

    private fun SupportSQLiteDatabase.insertLegacyOnchainWatchItem() {
        execSQL(
            """
            INSERT INTO watch_items (
                id, symbol, name, source, marketType, chainFamily, chainIndex,
                tokenAddress, iconUrl, overlaySelected, addedAt, homePinned,
                homeOrder, homePinnedOrder, lastPrice, previousPrice, liveTrend,
                change24hPercent, lastUpdatedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                LEGACY_ONCHAIN_ID,
                "TGT",
                "Target Token",
                "OKX",
                "ONCHAIN_TOKEN",
                "EVM",
                "1",
                TOKEN_ADDRESS,
                "https://example.com/token.png",
                1,
                1_700_000_000_000L,
                1,
                99L,
                7L,
                2.5,
                2.0,
                "UP",
                25.0,
                1_700_000_100_000L
            )
        )
    }

    private fun SupportSQLiteDatabase.insertLegacyOverlaySettings() {
        execSQL(
            """
            INSERT INTO overlay_settings (
                id, enabled, locked, opacity, maxItems, leadingDisplayMode,
                fontScale, snapToEdge, windowX, windowY
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                1,
                1,
                1,
                0.64,
                7,
                OverlayLeadingDisplayMode.PAIR_NAME.name,
                1.25,
                1,
                123,
                456
            )
        )
    }

    private fun assertOverlayTableIsEmpty(database: SupportSQLiteDatabase) {
        database.query("SELECT COUNT(*) FROM overlay_settings").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    private fun assertMigratedOverlaySettings(settings: OverlaySettings) {
        assertTrue(settings.enabled)
        assertTrue(settings.locked)
        assertEquals(0.64f, settings.opacity)
        assertEquals(7, settings.maxItems)
        assertEquals(OverlayLeadingDisplayMode.PAIR_NAME, settings.leadingDisplayMode)
        assertEquals(1.25f, settings.fontScale)
        assertTrue(settings.snapToEdge)
        assertEquals(123, settings.windowX)
        assertEquals(456, settings.windowY)
        assertEquals(OverlayEdgeDisplayMode.TICKER, settings.edgeDisplayMode)
        assertEquals(OverlaySettings.DEFAULT_EDGE_TAB_OPACITY, settings.edgeTabOpacity)
        assertEquals(
            OverlaySettings.DEFAULT_EDGE_AUTO_COLLAPSE_SECONDS,
            settings.edgeAutoCollapseSeconds
        )
    }

    private fun clearMigrationState() {
        listOf(WATCH_ITEM_DATABASE, PREOPEN_DATABASE, ROOM_FALLBACK_DATABASE)
            .forEach(context::deleteDatabase)
        listOf(
            OverlayPreferencesContract.LEGACY_SHARED_PREFERENCES_NAME,
            MIGRATION_STATE_PREFERENCES
        ).forEach { name ->
            context.getSharedPreferences(
                TEST_PREFERENCES_PREFIX + name,
                Context.MODE_PRIVATE
            ).edit().clear().commit()
        }
        isolatedFilesDirectory().deleteRecursively()
    }

    private fun isolatedMigrationContext(databaseName: String): Context {
        return IsolatedMigrationContext(
            base = context,
            redirectedDatabase = context.getDatabasePath(databaseName),
            isolatedFilesDirectory = isolatedFilesDirectory()
        )
    }

    private fun isolatedFilesDirectory(): File {
        return File(context.cacheDir, TEST_FILES_DIRECTORY)
    }

    private fun android.database.Cursor.nullableString(columnName: String): String? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getString(index)
    }

    private class IsolatedMigrationContext(
        base: Context,
        private val redirectedDatabase: File,
        private val isolatedFilesDirectory: File
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getDatabasePath(name: String): File {
            return if (name == PRODUCTION_DATABASE_NAME) {
                redirectedDatabase
            } else {
                super.getDatabasePath(name)
            }
        }

        override fun getFilesDir(): File {
            return isolatedFilesDirectory.apply(File::mkdirs)
        }

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            return super.getSharedPreferences(TEST_PREFERENCES_PREFIX + name, mode)
        }
    }

    private companion object {
        const val WATCH_ITEM_DATABASE = "coin-monitor-migration-watch-item"
        const val PREOPEN_DATABASE = "coin-monitor-migration-preopen"
        const val ROOM_FALLBACK_DATABASE = "coin-monitor-migration-room-fallback"
        const val PRODUCTION_DATABASE_NAME = "coin_monitor.db"
        const val MIGRATION_STATE_PREFERENCES = "coin_monitor_migration_state"
        const val TEST_PREFERENCES_PREFIX = "migration_test_"
        const val TEST_FILES_DIRECTORY = "coin-monitor-migration-test-files"
        const val LEGACY_ONCHAIN_ID =
            "okx-onchain:1:0x1111111111111111111111111111111111111111"
        const val TOKEN_ADDRESS = "0x1111111111111111111111111111111111111111"
    }
}
