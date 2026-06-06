package com.tradingview.lightweightcharts.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature.*
import com.tradingview.lightweightcharts.Logger
import com.tradingview.lightweightcharts.api.delegates.ChartApiDelegate
import com.tradingview.lightweightcharts.runtime.WebMessageChannel
import com.tradingview.lightweightcharts.runtime.controller.WebMessageController
import com.tradingview.lightweightcharts.runtime.messaging.LogLevel
import com.tradingview.lightweightcharts.view.gesture.TouchDelegate
import java.lang.Exception
import java.util.HashMap

@SuppressLint("RequiresFeature")
open class ChartsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    companion object {
        private const val DEFAULT_URL = "file:///android_asset/com/tradingview/lightweightcharts/index.html"
        private val features = listOf(
            CREATE_WEB_MESSAGE_CHANNEL,
            POST_WEB_MESSAGE,
            WEB_MESSAGE_PORT_SET_MESSAGE_CALLBACK,
            WEB_MESSAGE_PORT_POST_MESSAGE
        )
    }

    private val webView = WebSession(context, attrs, defStyleRes)

    open val logLevel = LogLevel.WARNING

    val api by lazy { ChartApiDelegate(webMessageController) }

    private val webMessageController = WebMessageController()

    var state: State = State.Preparing()
        private set(value) {
            field = value
            onStateChanged.forEach { it.invoke(value) }
        }

    private var onStateChanged: MutableList<((State) -> Unit)> = mutableListOf()

    private val notSupportedFeatures: List<String> = checkSupportedFeature()

    private val channel: WebMessageChannel by lazy {
        WebViewCompat.createWebMessageChannel(webView)
            .asList()
            .let { WebMessageChannel(logLevel, it) }
            .apply { initConnection(webView) }
    }

    init {
        webView.onSessionReady {
            state = when {
                notSupportedFeatures.isEmpty() -> {
                    webMessageController.setWebMessageChannel(channel)
                    State.Ready()
                }
                else -> {
                    State.Error(
                        Exception("WebView does not support features: $notSupportedFeatures")
                    )
                }
            }
        }
        addView(webView)
    }

    final override fun addView(child: View?) {
        super.addView(child)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (state is State.Ready) {
            Logger.d("Reattach to window")
        } else {
            state = State.Preparing()
            webView.loadUrl(DEFAULT_URL)
        }
    }

    fun subscribeOnChartStateChange(subscriber: (State) -> Unit) {
        onStateChanged.add(subscriber)
        subscriber.invoke(state)
    }

    fun unsubscribeOnChartStateChange(subscriber: (State) -> Unit) {
        onStateChanged.remove(subscriber)
    }

    fun addTouchDelegate(touchDelegate: TouchDelegate) {
        webView.addTouchDelegate(touchDelegate)
    }

    fun removeTouchDelegate(touchDelegate: TouchDelegate) {
        webView.removeTouchDelegate(touchDelegate)
    }

    private fun checkSupportedFeature(): List<String> {
        return HashMap<String, Boolean>(features.size)
            .apply { features.forEach { this[it] = isFeatureSupported(it) } }
            .filter { !it.value }
            .keys.toList()
    }

    interface State {
        class Ready : State
        class Preparing : State
        class Error(val exception: Exception) : State
    }
}
