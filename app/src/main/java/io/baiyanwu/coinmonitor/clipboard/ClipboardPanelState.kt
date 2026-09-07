package io.baiyanwu.coinmonitor.clipboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.baiyanwu.coinmonitor.R
import kotlinx.coroutines.*
import kotlin.math.roundToInt

internal class ClipboardPanelState(
    private val repository: ClipboardDataSource,
    private val scope: CoroutineScope
) {
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
        addresses = ClipboardAddressParser.extract(text.orEmpty())
        message = if (addresses.isEmpty()) R.string.clipboard_no_address else R.string.clipboard_choose_address
        if (addresses.size == 1) chooseAddress(addresses.single())
    }

    fun readFailed() { message = R.string.clipboard_read_failed }

    fun chooseAddress(value: String) {
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
        lookupJob = scope.launch {
            try {
                matches = withTimeout(15_000) { repository.lookup(value) }
                message = when (matches.size) {
                    0 -> R.string.clipboard_no_pool
                    1 -> null
                    else -> R.string.clipboard_choose_chain
                }
                if (matches.size == 1) chooseChain(matches.single())
            } catch (_: TimeoutCancellationException) {
                message = R.string.clipboard_request_failed
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                message = R.string.clipboard_request_failed
            } finally {
                if (isActive) loading = false
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
        chartJob = scope.launch {
            try {
                chart = withTimeout(15_000) { repository.chart(match) }
                chartUnavailable = chart.size < 2
            } catch (_: TimeoutCancellationException) {
                chartUnavailable = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                chartUnavailable = true
            } finally {
                if (isActive) chartLoading = false
            }
        }
        holdersLoading = true
        holdersJob = scope.launch {
            try {
                holderCount = withTimeout(8_000) { repository.holders(match) }
            } catch (_: TimeoutCancellationException) {
                holderCount = null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                holderCount = null
            } finally {
                if (isActive) holdersLoading = false
            }
        }
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
            minOf((safeWidth * 0.67f).roundToInt(), largeCardCap)
        },
        maxHeight = minOf(safeHeight, (fullWindowHeight.coerceAtLeast(0) * 0.52f).roundToInt())
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
