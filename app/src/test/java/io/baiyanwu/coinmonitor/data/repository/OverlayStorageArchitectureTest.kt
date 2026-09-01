package io.baiyanwu.coinmonitor.data.repository

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStorageArchitectureTest {
    @Test
    fun `overlay settings use DataStore while item selection stays in Room`() {
        val overlayRepository = sourceFile(
            "app/src/main/java/io/baiyanwu/coinmonitor/data/repository/DefaultOverlayRepository.kt",
            "src/main/java/io/baiyanwu/coinmonitor/data/repository/DefaultOverlayRepository.kt"
        ).readText()
        val watchItemDao = sourceFile(
            "app/src/main/java/io/baiyanwu/coinmonitor/data/local/dao/WatchItemDao.kt",
            "src/main/java/io/baiyanwu/coinmonitor/data/local/dao/WatchItemDao.kt"
        ).readText()

        assertTrue(overlayRepository.contains("DataStore<Preferences>"))
        assertFalse(overlayRepository.contains("OverlaySettingsDao"))
        assertFalse(overlayRepository.contains("OverlaySettingsEntity"))
        assertTrue(watchItemDao.contains("updateOverlaySelected"))
    }

    @Test
    fun `overlay export is merged into database migration seven to eight`() {
        val database = sourceFile(
            "app/src/main/java/io/baiyanwu/coinmonitor/data/local/CoinMonitorDatabase.kt",
            "src/main/java/io/baiyanwu/coinmonitor/data/local/CoinMonitorDatabase.kt"
        ).readText()

        assertTrue(database.contains("Migration(7, 8)"))
        assertTrue(database.contains("migrateOverlaySettings(context, database)"))
        assertFalse(database.contains("Migration(8, 9)"))
    }

    private fun sourceFile(vararg candidates: String): File {
        return candidates.asSequence()
            .map(::File)
            .firstOrNull(File::isFile)
            ?: error("Source file not found: ${candidates.joinToString()}")
    }
}
