package io.baiyanwu.coinmonitor.domain.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class TrackedAddress(
    val address: String,
    val chains: List<String>,
    val addedAtMillis: Long,
    val totalValueUsd: String? = null,
    val totalIsEstimated: Boolean = false,
    val chainValuesUsd: Map<String, String> = emptyMap(),
    val updatedAtMillis: Long? = null,
    val estimatedChains: Set<String> = emptySet(),
    val sourceName: String? = null,
    val customName: String? = null
)

fun trackedAddressKey(address: String): String =
    if (WalletAddressParser.classify(address) == WalletAddressKind.EVM) address.trim().lowercase() else address.trim()

fun mergeTrackedAddress(items: List<TrackedAddress>, address: String, chain: String, now: Long, sourceName: String? = null): List<TrackedAddress> {
    val kind = WalletAddressParser.classify(address) ?: return items
    if (chain !in OkxWalletChainRegistry.indexesFor(kind)) return items
    val key = trackedAddressKey(address)
    val existing = items.firstOrNull { trackedAddressKey(it.address) == key }
    val name = sourceName?.trim()?.takeIf(String::isNotBlank)
    val entry = existing?.copy(chains = (existing.chains + chain).distinct(), sourceName = name ?: existing.sourceName)
        ?: TrackedAddress(address.trim(), listOf(chain), now, sourceName = name)
    return if (existing == null) listOf(entry) + items else items.map { if (it == existing) entry else it }
}

fun TrackedAddress.withPortfolio(snapshot: WalletPortfolioSnapshot): TrackedAddress {
    require(trackedAddressKey(address) == trackedAddressKey(snapshot.address))
    val grouped = snapshot.assets.filterNot { it.isRiskToken }.groupBy { it.chainIndex }
    val allChains = (chains + grouped.keys).distinct()
    val values = allChains.associateWith { chain ->
        val assets = grouped[chain].orEmpty()
        assets.mapNotNull { it.holdingValueUsd }.fold(BigDecimal.ZERO, BigDecimal::add).toPlainString()
    }
    return copy(
        chains = allChains,
        totalValueUsd = snapshot.totalValueUsd.toPlainString(),
        totalIsEstimated = snapshot.totalIsEstimated,
        chainValuesUsd = values,
        updatedAtMillis = snapshot.updatedAtMillis,
        estimatedChains = grouped.filterValues { assets -> assets.any { it.holdingValueUsd == null } }.keys
    )
}

val TrackedAddress.displayName: String? get() = customName ?: sourceName

fun TrackedAddress.renamed(name: String): TrackedAddress = copy(customName = name.trim().takeIf(String::isNotBlank))
