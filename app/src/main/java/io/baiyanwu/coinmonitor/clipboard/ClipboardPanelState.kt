package io.baiyanwu.coinmonitor.clipboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.baiyanwu.coinmonitor.R
import kotlinx.coroutines.*
import kotlin.math.roundToInt
import io.baiyanwu.coinmonitor.domain.model.WalletAddressKind
import io.baiyanwu.coinmonitor.domain.model.WalletAddressParser
import io.baiyanwu.coinmonitor.domain.model.WalletPortfolioSnapshot

internal enum class ClipboardContentMode { TOKEN, WALLET, FOMO }

internal class ClipboardPanelState(
    private val repository: ClipboardDataSource,
    private val scope: CoroutineScope,
    private val wallets: ClipboardWalletDataSource? = null,
    private val fomo: FomoProfileDataSource = FomoProfileResolver()
) {
    var mode by mutableStateOf(ClipboardContentMode.TOKEN)
        private set
    var walletChain by mutableStateOf<String?>(null)
        private set
    var walletSnapshot by mutableStateOf<WalletPortfolioSnapshot?>(null)
        private set
    var walletLoading by mutableStateOf(false)
        private set
    var walletMessage by mutableStateOf<Int?>(null)
        private set
    var fomoLinks by mutableStateOf<List<FomoProfileLink>>(emptyList())
        private set
    var fomoLink by mutableStateOf<FomoProfileLink?>(null)
        private set
    var fomoWallets by mutableStateOf<FomoProfileWallets?>(null)
        private set
    var fomoLoading by mutableStateOf(false)
        private set
    var fomoMessage by mutableStateOf<Int?>(null)
        private set
    var fomoEvmChain by mutableStateOf<String?>(null)
        private set
    val walletPreview: ClipboardWalletPreview? get() = walletChain?.let { chain -> walletSnapshot?.let { ClipboardWalletPreview(it, chain) } }
    val supportsWallet: Boolean get() = wallets != null && address?.let(WalletAddressParser::classify) != null
    private var walletJob: Job? = null
    private var fomoJob: Job? = null
    private var generation = 0L
    var addresses by mutableStateOf<List<String>>(emptyList())
        private set
    var address by mutableStateOf<String?>(null)
        private set
    var matches by mutableStateOf<List<ClipboardMatch>>(emptyList())
        private set
    var selected by mutableStateOf<ClipboardMatch?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<Int?>(R.string.clipboard_reading)
        private set
    var chart by mutableStateOf<List<ClipboardChartPoint>>(emptyList())
        private set
    var chartLoading by mutableStateOf(false)
        private set
    var chartUnavailable by mutableStateOf(false)
        private set
    var holderCount by mutableStateOf<Int?>(null)
        private set
    var holdersLoading by mutableStateOf(false)
        private set
    var actionMessage by mutableStateOf<Int?>(null)
    var actionBusy by mutableStateOf(false)
    private var lookupJob: Job? = null
    private var chartJob: Job? = null
    private var holdersJob: Job? = null

    fun receive(text: String?) {
        cancelQueries()
        generation++
        address = null; selected = null; matches = emptyList(); chart = emptyList()
        walletSnapshot = null; walletChain = null; walletMessage = null
        fomoWallets = null; fomoLink = null; fomoMessage = null; fomoEvmChain = null
        addresses = emptyList(); mode = ClipboardContentMode.TOKEN
        if (text?.contains("PRIVATE KEY") == true || text?.contains("GMGN_PRIVATE_KEY") == true) { message = R.string.clipboard_no_address; return }
        fomoLinks = FomoProfileLink.extractAll(text.orEmpty())
        if (fomoLinks.isNotEmpty()) {
            mode = ClipboardContentMode.FOMO
            if (fomoLinks.size == 1) chooseFomo(fomoLinks.single())
            return
        }
        addresses = ClipboardAddressParser.extract(text.orEmpty())
        message = if (addresses.isEmpty()) R.string.clipboard_no_address else R.string.clipboard_choose_address
        if (addresses.size == 1) chooseAddress(addresses.single())
    }

    fun readFailed() { message = R.string.clipboard_read_failed }

    fun chooseAddress(value: String) {
        generation++
        mode = ClipboardContentMode.TOKEN
        walletJob?.cancel(); walletSnapshot = null; walletChain = null; walletLoading = false; walletMessage = null
        lookupJob?.cancel()
        chartJob?.cancel()
        holdersJob?.cancel()
        address = value
        matches = emptyList()
        selected = null
        chart = emptyList()
        holderCount = null
        holdersLoading = false
        actionMessage = null
        loading = true
        message = null
        val version = generation
        lookupJob = scope.launch {
            try {
                val result = withTimeout(15_000) { repository.lookup(value) }
                if (version != generation || !isActive) return@launch
                matches = result
                message = when (matches.size) {
                    0 -> R.string.clipboard_no_pool
                    1 -> null
                    else -> R.string.clipboard_choose_chain
                }
                if (matches.size == 1) chooseChain(matches.single())
            } catch (_: TimeoutCancellationException) {
                if (version == generation && isActive) message = R.string.clipboard_request_failed
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (version == generation && isActive) message = R.string.clipboard_request_failed
            } finally {
                if (isActive && version == generation) loading = false
            }
        }
    }

    fun chooseChain(match: ClipboardMatch) {
        chartJob?.cancel()
        holdersJob?.cancel()
        selected = match
        message = null
        chart = emptyList()
        chartUnavailable = false
        holderCount = null
        chartLoading = true
        actionMessage = null
        val version = generation
        chartJob = scope.launch {
            try {
                val result = withTimeout(15_000) { repository.chart(match) }
                if (version == generation && isActive && selected == match) {
                    chart = result
                    chartUnavailable = chart.size < 2
                }
            } catch (_: TimeoutCancellationException) {
                if (version == generation && isActive && selected == match) chartUnavailable = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (version == generation && isActive && selected == match) chartUnavailable = true
            } finally {
                if (isActive && version == generation && selected == match) chartLoading = false
            }
        }
        holdersLoading = true
        holdersJob = scope.launch {
            try {
                val result = withTimeout(8_000) { repository.holders(match) }
                if (version == generation && isActive && selected == match) holderCount = result
            } catch (_: TimeoutCancellationException) {
                if (version == generation && isActive && selected == match) holderCount = null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (version == generation && isActive && selected == match) holderCount = null
            } finally {
                if (isActive && version == generation && selected == match) holdersLoading = false
            }
        }
    }

    fun useTokenMode() {
        walletJob?.cancel(); walletLoading = false
        mode = ClipboardContentMode.TOKEN
        selected?.let(::chooseChain) ?: address?.let(::chooseAddress)
    }

    fun useWalletMode() {
        if (!supportsWallet) return
        lookupJob?.cancel(); chartJob?.cancel(); holdersJob?.cancel()
        generation++; loading = false; chartLoading = false; holdersLoading = false
        mode = ClipboardContentMode.WALLET
        if (WalletAddressParser.classify(address.orEmpty()) == WalletAddressKind.SOLANA) walletChain = "501"
        else if (walletChain == null) walletChain = selected?.chain?.chainIndex
        if (walletChain != null) loadWallet()
    }

    fun chooseWalletChain(chain: String) {
        walletChain = chain
        if (walletSnapshot == null) loadWallet()
        else if (walletSnapshot?.address != address) { walletSnapshot = null; loadWallet() }
    }

    fun loadWallet() {
        val target = address ?: return
        if (walletChain == null || wallets == null) return
        walletJob?.cancel()
        walletMessage = null
        if (!wallets.isConfigured()) { walletLoading = false; walletMessage = R.string.wallet_watch_credentials_required; return }
        walletLoading = true
        val version = generation
        walletJob = scope.launch {
            try {
                val snapshot = withTimeout(20_000) { wallets.load(target) }
                if (version == generation && isActive && snapshot.address.equals(target, WalletAddressParser.classify(target) == WalletAddressKind.EVM)) walletSnapshot = snapshot
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException && version == generation) walletMessage = R.string.clipboard_request_failed else throw error
            } catch (_: Exception) { if (version == generation && isActive) walletMessage = R.string.clipboard_wallet_failed }
            finally { if (version == generation && isActive) walletLoading = false }
        }
    }

    fun chooseFomo(link: FomoProfileLink) {
        cancelQueries(); generation++
        mode = ClipboardContentMode.FOMO; fomoLink = link; fomoWallets = null
        fomoMessage = null; fomoLoading = true; fomoEvmChain = null; address = null; matches = emptyList(); selected = null
        val version = generation
        fomoJob = scope.launch {
            try {
                val result = withTimeout(20_000) { fomo.resolve(link) }
                if (version == generation && isActive) {
                    if (!result.handle.equals(link.handle, true)) throw FomoProfileException(FomoFailure.UNAVAILABLE)
                    fomoWallets = result
                }
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException && version == generation) fomoMessage = R.string.clipboard_fomo_unavailable else throw error
            } catch (error: Exception) {
                if (version == generation) fomoMessage = when ((error as? FomoProfileException)?.reason) {
                    FomoFailure.NOT_FOUND -> R.string.clipboard_fomo_not_found
                    FomoFailure.NO_EVIDENCE -> R.string.clipboard_fomo_no_evidence
                    else -> R.string.clipboard_fomo_unavailable
                }
            } finally { if (version == generation && isActive) fomoLoading = false }
        }
    }

    fun selectFomoChain(chain: String) { fomoEvmChain = chain }
    fun showFomoWallet(evidence: FomoWalletEvidence) {
        if (evidence != fomoWallets?.solana && evidence != fomoWallets?.evm) return
        val chain = if (evidence == fomoWallets?.solana) "501" else fomoEvmChain ?: return
        cancelQueries(); generation++; address = evidence.address; selected = null; matches = emptyList()
        walletSnapshot = null; walletChain = chain; mode = ClipboardContentMode.WALLET
        loadWallet()
    }
    fun showFomoResult() { walletJob?.cancel(); walletLoading = false; mode = ClipboardContentMode.FOMO }
    private fun cancelQueries() {
        lookupJob?.cancel(); chartJob?.cancel(); holdersJob?.cancel(); walletJob?.cancel(); fomoJob?.cancel()
        loading = false; chartLoading = false; holdersLoading = false; walletLoading = false; fomoLoading = false
    }
}

