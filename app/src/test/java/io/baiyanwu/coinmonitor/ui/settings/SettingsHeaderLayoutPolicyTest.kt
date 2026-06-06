package io.baiyanwu.coinmonitor.ui.settings

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHeaderLayoutPolicyTest {
    @Test
    fun `third party api settings should use fixed scaffold top bar`() {
        val source = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/ThirdPartyApiSettingsRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/settings/ThirdPartyApiSettingsRoute.kt"
        )

        assertTrue(source.contains("Scaffold("))
        assertTrue(source.contains("topBar = {"))
        assertTrue(source.contains("ThirdPartyApiTopBar(onBack = onBack)"))
        assertTrue(source.contains(".padding(innerPadding)"))
        assertTrue(source.contains("CenterAlignedTopAppBar("))
        assertFalse(source.contains("statusBarsPadding"))
    }

    @Test
    fun `overlay settings should keep top bar outside lazy column`() {
        val source = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/OverlaySettingsRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/settings/OverlaySettingsRoute.kt"
        )

        assertTrue(source.contains("Scaffold("))
        assertTrue(source.contains("topBar = {"))
        assertTrue(source.contains("OverlaySettingsTopBar(onBack = onBack)"))
        assertTrue(source.contains(".padding(innerPadding)"))
        assertTrue(source.contains("CenterAlignedTopAppBar("))
        assertFalse(source.contains("statusBarsPadding"))
        assertFalse(source.contains("item {\n            OverlaySettingsTopBar(onBack = onBack)"))
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
