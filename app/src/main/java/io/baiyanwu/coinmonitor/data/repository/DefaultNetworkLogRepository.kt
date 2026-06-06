package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.domain.model.NetworkLogEntry
import io.baiyanwu.coinmonitor.domain.model.NetworkLogProtocol
import io.baiyanwu.coinmonitor.domain.model.NetworkLogRecordingSettings
import io.baiyanwu.coinmonitor.domain.repository.NetworkLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * 默认网络日志仓库实现。
 *
 * 当前先保存在内存里，便于调试 K 线和行情链路。
 * 后续如果要做持久化导出，只需要替换这里的存储实现，不影响上层调用。
 */
class DefaultNetworkLogRepository : NetworkLogRepository {
    private val entries = MutableStateFlow<List<NetworkLogEntry>>(emptyList())
    private val recordingSettings = MutableStateFlow(NetworkLogRecordingSettings())
    private val nextId = AtomicLong(1L)
    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun observeEntries(): Flow<List<NetworkLogEntry>> = entries.asStateFlow()

    override fun observeRecordingSettings(): Flow<NetworkLogRecordingSettings> = recordingSettings.asStateFlow()

    override fun getRecordingSettings(): NetworkLogRecordingSettings = recordingSettings.value

    override fun setRecordingEnabled(enabled: Boolean) {
        recordingSettings.update { current ->
            current.copy(recordingEnabled = enabled)
        }
    }

    override fun setProtocolEnabled(protocol: NetworkLogProtocol, enabled: Boolean) {
        recordingSettings.update { current ->
            when (protocol) {
                NetworkLogProtocol.HTTP -> current.copy(httpEnabled = enabled)
                NetworkLogProtocol.WSS -> current.copy(wssEnabled = enabled)
            }
        }
    }

    override fun clear() {
        entries.value = emptyList()
    }

    override fun append(protocol: NetworkLogProtocol, line: String, detail: String) {
        val settings = recordingSettings.value
        if (!settings.recordingEnabled || !settings.isEnabledFor(protocol)) return

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
            protocol = protocol,
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
