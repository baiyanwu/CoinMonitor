package io.baiyanwu.coinmonitor.ui.browser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import io.baiyanwu.coinmonitor.data.repository.DappAddressParser
import io.baiyanwu.coinmonitor.ui.CoinMonitorComposeActivity
import io.baiyanwu.coinmonitor.ui.navigation.DetailPageTransitions
import kotlinx.coroutines.delay

class DappBrowserActivity : CoinMonitorComposeActivity() {
    private var webView: DappWebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUrl = DappAddressParser.normalize(intent.getStringExtra(EXTRA_URL).orEmpty())
            ?: DEFAULT_DAPP_URL
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val browser = webView
                    if (browser?.canGoBack() == true) browser.goBack() else finish()
                }
            }
        )

        setCoinMonitorContent { container ->
            var browser by remember { mutableStateOf<DappWebView?>(null) }
            LaunchedEffect(Unit) {
                // A frame clock tick happens before rendering. Waiting for two ticks plus one short
                // main-loop turn guarantees that the chrome's first frame reaches the screen before
                // WebView's synchronous Chromium initialization can occupy the UI thread.
                repeat(2) { withFrameNanos { } }
                delay(CHROME_COMMIT_GRACE_MS)
                val startedAt = SystemClock.elapsedRealtime()
                Log.i(LOG_TAG, "Browser chrome committed; creating WebView")
                DappWebView(this@DappBrowserActivity).also {
                    webView = it
                    browser = it
                }
                Log.i(
                    LOG_TAG,
                    "WebView created in ${SystemClock.elapsedRealtime() - startedAt} ms"
                )
            }
            DappBrowserRoute(
                container = container,
                webView = browser,
                initialUrl = initialUrl,
                onExit = ::finish
            )
        }
    }

    override fun onDestroy() {
        webView?.release()
        webView = null
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        DetailPageTransitions.finish(this)
    }

    companion object {
        private const val LOG_TAG = "DappBrowserStartup"
        private const val CHROME_COMMIT_GRACE_MS = 80L
        private const val EXTRA_URL = "dapp_url"

        fun start(activity: Activity, url: String) {
            DetailPageTransitions.start(
                activity = activity,
                intent = Intent(activity, DappBrowserActivity::class.java)
                    .putExtra(EXTRA_URL, url)
            )
        }
    }
}