internal data class ClipboardPanelPosition(val x: Int, val y: Int)

internal data class ClipboardPanelMeasure(val width: Int, val maxHeight: Int)

internal fun clipboardPanelMeasure(
    availableWidth: Int,
    availableHeight: Int,
    fullWindowHeight: Int,
    compactBreakpoint: Int,
    largeCardCap: Int
): ClipboardPanelMeasure {
    val safeWidth = availableWidth.coerceAtLeast(0)
    val safeHeight = availableHeight.coerceAtLeast(0)
    return ClipboardPanelMeasure(
        width = if (safeWidth <= compactBreakpoint) {
            safeWidth
        } else {
            minOf(safeWidth, largeCardCap)
        },
        maxHeight = minOf(safeHeight, (fullWindowHeight.coerceAtLeast(0) * 0.9f).roundToInt())
    )
}

internal fun clipboardPanelPosition(
    anchorX: Int, anchorTop: Int, anchorBottom: Int,
    width: Int, height: Int, availableWidth: Int, availableHeight: Int, gap: Int
): ClipboardPanelPosition {
    val below = anchorBottom + gap
    val y = if (below + height <= availableHeight) below else anchorTop - height - gap
    return ClipboardPanelPosition(
        anchorX.coerceIn(0, (availableWidth - width).coerceAtLeast(0)),
        y.coerceIn(0, (availableHeight - height).coerceAtLeast(0))
    )
}
