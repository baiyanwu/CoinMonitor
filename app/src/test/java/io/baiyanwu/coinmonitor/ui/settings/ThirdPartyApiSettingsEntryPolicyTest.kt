package io.baiyanwu.coinmonitor.ui.settings

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThirdPartyApiSettingsEntryPolicyTest {
    @Test
    fun `third party api settings should hide ai settings entry`() {
        val source = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/ThirdPartyApiSettingsRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/settings/ThirdPartyApiSettingsRoute.kt"
        )

        assertTrue(source.contains("private const val SHOW_AI_SETTINGS_ENTRY = false"))
        assertTrue(source.contains("if (SHOW_AI_SETTINGS_ENTRY)"))
    }

    @Test
    fun `ai settings implementation remains in source tree`() {
        val routeSource = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/ThirdPartyApiSettingsRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/settings/ThirdPartyApiSettingsRoute.kt"
        )
        val viewModelSource = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/ThirdPartyApiSettingsViewModel.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/settings/ThirdPartyApiSettingsViewModel.kt"
        )

        assertTrue(routeSource.contains("third_party_api_settings_section_ai"))
        assertTrue(routeSource.contains("third_party_api_settings_enable_ai"))
        assertTrue(routeSource.contains("onSaveAi()"))
        assertTrue(viewModelSource.contains("data class AiSettingsFormState"))
        assertTrue(viewModelSource.contains("fun saveAiConfig()"))
    }

    @Test
    fun `third party api settings list subtitle should not mention ai`() {
        val chineseStrings = readSource(
            rootRelativePath = "app/src/main/res/values/strings.xml",
            moduleRelativePath = "src/main/res/values/strings.xml"
        )
        val englishStrings = readSource(
            rootRelativePath = "app/src/main/res/values-en/strings.xml",
            moduleRelativePath = "src/main/res/values-en/strings.xml"
        )

        val chineseSubtitle = extractStringValue(chineseStrings, "settings_third_party_api_subtitle")
        val englishSubtitle = extractStringValue(englishStrings, "settings_third_party_api_subtitle")

        val aiLabel = Regex("""\bAI\b""", RegexOption.IGNORE_CASE)
        assertFalse(aiLabel.containsMatchIn(chineseSubtitle))
        assertFalse(aiLabel.containsMatchIn(englishSubtitle))
        assertTrue(chineseSubtitle.contains("免费链上行情"))
        assertTrue(englishSubtitle.contains("Free on-chain market data"))
    }

    @Test
    fun `onchain settings expose provider priority and refresh modes`() {
        val routeSource = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/ThirdPartyApiSettingsRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/settings/ThirdPartyApiSettingsRoute.kt"
        )
        val chineseStrings = readSource(
            rootRelativePath = "app/src/main/res/values/strings.xml",
            moduleRelativePath = "src/main/res/values/strings.xml"
        )
        val englishStrings = readSource(
            rootRelativePath = "app/src/main/res/values-en/strings.xml",
            moduleRelativePath = "src/main/res/values-en/strings.xml"
        )

        assertTrue(routeSource.contains("DexPollingIntervalSetting"))
        assertTrue(routeSource.contains("OnchainProviderPrioritySetting"))
        assertTrue(routeSource.contains("OnchainDataProvider.DEX_SCREENER"))
        assertTrue(routeSource.contains("OnchainDataProvider.OKX_DEX"))
        assertTrue(routeSource.contains("SingleChoiceSegmentedButtonRow"))
        assertTrue(routeSource.contains("OnchainRefreshMode.SMART"))
        assertTrue(routeSource.contains("OnchainRefreshMode.FIXED"))
        assertTrue(routeSource.contains("ONCHAIN_FIXED_INTERVAL_OPTIONS_SECONDS"))
        assertTrue(routeSource.contains("FilterChip("))
        assertTrue(routeSource.contains("colors.positive"))
        assertTrue(chineseStrings.contains("DexScreener · OKX DEX · GeckoTerminal"))
        assertTrue(englishStrings.contains("DexScreener · OKX DEX · GeckoTerminal"))
        assertTrue(chineseStrings.contains("third_party_api_settings_provider_priority_title"))
        assertTrue(englishStrings.contains("third_party_api_settings_provider_priority_title"))
        assertTrue(chineseStrings.contains("third_party_api_settings_refresh_status_active"))
        assertTrue(englishStrings.contains("third_party_api_settings_refresh_status_active"))
        assertTrue(chineseStrings.contains("third_party_api_settings_refresh_smart_hint"))
        assertTrue(englishStrings.contains("third_party_api_settings_refresh_smart_hint"))
    }

    @Test
    fun `wallet prompts can open settings at wallet network section`() {
        val routeSource = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/ThirdPartyApiSettingsRoute.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/settings/ThirdPartyApiSettingsRoute.kt"
        )
        val activitySource = readSource(
            rootRelativePath = "app/src/main/java/io/baiyanwu/coinmonitor/ui/settings/ThirdPartyApiSettingsActivity.kt",
            moduleRelativePath = "src/main/java/io/baiyanwu/coinmonitor/ui/settings/ThirdPartyApiSettingsActivity.kt"
        )

        assertTrue(routeSource.contains("ThirdPartyApiSettingsSection.WALLET_NETWORK"))
        assertTrue(routeSource.contains("initialFirstVisibleItemIndex"))
        assertTrue(activitySource.contains("EXTRA_INITIAL_SECTION"))
        assertTrue(activitySource.contains("initialSection.name"))
    }

    private fun extractStringValue(source: String, name: String): String {
        val pattern = Regex("""<string name="$name">([^<]*)</string>""")
        return requireNotNull(pattern.find(source)?.groupValues?.get(1)) {
            "Unable to locate string resource: $name"
        }
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
