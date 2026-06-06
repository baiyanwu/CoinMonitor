package io.baiyanwu.coinmonitor.domain.model

/**
 * 链上币缺少自身图标时，统一回退到对应链图标。
 *
 * 这里覆盖当前产品会用到的主流链，没命中的场景继续保留通用占位图兜底。
 */
object OnchainChainIconRegistry {
    private val iconUrlByChainIndex = mapOf(
        "1" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/ethereum/info/logo.png",
        "10" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/optimism/info/logo.png",
        "56" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/smartchain/info/logo.png",
        "137" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/polygon/info/logo.png",
        "250" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/fantom/info/logo.png",
        "324" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/zksync/info/logo.png",
        "1101" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/polygonzkevm/info/logo.png",
        "5000" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/mantle/info/logo.png",
        "7000" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/zetachain/info/logo.png",
        "8453" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/base/info/logo.png",
        "42161" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/arbitrum/info/logo.png",
        "43114" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/avalanchec/info/logo.png",
        "59144" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/linea/info/logo.png",
        "81457" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/blast/info/logo.png",
        "34443" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/mode/info/logo.png",
        "534352" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/scroll/info/logo.png",
        "501" to "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/solana/info/logo.png"
    )

    fun resolveIconUrl(chainIndex: String?): String? = iconUrlByChainIndex[chainIndex]
}
