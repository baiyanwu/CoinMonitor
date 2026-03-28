package io.baiyanwu.coinmonitor.domain.repository

import io.baiyanwu.coinmonitor.domain.model.NetworkLogEntry
import kotlinx.coroutines.flow.Flow

interface NetworkLogRepository {
    fun observeEntries(): Flow<List<NetworkLogEntry>>
    fun observeRecordingEnabled(): Flow<Boolean>
    fun isRecordingEnabled(): Boolean
    fun setRecordingEnabled(enabled: Boolean)
    fun clear()
    fun append(line: String, detail: String = line)
}
