package io.baiyanwu.coinmonitor.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DappCatalog(
    val version: Int,
    val dapps: List<DappDefinition>
)

@Serializable
data class DappDefinition(
    val id: String,
    val name: String,
    val url: String,
    val domain: String,
    val descriptionZh: String,
    val descriptionEn: String,
    val category: DappCategory,
    val chainIds: List<Long>,
    val iconLabel: String,
    val iconColor: String,
    val sortOrder: Int,
    val enabled: Boolean = true
)

@Serializable
enum class DappCategory {
    SWAP,
    LENDING,
    BRIDGE,
    NFT,
    DATA
}

data class DappSearchHistory(
    val query: String,
    val searchedAt: Long
)
