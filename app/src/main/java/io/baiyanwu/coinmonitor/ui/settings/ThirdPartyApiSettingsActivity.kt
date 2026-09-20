package io.baiyanwu.coinmonitor.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.baiyanwu.coinmonitor.ui.CoinMonitorComposeActivity
import io.baiyanwu.coinmonitor.ui.navigation.DetailPageTransitions

class ThirdPartyApiSettingsActivity : CoinMonitorComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialSection = runCatching {
            ThirdPartyApiSettingsSection.valueOf(
                intent.getStringExtra(EXTRA_INITIAL_SECTION).orEmpty()
            )
        }.getOrDefault(ThirdPartyApiSettingsSection.TOP)
        setCoinMonitorContent { container ->
            ThirdPartyApiSettingsRoute(
                container = container,
                initialSection = initialSection,
                onBack = { finish() }
            )
        }
    }

    override fun finish() {
        super.finish()
        DetailPageTransitions.finish(this)
    }

    companion object {
        private const val EXTRA_INITIAL_SECTION = "initial_section"

        fun start(
            activity: Activity,
            initialSection: ThirdPartyApiSettingsSection = ThirdPartyApiSettingsSection.TOP
        ) {
            DetailPageTransitions.start(
                activity = activity,
                intent = Intent(activity, ThirdPartyApiSettingsActivity::class.java)
                    .putExtra(EXTRA_INITIAL_SECTION, initialSection.name)
            )
        }
    }
}
