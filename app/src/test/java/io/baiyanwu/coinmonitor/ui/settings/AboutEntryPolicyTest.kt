package io.baiyanwu.coinmonitor.ui.settings

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutEntryPolicyTest {
    @Test
    fun `settings should expose about as a secondary screen`() {
        val settingsSource = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/SettingsRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/settings/SettingsRoute.kt"
        )
        val manifestSource = readSource(
            rootRelativePath = "app/src/main/AndroidManifest.xml",
            moduleRelativePath = "src/main/AndroidManifest.xml"
        )

        assertTrue(settingsSource.contains("onNavigateAbout"))
        assertTrue(settingsSource.contains("R.string.about_settings_title"))
        assertTrue(manifestSource.contains("io.baiyanwu.coinmonitor.ui.settings.AboutActivity"))
    }

    @Test
    fun `about page should use build version and canonical project links`() {
        val source = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/AboutRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/settings/AboutRoute.kt"
        )

        assertTrue(source.contains("BuildConfig.VERSION_NAME"))
        assertTrue(source.contains("BuildConfig.VERSION_CODE"))
        assertTrue(source.contains("https://github.com/baiyanwu/CoinMonitor"))
        assertTrue(source.contains("\$PROJECT_URL/blob/main/LICENSE"))
        assertTrue(source.contains("\$PROJECT_URL/issues"))
    }

    private fun readSource(rootRelativePath: String, moduleRelativePath: String): String {
        val cwd = Paths.get("").toAbsolutePath()
        val path = listOf(
            cwd.resolve(rootRelativePath),
            cwd.resolve(moduleRelativePath),
            cwd.parent?.resolve(rootRelativePath)
        ).filterNotNull().firstOrNull(Files::exists)

        requireNotNull(path) {
            "Unable to locate source file from $cwd: $rootRelativePath"
        }
        return path.toFile().readText()
    }
}
