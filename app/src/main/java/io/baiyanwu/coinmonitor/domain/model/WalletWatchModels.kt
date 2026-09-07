package io.baiyanwu.coinmonitor.domain.model

import java.math.BigDecimal

data class OkxWalletCredentials(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val secretKey: String = "",
    val passphrase: String = ""
) {
    val isComplete: Boolean
        get() = apiKey.isNotBlank() && secretKey.isNotBlank() && passphrase.isNotBlank()

    val isReady: Boolean
        get() = enabled && isComplete
}

enum class WalletAddressKind { EVM, SOLANA }

data class WalletAsset(
    val chainIndex: String,
    val chainName: String,
    val symbol: String,
    val contractAddress: String,
    val balance: BigDecimal,
    val tokenPriceUsd: BigDecimal?,
    val holdingValueUsd: BigDecimal?,
    val isRiskToken: Boolean
) {
    val id: String = "$chainIndex:${contractAddress.ifBlank { "native:$symbol" }}"
}

data class WalletPortfolioSnapshot(
    val address: String,
    val addressKind: WalletAddressKind,
    val assets: List<WalletAsset>,
    val totalValueUsd: BigDecimal,
    val totalIsEstimated: Boolean,
    val updatedAtMillis: Long
)

sealed interface WalletTotalResult {
    data class Success(val totalValueUsd: BigDecimal) : WalletTotalResult
    data class Failure(val message: String) : WalletTotalResult
}

object WalletAddressParser {
    private val evmPattern = Regex("^0x[0-9a-fA-F]{40}$")
    private const val base58Alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun classify(address: String): WalletAddressKind? {
        val value = address.trim()
        if (evmPattern.matches(value)) return WalletAddressKind.EVM
        return if (decodeBase58(value)?.size == 32) WalletAddressKind.SOLANA else null
    }

    internal fun decodeBase58(value: String): ByteArray? {
        if (value.isEmpty()) return null
        var bytes = byteArrayOf(0)
        value.forEach { character ->
            val digit = base58Alphabet.indexOf(character)
            if (digit < 0) return null
            var carry = digit
            for (index in bytes.lastIndex downTo 0) {
                val number = (bytes[index].toInt() and 0xff) * 58 + carry
                bytes[index] = number.toByte()
                carry = number ushr 8
            }
            while (carry > 0) {
                bytes = byteArrayOf(carry.toByte()) + bytes
                carry = carry ushr 8
            }
        }
        val leadingZeroCount = value.takeWhile { it == '1' }.length
        val significant = bytes.dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(leadingZeroCount) + significant
    }
}

object OkxWalletChainRegistry {
    const val SOLANA_CHAIN_INDEX = "501"
    val evmChainIndexes = listOf(
        "1", "10", "25", "56", "130", "137", "143", "146", "169", "196", "250",
        "324", "999", "1030", "1088", "1101", "1672", "4200", "4663", "5000", "7000",
        "81457", "8453", "9745", "42161", "43114", "534352", "57073", "59144"
    )

    private val names = mapOf(
        "1" to "Ethereum", "10" to "Optimism", "25" to "Cronos", "56" to "BNB Chain",
        "130" to "Unichain", "137" to "Polygon", "143" to "Monad", "146" to "Sonic",
        "169" to "Manta Pacific", "196" to "X Layer", "250" to "Fantom", "324" to "zkSync Era",
        "999" to "HyperEVM", "1030" to "Conflux eSpace", "1088" to "Metis",
        "1101" to "Polygon zkEVM", "1672" to "Pharos", "4200" to "Merlin",
        "4663" to "Robinhood", "5000" to "Mantle", "7000" to "ZetaChain", "81457" to "Blast",
        "8453" to "Base", "9745" to "Plasma", "42161" to "Arbitrum", "43114" to "Avalanche",
        "534352" to "Scroll", "57073" to "Ink", "59144" to "Linea", SOLANA_CHAIN_INDEX to "Solana"
    )

    fun indexesFor(kind: WalletAddressKind): List<String> =
        if (kind == WalletAddressKind.EVM) evmChainIndexes else listOf(SOLANA_CHAIN_INDEX)

    fun displayName(chainIndex: String): String = names[chainIndex] ?: "Chain $chainIndex"
}
