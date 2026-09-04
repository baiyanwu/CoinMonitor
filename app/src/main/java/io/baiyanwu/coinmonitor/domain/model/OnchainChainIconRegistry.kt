package io.baiyanwu.coinmonitor.domain.model

/**
 * 链上币缺少自身图标时，统一回退到对应链图标。
 *
 * 这里覆盖当前产品会用到的主流链，没命中的场景继续保留通用占位图兜底。
 */
object OnchainChainIconRegistry {
    private val localIconUrlByChainIndex = mapOf(
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

    fun resolveIconUrl(chainIndex: String?): String? = resolveIconUrls(chainIndex).firstOrNull()

    /**
     * 本地映射优先；没有映射或映射下载失败时，调用方会顺序尝试通用在线链图标源。
     * 所有候选都失败后，由 UI/悬浮窗展示内置默认占位图。
     */
    fun resolveIconUrls(chainIndex: String?): List<String> {
        val raw = chainIndex?.trim()?.takeIf(String::isNotBlank) ?: return emptyList()
        val known = OnchainChainRegistry.find(raw) ?: OnchainChainRegistry.findByDexScreenerId(raw)
        val canonicalIndex = known?.chainIndex ?: raw
        val chainSlug = (known?.dexScreenerId ?: raw)
            .lowercase()
            .replace(Regex("[^a-z0-9-]+"), "-")
            .trim('-')
            .takeIf(String::isNotBlank)
            ?: return listOfNotNull(localIconUrlByChainIndex[canonicalIndex])

        return buildList {
            localIconUrlByChainIndex[canonicalIndex]?.let(::add)
            add("https://icons.llamao.fi/icons/chains/rsz_$chainSlug?w=64&h=64")
            add("https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/$chainSlug/info/logo.png")
        }.distinct()
    }
}
