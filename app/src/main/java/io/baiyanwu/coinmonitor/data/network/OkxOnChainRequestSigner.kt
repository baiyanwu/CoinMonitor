package io.baiyanwu.coinmonitor.data.network

import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * OKX 链上行情接口的签名和链筛选都比较固定，统一收口在这里可以避免各仓库重复拼接。
 */
internal object OkxOnChainRequestSigner {
    private val okxTimestampFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'.000Z'")
        .withZone(ZoneOffset.UTC)

    fun buildSignature(
        timestamp: String,
        method: String,
        requestPath: String,
        secret: String,
        body: String = ""
    ): String {
        val preHash = "$timestamp${method.uppercase()}$requestPath$body"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(preHash.toByteArray(Charsets.UTF_8)))
    }

    /**
     * OKX 这里按固定毫秒位 `.000Z` 去签名更稳，和 curl 成功样例保持一致，避免 `Instant.toString()`
     * 在不同精度下生成不稳定文本，导致同一时刻的签名串不一致。
     */
    fun buildTimestamp(now: Instant = Instant.now()): String = okxTimestampFormatter.format(now)
}

/**
 * Solana 不是 EVM 链，查询时需要单独保留。
 * 这里优先覆盖主流 EVM 网络，后续如果 OKX 扩链，只需要补充这一组映射即可。
 */
internal object OkxOnChainChainRegistry {
    const val SOLANA_CHAIN_INDEX = "501"

    private val evmChainIndexes = linkedSetOf(
        "1",       // Ethereum
        "10",      // Optimism
        "56",      // BNB Smart Chain
        "137",     // Polygon
        "250",     // Fantom
        "324",     // zkSync Era
        "1101",    // Polygon zkEVM
        "5000",    // Mantle
        "7000",    // ZetaChain
        "8453",    // Base
        "42161",   // Arbitrum
        "43114",   // Avalanche C-Chain
        "59144",   // Linea
        "81457",   // Blast
        "34443",   // Mode
        "534352"   // Scroll
    )
    private val searchableChainIndexes = linkedSetOf<String>().apply {
        addAll(evmChainIndexes)
        add(SOLANA_CHAIN_INDEX)
    }

    /**
     * 当前产品只支持 EVM 和 Solana，因此直接内置一份查询链列表。
     * 这样可以省掉一次“获取支持链”的前置请求，降低凭证联调的阻塞面。
     */
    fun queryChains(
        requestedFamily: ChainFamily?,
        keyword: String
    ): List<String> {
        val keywordFamily = when {
            keyword.startsWith("0x", ignoreCase = true) -> ChainFamily.EVM
            keyword.length in 32..44 && keyword.all { it.isLetterOrDigit() && it !in "0OIl" } -> ChainFamily.SOL
            else -> null
        }
        val effectiveFamily = requestedFamily ?: keywordFamily
        return when (effectiveFamily) {
            ChainFamily.EVM -> evmChainIndexes
            ChainFamily.SOL -> linkedSetOf(SOLANA_CHAIN_INDEX)
            null -> searchableChainIndexes
        }
            .toList()
    }

    fun resolveChainFamily(chainIndex: String?, tokenAddress: String?): ChainFamily? {
        if (chainIndex == SOLANA_CHAIN_INDEX) return ChainFamily.SOL
        if (chainIndex != null && chainIndex in evmChainIndexes) return ChainFamily.EVM
        if (tokenAddress.isNullOrBlank()) return null
        return if (tokenAddress.startsWith("0x", ignoreCase = true)) {
            ChainFamily.EVM
        } else {
            ChainFamily.SOL
        }
    }
}
