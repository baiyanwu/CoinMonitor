package io.baiyanwu.coinmonitor.data.repository

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStorageArchitectureTest {
    @Test
    fun `overlay settings use DataStore while item selection and order stay in Room`() {
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
        assertTrue(watchItemDao.contains("updateOverlaySelection"))
        assertTrue(watchItemDao.contains("overlayOrder"))
    }

    @Test
    fun `overlay export stays in seven to eight and overlay order uses eight to nine`() {
        val database = sourceFile(
            "app/src/main/java/io/baiyanwu/coinmonitor/data/local/CoinMonitorDatabase.kt",
            "src/main/java/io/baiyanwu/coinmonitor/data/local/CoinMonitorDatabase.kt"
        ).readText()

        assertTrue(database.contains("Migration(7, 8)"))
        assertTrue(database.contains("migrateOverlaySettings(context, database)"))
        assertTrue(database.contains("Migration(8, 9)"))
        assertTrue(database.contains("ADD COLUMN overlayOrder INTEGER"))
    }

    @Test
    fun `home and overlay ordering remain independent`() {
        val overlayOrderManager = sourceFile(
            "app/src/main/java/io/baiyanwu/coinmonitor/data/repository/OverlayOrderManager.kt",
            "src/main/java/io/baiyanwu/coinmonitor/data/repository/OverlayOrderManager.kt"
        ).readText()
        val homeOrderManager = sourceFile(
            "app/src/main/java/io/baiyanwu/coinmonitor/data/repository/WatchlistHomeOrderManager.kt",
            "src/main/java/io/baiyanwu/coinmonitor/data/repository/WatchlistHomeOrderManager.kt"
        ).readText()

        assertFalse(overlayOrderManager.contains("homePinned"))
        assertFalse(overlayOrderManager.contains("homeOrder"))
        assertFalse(overlayOrderManager.contains("marketType"))
        assertFalse(homeOrderManager.contains("overlayOrder"))
    }

    @Test
    fun `overlay pair business lives on its dedicated settings page`() {
        val overlaySettingsRoute = sourceFile(
            "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/OverlaySettingsRoute.kt",
            "src/main/java/io/baiyanwu/coinmonitor/ui/settings/OverlaySettingsRoute.kt"
        ).readText()
        val overlayItemsRoute = sourceFile(
            "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/OverlayItemsSettingsRoute.kt",
            "src/main/java/io/baiyanwu/coinmonitor/ui/settings/OverlayItemsSettingsRoute.kt"
        ).readText()
        val overlaySettingsViewModel = sourceFile(
            "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/OverlaySettingsViewModel.kt",
            "src/main/java/io/baiyanwu/coinmonitor/ui/settings/OverlaySettingsViewModel.kt"
        ).readText()
        val overlayItemsViewModel = sourceFile(
            "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/OverlayItemsSettingsViewModel.kt",
            "src/main/java/io/baiyanwu/coinmonitor/ui/settings/OverlayItemsSettingsViewModel.kt"
        ).readText()
        val manifest = sourceFile(
            "app/src/main/AndroidManifest.xml",
            "src/main/AndroidManifest.xml"
        ).readText()

        assertTrue(overlaySettingsRoute.contains("OverlayItemsNavigationCard"))
        assertFalse(overlaySettingsRoute.contains("OverlayItemSelectionSection"))
        assertTrue(overlayItemsRoute.contains("OverlayItemSelectionSection"))
        assertFalse(overlaySettingsViewModel.contains("observeOverlayItems"))
        assertFalse(overlayItemsViewModel.contains("observeOverlayItems()"))
        assertTrue(overlayItemsViewModel.contains("WatchItem::overlaySelected"))
        assertTrue(manifest.contains("OverlayItemsSettingsActivity"))
    }

    private fun sourceFile(vararg candidates: String): File {
        return candidates.asSequence()
            .map(::File)
            .firstOrNull(File::isFile)
            ?: error("Source file not found: ${candidates.joinToString()}")
    }
}
