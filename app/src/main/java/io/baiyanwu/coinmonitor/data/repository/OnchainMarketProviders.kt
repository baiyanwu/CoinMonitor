package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.DexScreenerClient
import io.baiyanwu.coinmonitor.data.network.DexScreenerPairSelector
import io.baiyanwu.coinmonitor.data.network.OkxDexMarketClient
import io.baiyanwu.coinmonitor.data.network.PinnedPoolSelectionPolicy
import io.baiyanwu.coinmonitor.data.refresh.OnchainPollingStrategies
import io.baiyanwu.coinmonitor.data.refresh.OnchainPollingStrategy
import io.baiyanwu.coinmonitor.domain.model.ChainFamily
import io.baiyanwu.coinmonitor.domain.model.ExchangeSource
import io.baiyanwu.coinmonitor.domain.model.MarketQuote
import io.baiyanwu.coinmonitor.domain.model.MarketType
import io.baiyanwu.coinmonitor.domain.model.OnchainChain
import io.baiyanwu.coinmonitor.domain.model.OnchainChainRegistry
import io.baiyanwu.coinmonitor.domain.model.OnchainDataProvider
import io.baiyanwu.coinmonitor.domain.model.OnchainPoolOption
import io.baiyanwu.coinmonitor.domain.model.PoolTokenSide
import io.baiyanwu.coinmonitor.domain.model.WatchItem
import io.baiyanwu.coinmonitor.domain.model.inferOnchainChainFamily
import io.baiyanwu.coinmonitor.domain.model.normalizeOnchainAddress
import io.baiyanwu.coinmonitor.domain.model.onchainAddressesEqual
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

interface OnchainMarketProvider {
    val type: OnchainDataProvider
    val pollingStrategy: OnchainPollingStrategy
        get() = OnchainPollingStrategies.default

    fun isConfigured(): Boolean = true

    /** Converts a canonical watch item into the chain identifier expected by this provider. */
    fun prepareItem(item: WatchItem): WatchItem? = item.copy(onchainDataProvider = type)

    suspend fun search(keyword: String, chainFamilyFilter: ChainFamily? = null): List<WatchItem>

    suspend fun fetchQuotes(items: List<WatchItem>): List<MarketQuote>
}

