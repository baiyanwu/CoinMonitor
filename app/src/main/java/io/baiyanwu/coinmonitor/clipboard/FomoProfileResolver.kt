package io.baiyanwu.coinmonitor.clipboard

import io.baiyanwu.coinmonitor.domain.model.WalletAddressKind
import io.baiyanwu.coinmonitor.domain.model.WalletAddressParser
import java.io.IOException
import java.math.BigInteger
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.coroutineContext

internal data class FomoProfileLink(val handle: String) {
    val url: String get() = "https://fomo.family/profile/$handle"
    companion object {
        private val pattern = Regex("(?<![A-Za-z0-9_./@:-])(?:https?://)?(?:www\\.)?fomo\\.family/profile/[^\\s<>\"'\\[\\]()]+", RegexOption.IGNORE_CASE)
        fun extractAll(text: String): List<FomoProfileLink> {
            if (text.toByteArray().size > 8192) return emptyList()
            return pattern.findAll(text).mapNotNull { match ->
                val raw = match.value.trimEnd(',', ';', '，', '。', '；')
                val uri = runCatching { URI(if (raw.startsWith("http", true)) raw else "https://$raw") }.getOrNull() ?: return@mapNotNull null
                if (uri.scheme.lowercase() !in setOf("http", "https") || uri.host?.lowercase() !in setOf("fomo.family", "www.fomo.family") || uri.rawUserInfo != null || uri.port != -1) return@mapNotNull null
                val path = uri.path.removeSuffix("/").split('/')
                if (path.size != 3 || path[0].isNotEmpty() || path[1] != "profile" || !path[2].matches(Regex("[A-Za-z0-9_.-]{1,64}"))) return@mapNotNull null
                FomoProfileLink(path[2])
            }.distinctBy { it.handle.lowercase() }.toList()
        }
        fun extract(text: String): FomoProfileLink? = extractAll(text).singleOrNull()
    }
}

internal data class FomoWalletEvidence(val address: String, val networkId: Int, val transactionId: String)
internal data class FomoProfileWallets(val handle: String, val name: String, val solana: FomoWalletEvidence?, val evm: FomoWalletEvidence?, val avatarUrl: String?)
internal enum class FomoFailure { NOT_FOUND, NO_EVIDENCE, UNAVAILABLE }
internal class FomoProfileException(val reason: FomoFailure) : IOException()
internal fun interface FomoProfileDataSource { suspend fun resolve(link: FomoProfileLink): FomoProfileWallets }

