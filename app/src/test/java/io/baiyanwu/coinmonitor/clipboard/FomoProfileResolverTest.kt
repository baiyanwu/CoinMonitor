package io.baiyanwu.coinmonitor.clipboard

import io.baiyanwu.coinmonitor.domain.model.WalletAddressKind
import io.baiyanwu.coinmonitor.domain.model.WalletAddressParser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class FomoProfileResolverTest {
    private val owner = "0x" + "1".repeat(40)
    private val router = "0x" + "2".repeat(40)
    private val hash = "0x" + "a".repeat(64)
    private val transfer = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
    private fun obj(value: String) = Json.parseToJsonElement(value).jsonObject
    private fun topic(address: String) = "0x" + "0".repeat(24) + address.drop(2)
    private fun log(from: String, to: String, amount: Int) = """{"address":"$CA","topics":["$transfer","${topic(from)}","${topic(to)}"],"data":"0x${amount.toString(16)}"}"""
    private fun receipt(status: String = "0x1") = obj("""{"status":"$status","transactionHash":"$hash","blockNumber":"0x1","logs":[${log("0x" + "0".repeat(40), router, 100)},${log(router, owner, 100)}]}""")
    private fun profile(handle: String = "example") = obj("""{"ok":true,"profile":{"handle":"$handle","name":"Example"},"wallets":{"evm":"0xFAKE"},"trades":[{"side":"BUY","tokenAddress":"$CA","networkId":1,"ts":100000,"txHash":"$hash"}]}""")
    private suspend fun reason(block: suspend () -> Unit): FomoFailure {
        try { block(); fail("Expected unavailable or unconfirmed") } catch (e: FomoProfileException) { return e.reason }
        error("unreachable")
    }

    @Test fun acceptsOnlyActualProfileHostsAndUniqueHandles() {
        assertEquals("example", FomoProfileLink.extract("Copied: https://fomo.family/profile/example?x=1")?.handle)
        for (url in listOf("https://fomo.family.evil.com/profile/example", "https://evil.com/fomo.family/profile/example", "https://fomo.family@evil.com/profile/example", "https://fomo.family:443/profile/example", "https://fomo.family/profile/example/extra", "@example")) assertNull(url, FomoProfileLink.extract(url))
        assertNull(FomoProfileLink.extract("https://fomo.family/profile/one https://fomo.family/profile/two"))
        assertEquals(2, FomoProfileLink.extractAll("https://fomo.family/profile/one https://fomo.family/profile/two").size)
    }

    @Test fun ignoresCachedWalletsAndRejectsWrongProfileIdentity() = runBlocking {
        val cacheOnly = FomoProfileResolver { obj("""{"ok":true,"profile":{"handle":"example"},"wallets":{"solana":"So11111111111111111111111111111111111111112"}}""") }
        assertEquals(FomoFailure.NO_EVIDENCE, reason { cacheOnly.resolve(FomoProfileLink("example")) })
        val wrong = FomoProfileResolver { profile("other") }
        assertEquals(FomoFailure.NOT_FOUND, reason { wrong.resolve(FomoProfileLink("example")) })
    }

    @Test fun netFlowsExcludeRouterAndOnlyObservedDelegateIsAccepted() = runBlocking {
        assertEquals(setOf(owner), FomoProfileResolver.evmCandidates(receipt(), CA, true))
        var validDelegate = true
        var blockTime = "0x64"
        var status = "0x1"
        val resolver = FomoProfileResolver { request ->
            if (request.method == "GET") profile() else {
                val buffer = Buffer(); request.body!!.writeTo(buffer)
                when (obj(buffer.readUtf8())["method"]!!.jsonPrimitive.content) {
                    "eth_getTransactionReceipt" -> buildJsonObject { put("result", receipt(status)) }
                    "eth_getBlockByNumber" -> obj("""{"result":{"timestamp":"$blockTime"}}""")
                    "eth_getCode" -> obj("""{"result":"${if (validDelegate) "0xef0100e6cae83bde06e4c305530e199d7217f42808555b" else "0x"}"}""")
                    else -> error("Unexpected request")
                }
            }
        }
        assertEquals(owner, resolver.resolve(FomoProfileLink("example")).evm?.address)
        validDelegate = false; assertEquals(FomoFailure.NO_EVIDENCE, reason { resolver.resolve(FomoProfileLink("example")) })
        validDelegate = true; blockTime = "0x1000"; assertEquals(FomoFailure.NO_EVIDENCE, reason { resolver.resolve(FomoProfileLink("example")) })
        blockTime = "0x64"; status = "0x0"; assertEquals(FomoFailure.NO_EVIDENCE, reason { resolver.resolve(FomoProfileLink("example")) })
    }

    @Test fun rpcTransportFailureIsNotReportedAsNoWallet() = runBlocking {
        val resolver = FomoProfileResolver { request -> if (request.method == "GET") profile() else error("offline") }
        assertEquals(FomoFailure.UNAVAILABLE, reason { resolver.resolve(FomoProfileLink("example")) })
    }

    @Test fun solanaRequiresSuccessfulTransactionAndOwnerSignatureWithExactRawAmounts() {
        val solOwner = "7UBjMaYsE3tKcanfgGrtso6aHAMhVNSVHkkX5j3t7f5y"
        val token = "So11111111111111111111111111111111111111112"
        fun transaction(signer: Boolean, error: String = "null") = obj("""{"meta":{"err":$error,"preTokenBalances":[{"mint":"$token","owner":"$solOwner","uiTokenAmount":{"amount":"9007199254740993123456789"}}],"postTokenBalances":[{"mint":"$token","owner":"$solOwner","uiTokenAmount":{"amount":"9007199254740993123456790"}}]},"transaction":{"message":{"accountKeys":[{"pubkey":"$solOwner","signer":$signer},{"pubkey":"11111111111111111111111111111111","signer":true}]}}}""")
        assertEquals(setOf(solOwner), FomoProfileResolver.solanaCandidates(transaction(true), token, true))
        assertTrue(FomoProfileResolver.solanaCandidates(transaction(false), token, true).isEmpty())
        assertTrue(FomoProfileResolver.solanaCandidates(transaction(true, "{}"), token, true).isEmpty())
        assertTrue(FomoProfileResolver.solanaCandidates(transaction(true), token, false).isEmpty())
    }

    @Test fun avatarHostAllowlistDoesNotAcceptLookalikesOrCredentials() {
        assertEquals("https://985monitor.xyz/a.jpg", FomoProfileResolver.avatarUrl("/a.jpg"))
        assertNull(FomoProfileResolver.avatarUrl("https://985monitor.xyz.evil.com/a.jpg"))
        assertNull(FomoProfileResolver.avatarUrl("https://user:pass@985monitor.xyz/a.jpg"))
        assertNull(FomoProfileResolver.avatarUrl("http://985monitor.xyz/a.jpg"))
    }

    @Test fun livePublicProfileAndChainEvidence(): Unit = runBlocking {
        assumeTrue(System.getenv("COINMONITOR_FOMO_LIVE_TEST") == "1")
        val result = withTimeout(20_000) { FomoProfileResolver().resolve(FomoProfileLink("Tekkerrss")) }
        assertTrue(result.handle.equals("Tekkerrss", ignoreCase = true))
        assertTrue(result.solana != null || result.evm != null)
        println("FoMo live evidence: Solana=${result.solana != null}, EVM=${result.evm != null}")
        result.solana?.let { assertEquals(WalletAddressKind.SOLANA, WalletAddressParser.classify(it.address)); assertTrue(it.transactionId.length >= 64) }
        result.evm?.let { assertEquals(WalletAddressKind.EVM, WalletAddressParser.classify(it.address)); assertTrue(it.transactionId.matches(Regex("0x[0-9a-fA-F]{64}"))) }
    }
}
