package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.NetworkLogEntry
import io.baiyanwu.coinmonitor.domain.model.NetworkLogProtocol
import io.baiyanwu.coinmonitor.domain.model.NetworkLogRecordingSettings
import kotlinx.coroutines.flow.Flow

/**
 * 网络日志仓库。
 *
 * 统一收口 HTTP / WSS 日志写入、录制状态和协议级开关，
 * UI 层只消费这里暴露出来的状态流。
 */
interface NetworkLogRepository {
    fun observeEntries(): Flow<List<NetworkLogEntry>>
    fun observeRecordingSettings(): Flow<NetworkLogRecordingSettings>
    fun getRecordingSettings(): NetworkLogRecordingSettings
    fun setRecordingEnabled(enabled: Boolean)
    fun setProtocolEnabled(protocol: NetworkLogProtocol, enabled: Boolean)
    fun clear()
    fun append(protocol: NetworkLogProtocol, line: String, detail: String = line)
}