/** Public trade index + successful chain transactions; never trusts cached `wallets` fields. */
internal class FomoProfileResolver(
    private val transport: suspend (Request) -> JsonObject = FomoHttpTransport()::json
) : FomoProfileDataSource {
    override suspend fun resolve(link: FomoProfileLink): FomoProfileWallets = withContext(Dispatchers.Default) {
        val url = "https://985monitor.xyz/api/fomo-watch/profile".toHttpUrl().newBuilder()
            .addQueryParameter("handle", link.handle).addQueryParameter("limit", "15").build()
        val indexed = transport(Request.Builder().url(url).build())
        val profile = indexed.obj("profile")
        if (indexed["ok"]?.jsonPrimitive?.booleanOrNull != true || profile == null || !profile.string("handle").equals(link.handle, true)) throw FomoProfileException(FomoFailure.NOT_FOUND)
        val trades = indexed.rows("trades").take(15).mapNotNull(Trade::parse)
        val sol = async { family(trades.filter { it.network == SOLANA_NETWORK }, true) }
        val evm = async { family(trades.filter { it.network in EVM_RPC }, false) }
        val result = FomoProfileWallets(profile.string("handle")!!, profile.string("name")?.takeIf { it.isNotBlank() } ?: link.handle,
            sol.await().evidence, evm.await().evidence, avatarUrl(profile.string("avatar")))
        coroutineContext.ensureActive()
        if (result.solana == null && result.evm == null) throw FomoProfileException(if (sol.await().unavailable || evm.await().unavailable) FomoFailure.UNAVAILABLE else FomoFailure.NO_EVIDENCE)
        result
    }

    private data class FamilyResult(val evidence: FomoWalletEvidence?, val unavailable: Boolean)
    private suspend fun family(trades: List<Trade>, isSolana: Boolean): FamilyResult {
        var evidence: FomoWalletEvidence? = null
        var unavailable = false
        for (trade in trades.distinctBy { it.hash }.take(2)) {
            coroutineContext.ensureActive()
            val next = try { if (isSolana) solana(trade) else evm(trade) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { unavailable = true; continue }
            if (next != null) {
                if (evidence != null && evidence.address != next.address) return FamilyResult(null, unavailable)
                evidence = next
            }
        }
        return FamilyResult(evidence, unavailable)
    }

    private suspend fun solana(trade: Trade): FomoWalletEvidence? {
        val result = rpc(SOLANA_RPC, "getTransaction", buildJsonArray {
            add(trade.hash); add(buildJsonObject { put("encoding", "jsonParsed"); put("maxSupportedTransactionVersion", 1); put("commitment", "confirmed") })
        }) as? JsonObject ?: return null
        if (!trade.timeMatches(result.string("blockTime")?.toDoubleOrNull()) || trade.hash !in result.obj("transaction")?.rows("signatures").orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }) return null
        val address = solanaCandidates(result, trade.token, trade.buy).singleOrNull() ?: return null
        val hint = trade.key?.substringAfterLast(':')
        if (hint != null && WalletAddressParser.classify(hint) == WalletAddressKind.SOLANA && hint != address) return null
        return FomoWalletEvidence(address, trade.network, trade.hash)
    }

    private suspend fun evm(trade: Trade): FomoWalletEvidence? {
        val endpoint = EVM_RPC[trade.network] ?: return null
        val receipt = rpc(endpoint, "eth_getTransactionReceipt", params(trade.hash)) as? JsonObject ?: return null
        if (receipt.string("status") != "0x1" || !receipt.string("transactionHash").equals(trade.hash, true)) return null
        val blockNumber = receipt.string("blockNumber") ?: return null
        val block = rpc(endpoint, "eth_getBlockByNumber", buildJsonArray { add(blockNumber); add(false) }) as? JsonObject ?: return null
        val time = block.string("timestamp")?.removePrefix("0x")?.toLongOrNull(16)?.toDouble()
        if (!trade.timeMatches(time)) return null
        val candidates = evmCandidates(receipt, trade.token, trade.buy)
        if (candidates.size > 4) return null
        val matched = mutableListOf<String>()
        for (address in candidates.sorted()) {
            val code = rpc(endpoint, "eth_getCode", params(address, "latest"))?.jsonPrimitive?.contentOrNull
            if (code.equals(FOMO_DELEGATE, true)) matched += address
        }
        return matched.singleOrNull()?.let { FomoWalletEvidence(it, trade.network, trade.hash) }
    }

    private suspend fun rpc(endpoint: String, method: String, parameters: JsonArray): JsonElement? {
        val body = buildJsonObject { put("jsonrpc", "2.0"); put("id", 1); put("method", method); put("params", parameters) }
        val response = transport(Request.Builder().url(endpoint).post(body.toString().toRequestBody("application/json".toMediaType())).build())
        if (response["error"] != null && response["error"] != JsonNull) throw FomoProfileException(FomoFailure.UNAVAILABLE)
        return response["result"]?.takeUnless { it == JsonNull }
    }

    internal data class Trade(val token: String, val network: Int, val hash: String, val buy: Boolean, val milliseconds: Double, val key: String?) {
        fun timeMatches(seconds: Double?) = seconds != null && seconds.isFinite() && kotlin.math.abs(seconds - milliseconds / 1000) <= 60
        companion object {
            fun parse(element: JsonElement): Trade? {
                val row = element as? JsonObject ?: return null
                val network = row.string("networkId")?.toIntOrNull() ?: return null
                val token = row.string("tokenAddress") ?: return null
                val hash = row.string("txHash") ?: return null
                val side = row.string("side")?.takeIf { it in setOf("BUY", "SELL") } ?: return null
                val ts = row.string("ts")?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
                if (network == SOLANA_NETWORK) {
                    if (WalletAddressParser.classify(token) != WalletAddressKind.SOLANA || !hash.matches(Regex("[1-9A-HJ-NP-Za-km-z]{64,88}"))) return null
                } else if (WalletAddressParser.classify(token) != WalletAddressKind.EVM || !hash.matches(Regex("0x[0-9a-fA-F]{64}"))) return null
                return Trade(token, network, hash, side == "BUY", ts, row.string("key"))
            }
        }
    }

    companion object {
        const val SOLANA_NETWORK = 1399811149
        private const val SOLANA_RPC = "https://solana-rpc.publicnode.com"
        private const val FOMO_DELEGATE = "0xef0100e6cae83bde06e4c305530e199d7217f42808555b"
        private const val TRANSFER = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
        private val EVM_RPC = mapOf(1 to "https://ethereum-rpc.publicnode.com", 56 to "https://bsc-dataseed.bnbchain.org",
            8453 to "https://mainnet.base.org", 4663 to "https://rpc.mainnet.chain.robinhood.com", 143 to "https://rpc.monad.xyz")
        private fun params(vararg strings: String) = buildJsonArray { strings.forEach { add(it) } }

        fun avatarUrl(value: String?): String? = runCatching {
            if (value.isNullOrBlank() || value.toByteArray().size > 2048) return null
            val uri = URI("https://985monitor.xyz/").resolve(value)
            uri.toString().takeIf { uri.scheme == "https" && uri.rawUserInfo == null && uri.port in setOf(-1, 443) && uri.host?.lowercase() in setOf("985monitor.xyz", "www.985monitor.xyz", "prod-fomo-profile-pics.s3.amazonaws.com") }
        }.getOrNull()

        fun evmCandidates(receipt: JsonObject, token: String, buy: Boolean): Set<String> {
            val net = mutableMapOf<String, BigInteger>()
            for (row in receipt.rows("logs")) {
                val log = row as? JsonObject ?: continue
                if (!log.string("address").equals(token, true)) continue
                val topics = log.rows("topics").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                if (topics.size != 3 || !topics[0].equals(TRANSFER, true) || topics.drop(1).any { !it.matches(Regex("0x0{24}[0-9a-fA-F]{40}")) }) continue
                val raw = log.string("data")?.takeIf { it.matches(Regex("0x[0-9a-fA-F]{1,64}")) } ?: throw FomoProfileException(FomoFailure.UNAVAILABLE)
                val amount = BigInteger(raw.drop(2), 16)
                val from = "0x" + topics[1].takeLast(40).lowercase(); val to = "0x" + topics[2].takeLast(40).lowercase()
                net[from] = (net[from] ?: BigInteger.ZERO) - amount
                net[to] = (net[to] ?: BigInteger.ZERO) + amount
            }
            return net.filter { (address, amount) -> address != "0x" + "0".repeat(40) && !address.equals(token, true) && if (buy) amount.signum() > 0 else amount.signum() < 0 }.keys
        }

        fun solanaCandidates(transaction: JsonObject, token: String, buy: Boolean): Set<String> {
            val meta = transaction.obj("meta") ?: return emptySet()
            if (!meta.containsKey("err") || meta["err"] != JsonNull) return emptySet()
            val net = mutableMapOf<String, BigInteger>()
            for ((field, direction) in listOf("preTokenBalances" to -1, "postTokenBalances" to 1)) {
                if (meta[field] !is JsonArray) throw FomoProfileException(FomoFailure.UNAVAILABLE)
                for (element in meta.rows(field)) {
                    val balance = element as? JsonObject ?: continue
                    if (balance.string("mint") != token) continue
                    val owner = balance.string("owner") ?: continue
                    val raw = balance.obj("uiTokenAmount")?.string("amount")?.takeIf { it.matches(Regex("[0-9]{1,78}")) } ?: throw FomoProfileException(FomoFailure.UNAVAILABLE)
                    net[owner] = (net[owner] ?: BigInteger.ZERO) + BigInteger(raw) * BigInteger.valueOf(direction.toLong())
                }
            }
            return transaction.obj("transaction")?.obj("message")?.rows("accountKeys").orEmpty().mapNotNull { element ->
                val account = element as? JsonObject ?: return@mapNotNull null
                val address = account.string("pubkey") ?: return@mapNotNull null
                val amount = net[address] ?: BigInteger.ZERO
                address.takeIf { account["signer"]?.jsonPrimitive?.booleanOrNull == true && WalletAddressParser.classify(it) == WalletAddressKind.SOLANA && if (buy) amount.signum() > 0 else amount.signum() < 0 }
            }.toSet()
        }
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.obj(key: String) = this[key] as? JsonObject
private fun JsonObject.rows(key: String) = (this[key] as? JsonArray)?.toList().orEmpty()

/** Dedicated, credential-free client: no network logger, cookies, redirects or disk cache. */
internal class FomoHttpTransport {
    private val client = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    suspend fun json(request: Request): JsonObject = Json.parseToJsonElement(bytes(request, 2_097_152).decodeToString()) as? JsonObject
        ?: throw FomoProfileException(FomoFailure.UNAVAILABLE)
    suspend fun avatar(url: String): ByteArray {
        require(FomoProfileResolver.avatarUrl(url) == url)
        return bytes(Request.Builder().url(url).header("Accept", "image/*").build(), 1_048_576, image = true)
    }
    private suspend fun bytes(request: Request, limit: Long, image: Boolean = false): ByteArray = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        val body = it.body ?: throw IOException()
                        if (it.code != 200 || body.contentLength() > limit || image && body.contentType()?.type != "image") throw IOException()
                        val source = body.source()
                        if (source.request(limit + 1) && source.buffer.size > limit) throw IOException()
                        source.readByteArray().takeIf { bytes -> bytes.isNotEmpty() && bytes.size <= limit } ?: throw IOException()
                    }
                    if (continuation.isActive) continuation.resume(bytes)
                } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
            }
        })
    }
}
