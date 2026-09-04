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
        val initialSearchMode = resolveInitialSearchMode(intent.getStringExtra(EXTRA_SEARCH_MODE))
        setCoinMonitorContent { container ->
            SearchRoute(
                container = container,
                entryMode = entryMode,
                initialSearchMode = initialSearchMode,
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
        private const val EXTRA_SEARCH_MODE = "extra_search_mode"

        fun start(
            activity: Activity,
            initialSearchMode: SearchMode = SearchMode.EXCHANGE
        ) {
            DetailPageTransitions.start(
                activity = activity,
                intent = Intent(activity, SearchActivity::class.java)
                    .putExtra(EXTRA_ENTRY_MODE, SearchEntryMode.HOME.name)
                    .putExtra(EXTRA_SEARCH_MODE, initialSearchMode.name)
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

internal fun resolveInitialSearchMode(rawMode: String?): SearchMode {
    return SearchMode.entries.firstOrNull { it.name == rawMode } ?: SearchMode.EXCHANGE
}
