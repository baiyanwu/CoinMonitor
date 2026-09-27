package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.data.local.DappSearchHistoryEntity
import io.baiyanwu.coinmonitor.data.local.dao.DappSearchHistoryDao
import io.baiyanwu.coinmonitor.domain.model.DappCatalog
import io.baiyanwu.coinmonitor.domain.model.DappDefinition
import io.baiyanwu.coinmonitor.domain.model.DappSearchHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.net.IDN
import java.util.Locale

class DappDiscoveryRepository(
    context: Context,
    private val historyDao: DappSearchHistoryDao,
    json: Json = Json { ignoreUnknownKeys = true }
) {
    val catalog: List<DappDefinition> = context.resources.openRawResource(R.raw.dapp_catalog_v1)
        .bufferedReader(Charsets.UTF_8)
        .use { reader -> json.decodeFromString<DappCatalog>(reader.readText()) }
        .dapps
        .filter(DappDefinition::enabled)
        .sortedBy(DappDefinition::sortOrder)

    fun observeSearchHistory(limit: Int = HISTORY_DISPLAY_LIMIT): Flow<List<DappSearchHistory>> {
        return historyDao.observeRecent(limit).map { rows ->
            rows.map { DappSearchHistory(query = it.query, searchedAt = it.searchedAt) }
        }
    }

    suspend fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        historyDao.upsert(
            DappSearchHistoryEntity(
                normalizedQuery = trimmed.lowercase(Locale.ROOT),
                query = trimmed,
                searchedAt = System.currentTimeMillis()
            )
        )
        historyDao.trimTo(HISTORY_STORAGE_LIMIT)
    }

    suspend fun clearSearchHistory() {
        historyDao.clear()
    }

    companion object {
        private const val HISTORY_DISPLAY_LIMIT = 8
        private const val HISTORY_STORAGE_LIMIT = 20
    }
}

object DappAddressParser {
    fun normalize(address: String): String? {
        val trimmed = address.trim()
        if (trimmed.isBlank()) return null
        val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = runCatching { java.net.URI(candidate) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return null
        return uri.normalize().toString()
    }

    fun origin(address: String): String? {
        val normalized = normalize(address) ?: return null
        val uri = runCatching { java.net.URI(normalized) }.getOrNull() ?: return null
        val port = if (uri.port >= 0 && uri.port != 443) ":${uri.port}" else ""
        return "https://${uri.host.lowercase(Locale.ROOT)}$port"
    }

    fun normalizeUserWebAddress(address: String): String? {
        val normalized = normalize(address) ?: return null
        val host = runCatching { java.net.URI(normalized).host }.getOrNull() ?: return null
        return normalized.takeIf { host.isValidPublicWebHost() }
    }

    private fun String.isValidPublicWebHost(): Boolean {
        if (isBlank() || contains('_')) return false
        if (all { it.isDigit() || it == '.' }) {
            val octets = split('.')
            return octets.size == 4 && octets.all { part ->
                part.isNotEmpty() &&
                    part.length <= 3 &&
                    part.toIntOrNull()?.let { it in 0..255 } == true
            }
        }
        val asciiHost = runCatching { IDN.toASCII(this) }.getOrNull() ?: return false
        if (!asciiHost.contains('.') || asciiHost.length > 253) return false
        return asciiHost.split('.').all { label ->
            label.isNotEmpty() &&
                label.length <= 63 &&
                label.first().isLetterOrDigit() &&
                label.last().isLetterOrDigit() &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }
}
