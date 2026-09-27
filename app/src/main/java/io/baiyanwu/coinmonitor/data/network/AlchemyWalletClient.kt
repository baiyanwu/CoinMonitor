package io.baiyanwu.coinmonitor.data.network

import io.baiyanwu.coinmonitor.domain.model.WalletActivity
import io.baiyanwu.coinmonitor.domain.model.WalletActivityDirection
import io.baiyanwu.coinmonitor.domain.model.WalletActivityStatus
import io.baiyanwu.coinmonitor.domain.model.SelfCustodyAsset
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.model.WalletProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AlchemyPortfolioResult(
    val assets: List<SelfCustodyAsset>,
    val partialNetworks: Set<WalletNetwork>
)

class AlchemyWalletClient(private val httpClient: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val rpc = WalletRpcClient(httpClient)

    suspend fun loadPortfolio(
        wallet: WalletProfile,
        apiKey: String,
        enabledNetworks: List<WalletNetwork>
    ): AlchemyPortfolioResult {
        val indexedNetworks = enabledNetworks.filter(WalletNetwork::alchemyPortfolioSupported)
        val addressRequests = buildList {
            wallet.evmAddress?.let { address ->
                val networks = indexedNetworks.filter { it.isEvm && wallet.supports(it) }
                    .mapNotNull(WalletNetwork::alchemyNetwork)
                if (networks.isNotEmpty()) add(address to networks)
            }
            wallet.solanaAddress?.takeIf {
                WalletNetwork.SOLANA in indexedNetworks && wallet.supports(WalletNetwork.SOLANA)
            }?.let { address ->
                WalletNetwork.SOLANA.alchemyNetwork?.let { add(address to listOf(it)) }
            }
        }
        if (addressRequests.isEmpty()) return AlchemyPortfolioResult(emptyList(), emptySet())

        val assets = mutableListOf<SelfCustodyAsset>()
        val partial = linkedSetOf<WalletNetwork>()
        var pageKey: String? = null
        var page = 0
        do {
            val body = buildJsonObject {
                put("addresses", buildJsonArray {
                    addressRequests.forEach { (address, networks) ->
                        add(buildJsonObject {
                            put("address", address)
                            put("networks", buildJsonArray { networks.forEach { add(JsonPrimitive(it)) } })
                        })
                    }
                })
                put("withMetadata", true)
                put("withPrices", true)
                put("includeNativeTokens", true)
                put("includeErc20Tokens", true)
                put("includeBlockMetadata", false)
                pageKey?.let { put("pageKey", it) }
            }
            val root = execute(
                url = "https://api.g.alchemy.com/data/v1/$apiKey/assets/tokens/by-address",
                body = body
            )
            val data = root["data"]?.jsonObject ?: JsonObject(emptyMap())
            data["tokens"]?.jsonArray.orEmpty().mapNotNullTo(assets) { parseAsset(it, indexedNetworks) }
            root["error"]?.takeUnless { it is JsonNull }?.jsonObject
                ?.get("partialErrors")?.jsonArray.orEmpty()
                .mapNotNullTo(partial) { error ->
                    val networkId = error.jsonObject["network"]?.jsonPrimitive?.contentOrNull
                        ?: return@mapNotNullTo null
                    indexedNetworks.firstOrNull { it.matchesAlchemyNetwork(networkId) }
                }
            pageKey = data["pageKey"]?.jsonPrimitive?.contentOrNull
            page++
        } while (!pageKey.isNullOrBlank() && page < MAX_PORTFOLIO_PAGES)
        return AlchemyPortfolioResult(
            assets = assets.filter { it.balance.signum() != 0 }.distinctBy(SelfCustodyAsset::id),
            partialNetworks = partial
        )
    }

    suspend fun loadActivities(
        wallet: WalletProfile,
        apiKey: String,
        enabledNetworks: List<WalletNetwork>,
        knownAssets: List<SelfCustodyAsset> = emptyList()
    ): Pair<List<WalletActivity>, Set<WalletNetwork>> = coroutineScope {
        val failures = linkedSetOf<WalletNetwork>()
        val tasks = enabledNetworks.filter { it.alchemyTransfersSupported && wallet.supports(it) }.map { network ->
            async {
                runCatching {
                    if (network.isEvm) loadEvmActivities(wallet, network, apiKey)
                    else loadSolanaActivities(wallet, apiKey, knownAssets)
                }.getOrElse {
                    synchronized(failures) { failures += network }
                    emptyList()
                }
            }
        }
        tasks.awaitAll().flatten().distinctBy { "${it.network}:${it.transactionHash}:${it.direction}" }
            .sortedByDescending(WalletActivity::timestampMillis) to failures
    }

    private suspend fun loadEvmActivities(
        wallet: WalletProfile,
        network: WalletNetwork,
        apiKey: String
    ): List<WalletActivity> {
        val address = wallet.evmAddress ?: return emptyList()
        val url = "https://${requireNotNull(network.alchemyRpcHost)}/v2/$apiKey"
        val incoming = assetTransfers(url, "toAddress", address)
        val outgoing = assetTransfers(url, "fromAddress", address)
        return (incoming.map { parseTransfer(wallet.id, network, it, WalletActivityDirection.INCOMING) } +
            outgoing.map { parseTransfer(wallet.id, network, it, WalletActivityDirection.OUTGOING) })
            .filterNotNull()
    }

    private suspend fun assetTransfers(url: String, addressKey: String, address: String): List<JsonObject> {
        val params = buildJsonArray {
            add(buildJsonObject {
                put("fromBlock", "0x0")
                put("toBlock", "latest")
                put(addressKey, address)
                put("excludeZeroValue", true)
                put("withMetadata", true)
                put("maxCount", "0x32")
                put("category", buildJsonArray {
                    add(JsonPrimitive("external"))
                    add(JsonPrimitive("erc20"))
                })
            })
        }
        return rpc.call(url, "alchemy_getAssetTransfers", params).jsonObject["transfers"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
    }

    private suspend fun loadSolanaActivities(
        wallet: WalletProfile,
        apiKey: String,
        knownAssets: List<SelfCustodyAsset>
    ): List<WalletActivity> = coroutineScope {
        val address = wallet.solanaAddress ?: return@coroutineScope emptyList()
        val network = WalletNetwork.SOLANA
        val url = "https://${requireNotNull(network.alchemyRpcHost)}/v2/$apiKey"
        val symbols = knownAssets.filter { it.network == network && it.tokenAddress != null }
            .associate { it.tokenAddress!! to it.symbol }
        rpc.solanaSignatures(url, address, SOLANA_ACTIVITY_LIMIT).map { item ->
            async {
            val value = item.jsonObject
            val signature = value["signature"]?.jsonPrimitive?.contentOrNull ?: return@async emptyList()
            val failed = value["err"]?.let { it !is JsonNull } == true
            val timestamp = value["blockTime"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.times(1000) ?: 0L
            val transaction = runCatching { rpc.solanaTransaction(url, signature) }.getOrNull()
            parseSolanaChanges(wallet.id, address, signature, timestamp, failed, transaction, symbols)
        }
        }.awaitAll().flatten()
    }

    private fun parseSolanaChanges(
        walletId: String,
        owner: String,
        signature: String,
        timestamp: Long,
        failed: Boolean,
        transaction: JsonObject?,
        symbols: Map<String, String>
    ): List<WalletActivity> {
        val status = if (failed) WalletActivityStatus.FAILED else WalletActivityStatus.CONFIRMED
        val meta = transaction?.get("meta")?.takeUnless { it is JsonNull }?.jsonObject
        val tokenDeltas = tokenBalancesForOwner(meta?.get("postTokenBalances"), owner).toMutableMap()
        tokenBalancesForOwner(meta?.get("preTokenBalances"), owner).forEach { (mint, amount) ->
            tokenDeltas[mint] = (tokenDeltas[mint] ?: BigDecimal.ZERO) - amount
        }
        val tokenActivities = tokenDeltas.filterValues { it.signum() != 0 }.map { (mint, delta) ->
            activityFromDelta(walletId, signature, timestamp, status, symbols[mint] ?: mint.take(6), delta, mint)
        }
        if (tokenActivities.isNotEmpty()) return tokenActivities

        val keys = transaction?.get("transaction")?.jsonObject?.get("message")?.jsonObject
            ?.get("accountKeys")?.jsonArray.orEmpty().mapNotNull { key ->
                if (key is JsonPrimitive) key.contentOrNull else key.jsonObject["pubkey"]?.jsonPrimitive?.contentOrNull
            }
        val ownerIndex = keys.indexOf(owner)
        if (ownerIndex >= 0) {
            val pre = meta?.get("preBalances")?.jsonArray?.getOrNull(ownerIndex)?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
            val post = meta?.get("postBalances")?.jsonArray?.getOrNull(ownerIndex)?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
            if (pre != null && post != null) {
                var delta = post - pre
                if (delta.signum() < 0) {
                    val fee = meta?.get("fee")?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    delta += fee
                }
                if (delta.signum() != 0) {
                    return listOf(activityFromDelta(
                        walletId, signature, timestamp, status, WalletNetwork.SOLANA.symbol,
                        delta.movePointLeft(WalletNetwork.SOLANA.decimals), null
                    ))
                }
            }
        }
        return listOf(WalletActivity(
            id = "${WalletNetwork.SOLANA.name}:$signature",
            walletId = walletId,
            network = WalletNetwork.SOLANA,
            transactionHash = signature,
            direction = WalletActivityDirection.UNKNOWN,
            symbol = WalletNetwork.SOLANA.symbol,
            amount = null,
            counterparty = null,
            timestampMillis = timestamp,
            status = status,
            locallySubmitted = false
        ))
    }

    private fun tokenBalancesForOwner(element: kotlinx.serialization.json.JsonElement?, owner: String): Map<String, BigDecimal> =
        element?.takeUnless { it is JsonNull }?.jsonArray.orEmpty().mapNotNull { entry ->
            val value = entry.jsonObject
            if (value["owner"]?.jsonPrimitive?.contentOrNull != owner) return@mapNotNull null
            val mint = value["mint"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val ui = value["uiTokenAmount"]?.jsonObject ?: return@mapNotNull null
            val raw = ui["amount"]?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull() ?: return@mapNotNull null
            val decimals = ui["decimals"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            mint to raw.movePointLeft(decimals)
        }.groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.fold(BigDecimal.ZERO, BigDecimal::add) }

    private fun activityFromDelta(
        walletId: String,
        signature: String,
        timestamp: Long,
        status: WalletActivityStatus,
        symbol: String,
        delta: BigDecimal,
        discriminator: String?
    ) = WalletActivity(
        id = "${WalletNetwork.SOLANA.name}:$signature:${discriminator ?: "native"}",
        walletId = walletId,
        network = WalletNetwork.SOLANA,
        transactionHash = signature,
        direction = if (delta.signum() > 0) WalletActivityDirection.INCOMING else WalletActivityDirection.OUTGOING,
        symbol = symbol,
        amount = delta.abs().stripTrailingZeros(),
        counterparty = null,
        timestampMillis = timestamp,
        status = status,
        locallySubmitted = false,
        tokenAddress = discriminator
    )

    private fun parseAsset(
        element: kotlinx.serialization.json.JsonElement,
        networks: List<WalletNetwork>
    ): SelfCustodyAsset? {
        val token = element.jsonObject
        val networkId = token["network"]?.jsonPrimitive?.contentOrNull ?: return null
        val network = networks.firstOrNull { it.matchesAlchemyNetwork(networkId) } ?: return null
        val tokenAddress = token["tokenAddress"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
        val metadata = token["tokenMetadata"]?.takeUnless { it is JsonNull }?.jsonObject
        val decimals = metadata?.get("decimals")?.jsonPrimitive?.intOrNull ?: network.decimals
        val rawText = token["tokenBalance"]?.jsonPrimitive?.contentOrNull ?: return null
        val raw = parseAtomic(rawText)
        val balance = BigDecimal(raw).movePointLeft(decimals).stripTrailingZeros()
        val price = token["tokenPrices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("value")?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
        val value = price?.multiply(balance)?.setScale(8, RoundingMode.HALF_UP)?.stripTrailingZeros()
        val symbol = metadata?.get("symbol")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: if (tokenAddress == null) network.symbol else "UNKNOWN"
        val name = metadata?.get("name")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: if (tokenAddress == null) network.displayName else "Unknown Token"
        val verified = metadata?.get("symbol")?.jsonPrimitive?.contentOrNull?.isNotBlank() == true &&
            metadata["name"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true
        return SelfCustodyAsset(
            id = "${network.name}:${tokenAddress ?: "native"}",
            network = network,
            tokenAddress = tokenAddress,
            name = name,
            symbol = symbol,
            decimals = decimals,
            rawBalance = raw.toString(),
            balance = balance,
            priceUsd = price,
            valueUsd = value,
            logoUrl = metadata?.get("logo")?.jsonPrimitive?.contentOrNull,
            verified = verified || tokenAddress == null,
            isNative = tokenAddress == null
        )
    }

    private fun parseTransfer(
        walletId: String,
        network: WalletNetwork,
        transfer: JsonObject,
        direction: WalletActivityDirection
    ): WalletActivity? {
        val hash = transfer["hash"]?.jsonPrimitive?.contentOrNull ?: return null
        val metadata = transfer["metadata"]?.jsonObject
        val timestamp = metadata?.get("blockTimestamp")?.jsonPrimitive?.contentOrNull?.let {
            runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L)
        } ?: 0L
        val symbol = transfer["asset"]?.jsonPrimitive?.contentOrNull ?: network.symbol
        val tokenAddress = transfer["rawContract"]?.takeUnless { it is JsonNull }?.jsonObject
            ?.get("address")?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
        val amount = transfer["value"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
        val counterparty = if (direction == WalletActivityDirection.INCOMING) {
            transfer["from"]?.jsonPrimitive?.contentOrNull
        } else {
            transfer["to"]?.jsonPrimitive?.contentOrNull
        }
        return WalletActivity(
            id = "${network.name}:$hash:$direction:$symbol",
            walletId = walletId,
            network = network,
            transactionHash = hash,
            direction = direction,
            symbol = symbol,
            amount = amount,
            counterparty = counterparty,
            timestampMillis = timestamp,
            status = WalletActivityStatus.CONFIRMED,
            locallySubmitted = false,
            tokenAddress = tokenAddress
        )
    }

    private suspend fun execute(url: String, body: JsonObject): JsonObject {
        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val response = httpClient.newCall(request).await()
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw IOException("Alchemy HTTP ${it.code}")
            return json.parseToJsonElement(text).jsonObject
        }
    }

    private fun parseAtomic(value: String): BigInteger = if (value.startsWith("0x", true)) {
        value.removePrefix("0x").ifBlank { "0" }.let { BigInteger(it, 16) }
    } else {
        value.toBigInteger()
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_PORTFOLIO_PAGES = 10
        const val SOLANA_ACTIVITY_LIMIT = 15
    }
}
