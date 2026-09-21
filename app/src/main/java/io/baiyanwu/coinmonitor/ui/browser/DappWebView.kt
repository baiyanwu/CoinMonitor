package io.baiyanwu.coinmonitor.ui.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.AttributeSet
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.baiyanwu.coinmonitor.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.util.Locale

const val DEFAULT_DAPP_URL = "https://app.uniswap.org/"
private const val BRIDGE_NAME = "_coinmonitor_"
private const val LOG_TAG = "DappBrowser"

/**
 * Browser surface used by the DApp tab.
 *
 * WebView ownership, security settings, provider injection and bridge cleanup deliberately live
 * here instead of in the Compose route. The route can add browser chrome and network controls
 * without rebuilding the provider transport.
 */
@SuppressLint("SetJavaScriptEnabled")
class DappWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {
    private var onProgressChanged: (Int) -> Unit = {}
    private var onProviderAvailabilityChanged: (Boolean) -> Unit = {}
    private var onBridgeMessage: (String, String) -> Unit = { _, _ -> }
    private var onUrlChanged: (String) -> Unit = {}
    private var bridgeInstalled = false
    private var initialAddress = DEFAULT_DAPP_URL

    var currentProgress: Int = 0
        private set

    val currentUrl: String
        get() = url ?: initialAddress

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        setBackgroundColor(android.graphics.Color.WHITE)
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            useWideViewPort = true
            loadWithOverviewMode = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@DappWebView, true)
        }
    }

    fun initialize(
        initialUrl: String,
        initialChainId: Long,
        onProgress: (Int) -> Unit,
        onProviderAvailability: (Boolean) -> Unit,
        onMessage: (String, String) -> Unit,
        onUrlChange: (String) -> Unit
    ) {
        initialAddress = initialUrl
        onProgressChanged = onProgress
        onProviderAvailabilityChanged = onProviderAvailability
        onBridgeMessage = onMessage
        onUrlChanged = onUrlChange
        installClients()
        installProvider(initialChainId)
        if (url == null) {
            loadUrl(initialAddress)
        } else {
            onProgress(currentProgress)
            onUrlChange(currentUrl)
        }
    }

    fun navigate(address: String): Boolean {
        val candidate = address.trim().let {
            if (it.contains("://")) it else "https://$it"
        }
        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return false
        loadUrl(uri.toString())
        return true
    }

    fun updateChain(chainId: Long) {
        emitChainChanged("0x${chainId.toString(16)}")
    }

    fun deliver(command: DappBridgeCommand) {
        val responseType = when (command) {
            is DappBridgeCommand.Result -> "result"
            is DappBridgeCommand.Error -> "error"
            is DappBridgeCommand.ChainChanged -> "chainChanged"
        }
        Log.i(LOG_TAG, "Delivering native provider response: $responseType")
        val targetOrigin = when (command) {
            is DappBridgeCommand.Result -> command.origin
            is DappBridgeCommand.Error -> command.origin
            is DappBridgeCommand.ChainChanged -> null
        }
        if (targetOrigin != null && Uri.parse(currentUrl).secureWebOriginOrNull() != targetOrigin) {
            Log.w(LOG_TAG, "Dropped provider response after page origin changed")
            return
        }
        when (command) {
            is DappBridgeCommand.Result -> sendProviderResult(command.id, command.value)
            is DappBridgeCommand.Error -> sendProviderError(command.id, command.code, command.message)
            is DappBridgeCommand.ChainChanged -> emitChainChanged(command.chainIdHex)
        }
    }

    fun release() {
        if (bridgeInstalled) runCatching { WebViewCompat.removeWebMessageListener(this, BRIDGE_NAME) }
        bridgeInstalled = false
        onProgressChanged = {}
        onProviderAvailabilityChanged = {}
        onBridgeMessage = { _, _ -> }
        onUrlChanged = {}
        stopLoading()
        loadUrl("about:blank")
        clearHistory()
        webChromeClient = null
        webViewClient = WebViewClient()
        removeAllViews()
        destroy()
    }

    private fun installClients() {
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                currentProgress = newProgress
                onProgressChanged(newProgress)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                val message = consoleMessage.message()
                if (message.startsWith("CoinMonitor provider")) {
                    if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        Log.e(LOG_TAG, message)
                    } else {
                        Log.d(LOG_TAG, message)
                    }
                }
                return false
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https") return false
                if (request.isForMainFrame) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.i(LOG_TAG, "Page started: ${url.orEmpty()}")
                url?.let(onUrlChanged)
                currentProgress = 0
                onProgressChanged(0)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.i(LOG_TAG, "Page finished: ${url.orEmpty()}")
                url?.let(onUrlChanged)
                currentProgress = 100
                onProgressChanged(100)
            }

        }
    }

    private fun installProvider(initialChainId: Long) {
        if (bridgeInstalled) return
        val supported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        onProviderAvailabilityChanged(supported)
        Log.i(LOG_TAG, "Secure provider injection supported=$supported")
        if (!supported) return

        WebViewCompat.addWebMessageListener(
            this,
            BRIDGE_NAME,
            PROVIDER_ORIGIN_RULES,
            object : WebViewCompat.WebMessageListener {
                override fun onPostMessage(
                    view: WebView,
                    message: WebMessageCompat,
                    sourceOrigin: Uri,
                    isMainFrame: Boolean,
                    replyProxy: JavaScriptReplyProxy
                ) {
                    val origin = sourceOrigin.secureWebOriginOrNull()
                    if (!isMainFrame || origin == null) return
                    Log.i(LOG_TAG, "Native provider message received from $origin")
                    message.data?.let { onBridgeMessage(it, origin) }
                }
            }
        )
        val providerResourceId = resources.getIdentifier("trust_min", "raw", context.packageName)
        check(providerResourceId != 0) { "Trust Web3 Provider resource is missing" }
        val providerScript = resources.openRawResource(providerResourceId)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        WebViewCompat.addDocumentStartJavaScript(
            this,
            providerScript + providerBootstrap(initialChainId, context.applicationIconDataUri()),
            PROVIDER_ORIGIN_RULES
        )
        bridgeInstalled = true
    }

    private fun sendProviderResult(id: Long, value: JsonElement) {
        val raw = JSON.encodeToString(JsonElement.serializer(), value)
        evaluateJavascript("window.ethereum?.sendResponse($id, $raw);", null)
    }

    private fun sendProviderError(id: Long, code: Int, message: String) {
        val error = JSON.encodeToString(
            JsonElement.serializer(),
            buildJsonObject { put("code", code); put("message", message) }
        )
        evaluateJavascript("window.ethereum?.sendError($id, $error);", null)
    }

    private fun emitChainChanged(chainIdHex: String) {
        val value = JSON.encodeToString(JsonElement.serializer(), JsonPrimitive(chainIdHex))
        evaluateJavascript(
            "window.ethereum?.setChainId($value); window.ethereum?.emit('chainChanged', $value);",
            null
        )
    }
}

