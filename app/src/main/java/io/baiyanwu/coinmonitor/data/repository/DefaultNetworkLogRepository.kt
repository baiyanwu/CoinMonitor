package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.domain.model.NetworkLogEntry
import io.baiyanwu.coinmonitor.domain.repository.NetworkLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class DefaultNetworkLogRepository : NetworkLogRepository {
    private val entries = MutableStateFlow<List<NetworkLogEntry>>(emptyList())
    private val recordingEnabled = MutableStateFlow(false)
    private val nextId = AtomicLong(1L)
    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun observeEntries(): Flow<List<NetworkLogEntry>> = entries.asStateFlow()

    override fun observeRecordingEnabled(): Flow<Boolean> = recordingEnabled.asStateFlow()

    override fun isRecordingEnabled(): Boolean = recordingEnabled.value

    override fun setRecordingEnabled(enabled: Boolean) {
        recordingEnabled.value = enabled
    }

    override fun clear() {
        entries.value = emptyList()
    }

    override fun append(line: String, detail: String) {
        if (!recordingEnabled.value) return

        val createdAt = System.currentTimeMillis()
        val formattedLine = "${timeFormatter.format(Date(createdAt))} $line"
        val formattedDetail = buildString {
            append(formattedLine)
            if (detail != line) {
                append('\n')
                append(detail)
            }
        }
        val entry = NetworkLogEntry(
            id = nextId.getAndIncrement(),
            line = formattedLine,
            detail = formattedDetail,
            createdAt = createdAt
        )
        entries.update { current ->
            listOf(entry) + current.take(MAX_ENTRY_COUNT - 1)
        }
    }

    private companion object {
        private const val MAX_ENTRY_COUNT = 800
    }
}
