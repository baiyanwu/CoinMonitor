package io.baiyanwu.coinmonitor.ui.search

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.baiyanwu.coinmonitor.ui.CoinMonitorComposeActivity
import io.baiyanwu.coinmonitor.ui.navigation.DetailPageTransitions

class SearchActivity : CoinMonitorComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entryMode = resolveEntryMode()
        setCoinMonitorContent { container ->
            SearchRoute(
                container = container,
                entryMode = entryMode,
                onBack = { finish() },
                onSelectForKline = { itemId ->
                    container.klineSelectionStore.select(itemId)
                    finish()
                }
            )
        }
    }

    override fun finish() {
        super.finish()
        DetailPageTransitions.finish(this)
    }

    companion object {
        private const val EXTRA_ENTRY_MODE = "extra_entry_mode"

        fun start(activity: Activity) {
            DetailPageTransitions.start(
                activity = activity,
                intent = Intent(activity, SearchActivity::class.java).putExtra(
                    EXTRA_ENTRY_MODE,
                    SearchEntryMode.HOME.name
                )
            )
        }

        fun startForKline(activity: Activity) {
            DetailPageTransitions.start(
                activity = activity,
                intent = Intent(activity, SearchActivity::class.java).putExtra(
                    EXTRA_ENTRY_MODE,
                    SearchEntryMode.KLINE.name
                )
            )
        }
    }

    /**
     * 解析搜索页入口模式。
     */
    private fun resolveEntryMode(): SearchEntryMode {
        val rawMode = intent.getStringExtra(EXTRA_ENTRY_MODE)
        return SearchEntryMode.entries.firstOrNull { it.name == rawMode } ?: SearchEntryMode.HOME
    }
}
