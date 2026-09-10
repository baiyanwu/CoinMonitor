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
import io.baiyanwu.coinmonitor.domain.model.ArrangedEdgeDisplayMode
import io.baiyanwu.coinmonitor.domain.model.ArrangedOverlaySettings
import io.baiyanwu.coinmonitor.domain.model.MarqueeOverlaySettings
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
    fun migration8To9_preservesPreviousOverlayOrderAndLeavesUnselectedOrderEmpty() {
        migrationHelper.createDatabase(OVERLAY_ORDER_DATABASE, 8).apply {
            insertVersion8WatchItem(
                id = "pinned-a",
                source = "BINANCE",
                marketType = "CEX_SPOT",
                overlaySelected = true,
                addedAt = 400L,
                homePinned = true,
                homeOrder = 900L,
                homePinnedOrder = 10L
            )
            insertVersion8WatchItem(
                id = "pinned-b",
                source = "ONCHAIN",
                marketType = "ONCHAIN_TOKEN",
                overlaySelected = true,
                addedAt = 100L,
                homePinned = true,
                homeOrder = 100L,
                homePinnedOrder = 20L
            )
            insertVersion8WatchItem(
                id = "normal-onchain",
                source = "ONCHAIN",
                marketType = "ONCHAIN_TOKEN",
                overlaySelected = true,
                addedAt = 300L,
                homePinned = false,
                homeOrder = 1L,
                homePinnedOrder = null
            )
            insertVersion8WatchItem(
                id = "normal-exchange",
                source = "OKX",
                marketType = "CEX_SPOT",
                overlaySelected = true,
                addedAt = 200L,
                homePinned = false,
                homeOrder = 9L,
                homePinnedOrder = null
            )
            insertVersion8WatchItem(
                id = "not-selected",
                source = "BINANCE",
                marketType = "CEX_SPOT",
                overlaySelected = false,
                addedAt = 50L,
                homePinned = true,
                homeOrder = 1L,
                homePinnedOrder = 1L
            )
            close()
        }

        val database = migrationHelper.runMigrationsAndValidate(
            OVERLAY_ORDER_DATABASE,
            9,
            true,
            CoinMonitorDatabase.MIGRATION_8_9
        )

        database.query(
            """
            SELECT id, overlayOrder
            FROM watch_items
            WHERE overlaySelected = 1
            ORDER BY overlayOrder ASC
            """.trimIndent()
        ).use { cursor ->
            val actual = mutableListOf<Pair<String, Long>>()
            while (cursor.moveToNext()) {
                actual += cursor.getString(0) to cursor.getLong(1)
            }
            assertEquals(
                listOf(
                    "pinned-a" to 1024L,
                    "pinned-b" to 2048L,
                    "normal-onchain" to 3072L,
                    "normal-exchange" to 4096L
                ),
                actual
            )
        }
        database.query(
            "SELECT overlayOrder FROM watch_items WHERE id = 'not-selected'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        database.close()
    }

    @Test
    fun migration9To10_addsNullableMarketCap() {
        migrationHelper.createDatabase(MARKET_CAP_DATABASE, 9).close()

        val database = migrationHelper.runMigrationsAndValidate(
            MARKET_CAP_DATABASE,
            10,
            true,
            CoinMonitorDatabase.MIGRATION_9_10
        )

        database.query("PRAGMA table_info(watch_items)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val notNullColumn = cursor.getColumnIndexOrThrow("notnull")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == "marketCap") {
                    found = true
                    assertEquals(0, cursor.getInt(notNullColumn))
                }
            }
            assertTrue(found)
        }
        database.close()
    }

    @Test
    fun migration7To10_runsTheCompleteUpgradePath() {
        val testContext = isolatedMigrationContext(FULL_UPGRADE_DATABASE)
        migrationHelper.createDatabase(FULL_UPGRADE_DATABASE, 7).apply {
            insertLegacyOnchainWatchItem()
            close()
        }

        val database = migrationHelper.runMigrationsAndValidate(
            FULL_UPGRADE_DATABASE,
            10,
            true,
            migration7To8(testContext),
            CoinMonitorDatabase.MIGRATION_8_9,
            CoinMonitorDatabase.MIGRATION_9_10
        )

        database.query(
            "SELECT source, overlayOrder, marketCap FROM watch_items WHERE id = ?",
            arrayOf(LEGACY_ONCHAIN_ID)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ONCHAIN", cursor.getString(0))
            assertEquals(1024L, cursor.getLong(1))
            assertTrue(cursor.isNull(2))
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

    private fun SupportSQLiteDatabase.insertVersion8WatchItem(
        id: String,
        source: String,
        marketType: String,
        overlaySelected: Boolean,
        addedAt: Long,
        homePinned: Boolean,
        homeOrder: Long,
        homePinnedOrder: Long?
    ) {
        execSQL(
            """
            INSERT INTO watch_items (
                id, symbol, name, source, marketType, chainFamily, chainIndex,
                tokenAddress, poolAddress, poolTokenSide, iconUrl, overlaySelected,
                addedAt, homePinned, homeOrder, homePinnedOrder, lastPrice,
                previousPrice, liveTrend, change24hPercent, lastUpdatedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                id,
                id.uppercase(),
                id,
                source,
                marketType,
                null,
                null,
                null,
                null,
                null,
                null,
                if (overlaySelected) 1 else 0,
                addedAt,
                if (homePinned) 1 else 0,
                homeOrder,
                homePinnedOrder,
                null,
                null,
                "NEUTRAL",
                null,
                null
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
        assertEquals(0.64f, settings.arranged.opacity)
        assertEquals(7, settings.arranged.maxItems)
        assertEquals(OverlayLeadingDisplayMode.PAIR_NAME, settings.arranged.leadingDisplayMode)
        assertEquals(1.25f, settings.arranged.fontScale)
        assertTrue(settings.arranged.snapToEdge)
        assertEquals(123, settings.arranged.windowX)
        assertEquals(456, settings.arranged.windowY)
        assertEquals(ArrangedEdgeDisplayMode.DOCKED, settings.arranged.edgeDisplayMode)
        assertEquals(
            ArrangedOverlaySettings.DEFAULT_EDGE_TAB_OPACITY,
            settings.arranged.edgeTabOpacity
        )
        assertEquals(
            ArrangedOverlaySettings.DEFAULT_EDGE_AUTO_COLLAPSE_SECONDS,
            settings.arranged.edgeAutoCollapseSeconds
        )
        assertEquals(MarqueeOverlaySettings.DEFAULT_OPACITY, settings.marquee.opacity)
        assertEquals(456, settings.marquee.windowY)
    }

    private fun clearMigrationState() {
        listOf(
            WATCH_ITEM_DATABASE,
            OVERLAY_ORDER_DATABASE,
            MARKET_CAP_DATABASE,
            FULL_UPGRADE_DATABASE,
            PREOPEN_DATABASE,
            ROOM_FALLBACK_DATABASE
        )
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
        const val OVERLAY_ORDER_DATABASE = "coin-monitor-migration-overlay-order"
        const val MARKET_CAP_DATABASE = "coin-monitor-migration-market-cap"
        const val FULL_UPGRADE_DATABASE = "coin-monitor-migration-full-upgrade"
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