private fun Uri.secureWebOriginOrNull(): String? {
    if (!scheme.equals("https", ignoreCase = true) || host.isNullOrBlank()) return null
    val explicitPort = port.takeIf { it >= 0 && it != 443 }?.let { ":$it" }.orEmpty()
    return "https://${host!!.lowercase(Locale.ROOT)}$explicitPort"
}

private fun providerBootstrap(chainId: Long, providerIcon: String): String {
    val encodedProviderIcon = JSON.encodeToString(JsonElement.serializer(), JsonPrimitive(providerIcon))
    return """
;(function() {
  const core = trustwallet.core('CALLBACK', function(params) {
    window.$BRIDGE_NAME.postMessage(JSON.stringify(params));
  });
  const ethereum = trustwallet.ethereum({ chainId: $chainId, rpcUrl: '' });
  if (typeof ethereum.setOverwriteMetamask === 'function') {
    ethereum.setOverwriteMetamask(true);
  }
  if (typeof trustwallet.nativeRpc === 'function' && typeof ethereum.setRPC === 'function') {
    ethereum.setRPC(trustwallet.nativeRpc(ethereum));
  }
  ethereum.sendResponse = core.sendResponse.bind(core);
  ethereum.sendError = core.sendError.bind(core);
  core.registerProviders([ethereum]);
  trustwallet.ethereum = ethereum;
  Object.assign(window.trustwallet, {
    isTrust: true,
    isTrustWallet: true,
    request: ethereum.request.bind(ethereum),
    send: ethereum.send.bind(ethereum),
    on: (...params) => ethereum.on(...params),
    off: (...params) => ethereum.off(...params)
  });
  window.ethereum = ethereum;
  window.trustWallet = window.trustwallet;

  const providerInfo = Object.freeze({
    uuid: trustwallet.randomUUID(),
    name: 'CoinMonitor',
    icon: $encodedProviderIcon,
    rdns: 'io.baiyanwu.coinmonitor'
  });
  const announceProvider = function() {
    window.dispatchEvent(new CustomEvent('eip6963:announceProvider', {
      detail: Object.freeze({ info: providerInfo, provider: ethereum })
    }));
  };
  window.addEventListener('eip6963:requestProvider', announceProvider);
  announceProvider();
  window.dispatchEvent(new Event('ethereum#initialized'));
})();
""".trimIndent()
}

private fun Context.applicationIconDataUri(): String {
    val drawable = packageManager.getApplicationIcon(applicationInfo)
    val bitmap = Bitmap.createBitmap(
        PROVIDER_ICON_SIZE_PX,
        PROVIDER_ICON_SIZE_PX,
        Bitmap.Config.ARGB_8888
    )
    drawable.setBounds(0, 0, bitmap.width, bitmap.height)
    drawable.draw(Canvas(bitmap))
    val png = ByteArrayOutputStream().use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            "Unable to encode the application icon"
        }
        output.toByteArray()
    }
    bitmap.recycle()
    return "data:image/png;base64,${Base64.encodeToString(png, Base64.NO_WRAP)}"
}

private val JSON = Json { explicitNulls = false }
private val PROVIDER_ORIGIN_RULES = setOf("*")
private const val PROVIDER_ICON_SIZE_PX = 128