class DexScreenerOnchainMarketProvider(
    private val client: DexScreenerClient
) : OnchainMarketProvider {
    override val type: OnchainDataProvider = OnchainDataProvider.DEX_SCREENER
    override val pollingStrategy: OnchainPollingStrategy = OnchainPollingStrategies.dexScreener
    private val pinnedPoolSelectionPolicy = PinnedPoolSelectionPolicy()

    override fun prepareItem(item: WatchItem): WatchItem? {
        val rawChainId = item.chainIndex?.takeIf(String::isNotBlank) ?: return null
        val knownChain = OnchainChainRegistry.find(rawChainId)
            ?: OnchainChainRegistry.findByDexScreenerId(rawChainId)
        if (knownChain == null && item.onchainDataProvider != type) return null
        return item.copy(
            chainIndex = knownChain?.dexScreenerId ?: rawChainId,
            onchainDataProvider = type
        )
    }

    override suspend fun search(keyword: String, chainFamilyFilter: ChainFamily?): List<WatchItem> {
        val pairs = client.searchPairs(keyword)
        return pairs
            .filter { it.chainId.isNotBlank() }
            .groupBy { it.chainId.lowercase() }
            .values
            .flatMap { chainPairs ->
                val upstreamChainId = chainPairs.first().chainId
                val family = inferOnchainChainFamily(
                    dexScreenerId = upstreamChainId,
                    addresses = chainPairs.flatMap { pair ->
                        listOf(pair.baseToken.address, pair.quoteToken.address)
                    }
                )
                if (chainFamilyFilter != null && family != chainFamilyFilter) return@flatMap emptyList()
                val chain = OnchainChainRegistry.resolveDexScreenerChain(upstreamChainId, family)
                    ?: return@flatMap emptyList()
                val addresses = chainPairs.flatMap { pair ->
                    DexScreenerPairSelector.matchingTokenAddresses(pair, keyword, chain.family)
                }
                addresses
                    .distinctBy { normalizeOnchainAddress(chain.family, it) }
                    .mapNotNull { address ->
                        val candidates = DexScreenerPairSelector.candidates(
                            pairs = chainPairs,
                            tokenAddress = address,
                            family = chain.family
                        )
                        candidates.firstOrNull()?.toWatchItem(chain, candidates)
                    }
            }
            .distinctBy(WatchItem::semanticKey)
            .sortedWith(compareBy({ it.symbol }, { it.name }))
    }

    override suspend fun fetchQuotes(items: List<WatchItem>): List<MarketQuote> = coroutineScope {
        if (items.isEmpty()) return@coroutineScope emptyList()
        items.groupBy { item ->
            OnchainChainRegistry.resolve(
                chainIndexOrDexScreenerId = item.chainIndex,
                family = item.chainFamily ?: inferOnchainChainFamily(
                    dexScreenerId = item.chainIndex,
                    addresses = listOfNotNull(item.tokenAddress)
                )
            )
        }
            .filterKeys { it != null }
            .map { (chainOrNull, chainItems) ->
                async {
                    val chain = chainOrNull ?: return@async emptyList()
                    chainItems.chunked(DEX_MAX_TOKEN_ADDRESSES).flatMap { chunk ->
                        try {
                            val addresses = chunk.mapNotNull { item ->
                                item.tokenAddress?.takeIf(String::isNotBlank)
                                    ?.let { normalizeOnchainAddress(chain.family, it) }
                            }.distinct()
                            if (addresses.isEmpty()) return@flatMap emptyList()
                            val pairs = client.getTokenPairsBatch(chain.dexScreenerId, addresses)
                                .filter { it.chainId.equals(chain.dexScreenerId, ignoreCase = true) }
                            chunk.mapNotNull { item ->
                                val tokenAddress = item.tokenAddress ?: return@mapNotNull null
                                val selected = pinnedPoolSelectionPolicy.select(
                                    itemId = item.id,
                                    pairs = pairs,
                                    tokenAddress = tokenAddress,
                                    family = chain.family,
                                    preferredPoolAddress = item.poolAddress
                                ) ?: return@mapNotNull null
                                val selectedPoolAddress = normalizeOnchainAddress(
                                    chain.family,
                                    selected.pair.pairAddress
                                )
                                val bindingChanged = item.poolAddress == null ||
                                    !onchainAddressesEqual(chain.family, item.poolAddress, selectedPoolAddress) ||
                                    item.poolTokenSide != selected.tokenSide
                                MarketQuote(
                                    id = item.id,
                                    symbol = selected.pairLabel,
                                    name = item.name,
                                    priceUsd = selected.priceUsd,
                                    change24hPercent = selected.change24hPercent,
                                    marketCap = selected.marketCap,
                                    poolAddress = selectedPoolAddress,
                                    poolTokenSide = selected.tokenSide,
                                    requestedPoolAddress = item.poolAddress,
                                    requestedPoolTokenSide = item.poolTokenSide,
                                    resetTrend = bindingChanged
                                )
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
            }
            .awaitAll()
            .flatten()
    }

    private fun io.baiyanwu.coinmonitor.data.network.SelectedDexPair.toWatchItem(
        chain: OnchainChain,
        candidates: List<io.baiyanwu.coinmonitor.data.network.SelectedDexPair>
    ): WatchItem {
        val normalizedAddress = normalizeOnchainAddress(chain.family, tokenAddress)
        val poolOptions = candidates.map { candidate -> candidate.toPoolOption(chain) }
        val selectedPool = poolOptions.firstOrNull()
        return WatchItem(
            id = "onchain:${chain.chainIndex}:$normalizedAddress",
            symbol = selectedPool?.pairLabel ?: tokenSymbol.uppercase(),
            name = tokenName,
            exchangeSource = ExchangeSource.ONCHAIN,
            marketType = MarketType.ONCHAIN_TOKEN,
            chainFamily = chain.family,
            chainIndex = chain.chainIndex,
            tokenAddress = normalizedAddress,
            onchainDataProvider = type,
            poolAddress = normalizeOnchainAddress(chain.family, pair.pairAddress),
            poolTokenSide = tokenSide,
            iconUrl = pair.info?.imageUrl.takeIf { tokenSide == PoolTokenSide.BASE },
            lastPrice = priceUsd,
            change24hPercent = change24hPercent,
            marketCap = marketCap,
            lastUpdatedAt = System.currentTimeMillis(),
            addedAt = System.currentTimeMillis(),
            selectedPool = selectedPool,
            poolOptions = poolOptions
        )
    }

    private fun io.baiyanwu.coinmonitor.data.network.SelectedDexPair.toPoolOption(
        chain: OnchainChain
    ): OnchainPoolOption = OnchainPoolOption(
        poolAddress = normalizeOnchainAddress(chain.family, pair.pairAddress),
        tokenSide = tokenSide,
        pairLabel = pairLabel,
        dexId = pair.dexId,
        labels = pair.labels.orEmpty(),
        liquidityUsd = pair.liquidity?.usd ?: 0.0,
        volume24hUsd = pair.volume["h24"],
        priceUsd = priceUsd,
        change24hPercent = change24hPercent,
        marketCap = marketCap,
        poolUrl = pair.url
    )
}

internal class OkxDexOnchainMarketProvider(
    private val client: OkxDexMarketClient
) : OnchainMarketProvider {
    override val type: OnchainDataProvider = OnchainDataProvider.OKX_DEX
    override val pollingStrategy: OnchainPollingStrategy = OnchainPollingStrategies.okxDex

    override fun isConfigured(): Boolean = client.isConfigured()

    override fun prepareItem(item: WatchItem): WatchItem? {
        val rawChainId = item.chainIndex?.takeIf(String::isNotBlank) ?: return null
        val knownChain = OnchainChainRegistry.find(rawChainId)
            ?: OnchainChainRegistry.findByDexScreenerId(rawChainId)
        if (knownChain == null && item.onchainDataProvider != type) return null
        return item.copy(
            chainIndex = knownChain?.chainIndex ?: rawChainId,
            onchainDataProvider = type
        )
    }

    override suspend fun search(keyword: String, chainFamilyFilter: ChainFamily?): List<WatchItem> {
        if (!isConfigured()) return emptyList()
        return client.searchTokens(keyword).mapNotNull { token ->
            val family = inferFamily(token.chainIndex, token.tokenContractAddress)
            if (chainFamilyFilter != null && family != chainFamilyFilter) return@mapNotNull null
            val normalizedAddress = normalizeOnchainAddress(family, token.tokenContractAddress)
            WatchItem(
                id = "onchain:${token.chainIndex}:$normalizedAddress",
                symbol = token.tokenSymbol.uppercase().ifBlank { "?" },
                name = token.tokenName.ifBlank { token.tokenSymbol },
                exchangeSource = ExchangeSource.ONCHAIN,
                marketType = MarketType.ONCHAIN_TOKEN,
                chainFamily = family,
                chainIndex = token.chainIndex,
                tokenAddress = normalizedAddress,
                onchainDataProvider = type,
                iconUrl = token.tokenLogoUrl,
                lastPrice = token.price,
                change24hPercent = token.change24hPercent,
                marketCap = token.marketCap,
                lastUpdatedAt = token.price?.let { System.currentTimeMillis() },
                addedAt = System.currentTimeMillis()
            )
        }
            .distinctBy(WatchItem::semanticKey)
            .sortedWith(compareBy({ it.symbol }, { it.name }))
    }

    override suspend fun fetchQuotes(items: List<WatchItem>): List<MarketQuote> {
        if (!isConfigured() || items.isEmpty()) return emptyList()
        val itemByKey = items.mapNotNull { item ->
            val chainIndex = item.chainIndex ?: return@mapNotNull null
            val address = item.tokenAddress ?: return@mapNotNull null
            okxKey(chainIndex, address, item.chainFamily) to item
        }.toMap()
        return client.getQuotes(itemByKey.values.mapNotNull { item ->
            val chainIndex = item.chainIndex ?: return@mapNotNull null
            val address = item.tokenAddress ?: return@mapNotNull null
            chainIndex to normalizeOnchainAddress(item.chainFamily, address)
        }).mapNotNull { quote ->
            val family = inferFamily(quote.chainIndex, quote.tokenContractAddress)
            val item = itemByKey[okxKey(quote.chainIndex, quote.tokenContractAddress, family)]
                ?: return@mapNotNull null
            MarketQuote(
                id = item.id,
                symbol = item.symbol,
                name = item.name,
                priceUsd = quote.price,
                change24hPercent = quote.change24hPercent,
                marketCap = quote.marketCap
            )
        }
    }

    private fun inferFamily(chainIndex: String, address: String): ChainFamily {
        OnchainChainRegistry.find(chainIndex)?.let { return it.family }
        return when {
            chainIndex == SOLANA_CHAIN_INDEX -> ChainFamily.SOL
            EVM_ADDRESS.matches(address) -> ChainFamily.EVM
            else -> ChainFamily.OTHER
        }
    }

    private fun okxKey(chainIndex: String, address: String, family: ChainFamily?): String =
        "$chainIndex:${normalizeOnchainAddress(family, address)}"

    private companion object {
        const val SOLANA_CHAIN_INDEX = "501"
        val EVM_ADDRESS = Regex("^0x[0-9a-fA-F]{40}$")
    }
}

private const val DEX_MAX_TOKEN_ADDRESSES = 30
