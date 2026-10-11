package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import io.baiyanwu.coinmonitor.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AddressTrackingStore(context: Context) {
    private val preferences = context.getSharedPreferences("address_tracking", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableEntries = MutableStateFlow(runCatching {
        json.decodeFromString<List<TrackedAddress>>(preferences.getString("entries", null) ?: "[]")
    }.getOrDefault(emptyList()))
    val entries = mutableEntries.asStateFlow()

    @Synchronized
    fun track(address: String, chain: String, snapshot: WalletPortfolioSnapshot? = null, sourceName: String? = null) {
        val merged = mergeTrackedAddress(mutableEntries.value, address, chain, System.currentTimeMillis(), sourceName)
        save(merged.map { entry ->
            if (snapshot != null && trackedAddressKey(entry.address) == trackedAddressKey(snapshot.address)) entry.withPortfolio(snapshot) else entry
        })
    }

    @Synchronized
    fun update(snapshot: WalletPortfolioSnapshot) {
        save(mutableEntries.value.map { if (trackedAddressKey(it.address) == trackedAddressKey(snapshot.address)) it.withPortfolio(snapshot) else it })
    }

    @Synchronized
    fun rename(address: String, name: String) {
        save(mutableEntries.value.map { if (trackedAddressKey(it.address) == trackedAddressKey(address)) it.renamed(name) else it })
    }

    @Synchronized
    fun remove(address: String) = save(mutableEntries.value.filterNot { trackedAddressKey(it.address) == trackedAddressKey(address) })

    private fun save(entries: List<TrackedAddress>) {
        preferences.edit().putString("entries", json.encodeToString(entries)).apply()
        mutableEntries.value = entries
    }
}
