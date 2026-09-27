package io.baiyanwu.coinmonitor.ui.browser

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DappBrowserPolicyTest {
    @Test
    fun `provider injection is document start for every secure browser page`() {
        val source = readSource("app/src/main/java/io/baiyanwu/coinmonitor/ui/browser/DappWebView.kt")
        val activity = readSource("app/src/main/java/io/baiyanwu/coinmonitor/ui/browser/DappBrowserActivity.kt")
        val discoveryRepository = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/data/repository/DappDiscoveryRepository.kt"
        )

        assertTrue(source.contains("WebViewCompat.addDocumentStartJavaScript"))
        assertTrue(source.contains("WebViewCompat.addWebMessageListener"))
        assertTrue(source.contains("PROVIDER_ORIGIN_RULES = setOf(\"*\")"))
        assertTrue(source.contains("if (!isMainFrame || origin == null) return"))
        assertTrue(source.contains("secureWebOriginOrNull"))
        assertTrue(source.contains("Dropped provider response after page origin changed"))
        assertTrue(source.contains("Uri.parse(currentUrl).secureWebOriginOrNull() != targetOrigin"))
        assertFalse(activity.contains("walletOrigins"))
        assertFalse(discoveryRepository.contains("walletOrigins"))
        assertTrue(source.contains("mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW"))
        assertTrue(source.contains("allowFileAccess = false"))
        assertFalse(source.contains("handler?.proceed()"))
        assertFalse(source.contains("addJavascriptInterface"))
        assertTrue(source.contains("window.ethereum = ethereum"))
        assertTrue(source.contains("typeof ethereum.setOverwriteMetamask === 'function'"))
        assertTrue(source.contains("ethereum.setOverwriteMetamask(true)"))
        assertFalse(source.contains("ethereum.isMetaMask = true"))
        assertTrue(source.contains("core.registerProviders([ethereum])"))
        assertTrue(source.contains("eip6963:announceProvider"))
        assertTrue(source.contains("context.applicationIconDataUri()"))
        assertTrue(source.contains("data:image/png;base64"))
        assertFalse(source.contains("data:image/svg+xml"))
        assertFalse(source.contains("window.coinmonitor"))
        assertFalse(source.contains("window.web3"))
        assertFalse(source.contains("window.metamask"))
        assertTrue(source.contains("window.trustWallet = window.trustwallet"))
        assertTrue(source.contains("trustwallet.ethereum = ethereum"))
        assertTrue(source.contains("Object.assign(window.trustwallet"))
        assertTrue(source.contains("request: ethereum.request.bind(ethereum)"))
        assertTrue(source.contains("on: (...params) => ethereum.on(...params)"))
        assertFalse(source.contains("MutationObserver"))
        assertFalse(source.contains("querySelector"))
        assertTrue(source.contains("class DappWebView"))
        assertTrue(source.contains("fun updateChain(chainId: Long)"))
        assertFalse(source.contains("uniswapSheetCompatibilityScript"))
        assertFalse(source.contains(".mc-sheet-frame"))
    }

    @Test
    fun `browser toolbar buttons inherit a theme aware content color`() {
        val route = readSource("app/src/main/java/io/baiyanwu/coinmonitor/ui/browser/DappBrowserRoute.kt")

        // IconButton 的图标颜色来自 LocalContentColor，只有 Surface 会提供主题色，
        // 否则会回落成 Color.Black，导致日夜间模式下返回/刷新/关闭等按钮不随主题变化。
        val chromeSurface = route.indexOf("Surface(")
        val toolbar = route.indexOf("BrowserToolbar(")
        assertTrue(chromeSurface in 0 until toolbar)
        assertTrue(route.contains("contentColor = MaterialTheme.colorScheme.onBackground"))
    }

    @Test
    fun `browser routes approved transactions and message signatures through separate executors`() {
        val source = readSource("app/src/main/java/io/baiyanwu/coinmonitor/ui/browser/DappBrowserViewModel.kt")
        val repository = readSource("app/src/main/java/io/baiyanwu/coinmonitor/data/repository/DappBrowserRepository.kt")
        val signingRepository = readSource("app/src/main/java/io/baiyanwu/coinmonitor/data/repository/DappSigningRepository.kt")
        val messageSigner = readSource("app/src/main/java/io/baiyanwu/coinmonitor/data/wallet/DappMessageSigner.kt")

        assertTrue(source.contains("DappProviderAction.SendTransaction -> requestTransaction(request)"))
        assertTrue(source.contains("is DappProviderAction.Sign -> requestSignature(request, action.method)"))
        assertTrue(source.contains("method !in READ_ONLY_RPC_METHODS"))
        assertTrue(source.contains("privateKeyFor(approval.walletId"))
        assertTrue(source.contains("key.fill(0)"))
        assertTrue(source.contains("fun approveTransactionWithDerivedKey(derivedKey: ByteArray)"))
        assertTrue(source.contains("fun approveSignatureWithDerivedKey(derivedKey: ByteArray)"))
        assertTrue(source.contains("unlockWithDerivedKeyAndErase"))
        assertTrue(source.contains("val origin: String, val value: JsonElement"))
        assertTrue(source.contains("approval.request.origin"))
        assertTrue(repository.contains("evmSendRawTransaction"))
        assertTrue(repository.contains("expectedAddress"))
        assertTrue(repository.contains("balance >= value + transaction.maximumFee"))
        assertTrue(signingRepository.contains("Typed Data Chain ID 与当前网络不一致"))
        assertTrue(messageSigner.contains("EthereumMessageSigner.signTypedMessage"))
        assertTrue(messageSigner.contains("EthereumMessageSigner.signMessage"))
        assertTrue(messageSigner.contains("Curve.SECP256K1"))
    }

    @Test
    fun `browser surface is activity owned and supports address editing and chain selection`() {
        val route = readSource("app/src/main/java/io/baiyanwu/coinmonitor/ui/browser/DappBrowserRoute.kt")
        val webView = readSource("app/src/main/java/io/baiyanwu/coinmonitor/ui/browser/DappWebView.kt")
        val viewModel = readSource("app/src/main/java/io/baiyanwu/coinmonitor/ui/browser/DappBrowserViewModel.kt")
        val activity = readSource("app/src/main/java/io/baiyanwu/coinmonitor/ui/browser/DappBrowserActivity.kt")

        assertTrue(route.contains("webView: DappWebView?"))
        assertFalse(route.contains("visible: Boolean"))
        assertTrue(route.contains("DappApprovalDialogs(state = state, viewModel = viewModel)"))
        assertTrue(route.contains("BasicTextField("))
        assertTrue(route.contains("private fun CompactAddressField("))
        assertTrue(route.contains("onClose = onExit"))
        assertTrue(route.contains("Icons.Rounded.Close"))
        assertTrue(route.contains("R.string.browser_close"))
        assertTrue(route.contains("DropdownMenu("))
        assertTrue(route.contains("NetworkIcon(selectedNetwork"))
        assertTrue(route.contains(".statusBarsPadding()"))
        assertTrue(route.contains("if (progress < 100)"))
        assertTrue(route.contains("if (progress <= 0)"))
        assertTrue(route.contains(".zIndex(1f)"))
        assertTrue(route.contains(".clipToBounds()"))
        assertTrue(webView.contains("layoutParams = ViewGroup.LayoutParams"))
        assertTrue(webView.contains("ViewGroup.LayoutParams.MATCH_PARENT"))
        assertTrue(webView.contains("fun navigate(address: String): Boolean"))
        assertTrue(webView.contains("uri.scheme != \"https\""))
        assertFalse(webView.contains("doOnLayout"))
        assertTrue(webView.contains("if (url == null) {\n            loadUrl(initialAddress)"))
        assertTrue(viewModel.contains("fun selectNetwork(network: WalletNetwork)"))
        assertTrue(viewModel.contains("DappBridgeCommand.ChainChanged"))
        assertTrue(activity.contains("class DappBrowserActivity"))
        assertTrue(activity.contains("withFrameNanos"))
        assertTrue(activity.contains("repeat(2) { withFrameNanos { } }"))
        assertTrue(activity.contains("delay(CHROME_COMMIT_GRACE_MS)"))
        assertTrue(activity.contains("Browser chrome committed; creating WebView"))
        assertTrue(activity.contains("DappWebView(this@DappBrowserActivity)"))
        assertTrue(activity.contains("var browser by remember { mutableStateOf<DappWebView?>(null) }"))
        assertTrue(activity.contains("override fun onDestroy()"))
        assertTrue(activity.contains("webView?.release()"))

        val discovery = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/ui/browser/DappDiscoveryRoute.kt"
        )
        val compactSearch = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/ui/components/CompactSearchField.kt"
        )
        val marketSearch = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/ui/search/SearchRoute.kt"
        )
        val firstSearchField = discovery.indexOf("CompactSearchField(")
        val addressSuggestion = discovery.indexOf("webAddressSuggestion?.let")
        val historyHeader = discovery.indexOf("if (state.history.isNotEmpty())")
        val firstCategoryRow = discovery.indexOf("items(DappCategory.entries")
        val scrollingCatalog = discovery.indexOf("LazyColumn(")
        assertTrue(firstSearchField in 0 until scrollingCatalog)
        assertTrue(addressSuggestion in (firstSearchField + 1) until historyHeader)
        assertTrue(historyHeader in (addressSuggestion + 1) until firstCategoryRow)
        assertTrue(firstCategoryRow in (historyHeader + 1) until scrollingCatalog)
        assertTrue(discovery.contains("DappAddressParser.normalizeUserWebAddress(query)"))
        assertFalse(discovery.contains("DappAddressParser.normalize(submitted)?.let(onOpenBrowser)"))
        assertTrue(discovery.contains("pendingThirdPartyAddress = address"))
        assertTrue(discovery.contains("ThirdPartyWebsiteWarningDialog("))
        assertTrue(route.contains("pendingThirdPartyAddress = normalized"))
        assertTrue(route.contains("ThirdPartyWebsiteWarningDialog("))
        assertTrue(marketSearch.contains("CompactSearchField("))
        assertTrue(compactSearch.contains(".height(40.dp)"))
        assertTrue(compactSearch.contains("shape = RoundedCornerShape(18.dp)"))

        val approvals = readSource("app/src/main/java/io/baiyanwu/coinmonitor/ui/browser/DappApprovalDialogs.kt")
        val biometric = readSource(
            "app/src/main/java/io/baiyanwu/coinmonitor/ui/wallet/components/WalletBiometricPrompt.kt"
        )
        assertTrue(approvals.contains("WalletBiometricManager(context)"))
        assertTrue(approvals.contains("rememberWalletBiometricAuthorization("))
        assertTrue(approvals.contains("authorization.authenticate"))
        assertTrue(approvals.contains("authorization.revealPassword"))
        assertTrue(approvals.contains("if (authorization.showPassword)"))
        assertTrue(approvals.contains("onApproveWithDerivedKey"))
        assertTrue(biometric.contains("biometricManager.canAuthenticate()"))
        assertTrue(biometric.contains("promptWalletBiometric("))
        assertTrue(biometric.contains("LaunchedEffect(requestKey, available)"))
        assertTrue(biometric.contains("onAuthenticationError = { showPassword = true }"))
    }

    private fun readSource(path: String): String {
        val root = Paths.get("").toAbsolutePath()
        val direct = root.resolve(path)
        val module = root.resolve(path.removePrefix("app/"))
        return String(Files.readAllBytes(if (Files.exists(direct)) direct else module))
    }
}
