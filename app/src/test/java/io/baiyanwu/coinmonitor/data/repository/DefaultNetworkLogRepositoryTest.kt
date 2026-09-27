package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.domain.model.NetworkLogEventKind
import io.baiyanwu.coinmonitor.domain.model.NetworkLogProtocol
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultNetworkLogRepositoryTest {
    @Test
    fun `records typed events only while recording is enabled`() = runBlocking {
        val repository = DefaultNetworkLogRepository()
        repository.append(
            protocol = NetworkLogProtocol.HTTP,
            kind = NetworkLogEventKind.HTTP_REQUEST,
            line = "HTTP -> GET https://example.test"
        )
        assertEquals(emptyList<Any>(), repository.observeEntries().first())

        repository.setRecordingEnabled(true)
        repository.append(
            protocol = NetworkLogProtocol.HTTP,
            kind = NetworkLogEventKind.HTTP_REQUEST,
            line = "HTTP -> GET https://example.test"
        )
        repository.append(
            protocol = NetworkLogProtocol.HTTP,
            kind = NetworkLogEventKind.HTTP_RESPONSE,
            line = "HTTP <- 200 GET https://example.test"
        )

        val entries = repository.observeEntries().first()
        assertEquals(2, entries.size)
        assertEquals(NetworkLogEventKind.HTTP_RESPONSE, entries[0].kind)
        assertEquals(NetworkLogEventKind.HTTP_REQUEST, entries[1].kind)
    }
}
