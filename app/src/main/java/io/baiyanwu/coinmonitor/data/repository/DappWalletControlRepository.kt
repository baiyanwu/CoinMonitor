package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.domain.model.WalletCustomToken
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.repository.WalletCustomTokenRepository
import io.baiyanwu.coinmonitor.domain.repository.WalletNetworkSettingsRepository
import java.math.BigInteger
import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

class DappWalletControlRepository(
    private val networkSettings: WalletNetworkSettingsRepository,
    private val customTokens: WalletCustomTokenRepository
) {
    fun prepareAddChain(params: JsonObject): DappWalletControlRequest.AddChain {
        val chainIdValue = params.string("chainId") ?: error("网络请求缺少 Chain ID。")
        val chainId = chainIdValue.hexQuantity("chainId")
        require(chainId > BigInteger.ZERO && chainId <= LONG_MAX) { "Chain ID 超出支持范围。" }
        val name = params.string("chainName")?.trim()?.takeIf(String::isNotBlank)
            ?: error("网络请求缺少名称。")
        require(name.length <= MAX_NETWORK_NAME) { "网络名称过长。" }
        val currency = params["nativeCurrency"] as? JsonObject ?: error("网络请求缺少原生币信息。")
        val symbol = currency.string("symbol")?.trim()?.uppercase()?.takeIf(String::isNotBlank)
            ?: error("网络请求缺少原生币符号。")
        require(symbol.length <= MAX_SYMBOL_LENGTH) { "原生币符号过长。" }
        val decimals = (currency["decimals"] as? JsonPrimitive)?.intOrNull
            ?: error("网络请求缺少原生币精度。")
        require(decimals == EVM_NATIVE_DECIMALS) { "当前仅支持 18 位精度的 EVM 原生币。" }
        val rpcUrl = (params["rpcUrls"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.firstOrNull(::isSafeHttpsUrl)
            ?: error("网络请求必须提供安全的 HTTPS RPC。")
        val explorerUrl = (params["blockExplorerUrls"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.firstOrNull(::isSafeHttpsUrl)
            .orEmpty()
        return DappWalletControlRequest.AddChain(
            chainId = chainId.toLong(),
            name = name,
            symbol = symbol,
            rpcUrl = rpcUrl.trimEnd('/'),
            explorerUrl = explorerUrl.trimEnd('/')
        )
    }

    fun prepareWatchAsset(
        walletId: String,
        network: WalletNetwork,
        params: JsonObject
    ): DappWalletControlRequest.WatchAsset {
        require(network.isEvm) { "当前网络不是 EVM 网络。" }
        require(params.string("type")?.equals("ERC20", ignoreCase = true) == true) {
            "当前仅支持 ERC20/BEP20 Token。"
        }
        val contract = params.string("contract") ?: error("Token 请求缺少合约地址。")
        require(EVM_ADDRESS.matches(contract)) { "Token 合约地址无效。" }
        val symbol = params.string("symbol")?.trim()?.takeIf(String::isNotBlank)
            ?: error("Token 请求缺少符号。")
        require(symbol.length <= MAX_SYMBOL_LENGTH) { "Token 符号过长。" }
        val decimals = (params["decimals"] as? JsonPrimitive)?.intOrNull
            ?: error("Token 请求缺少精度。")
        require(decimals in 0..MAX_TOKEN_DECIMALS) { "Token 精度超出支持范围。" }
        return DappWalletControlRequest.WatchAsset(
            walletId = walletId,
            network = network,
            contractAddress = contract.lowercase(),
            symbol = symbol,
            decimals = decimals
        )
    }

    suspend fun addChain(request: DappWalletControlRequest.AddChain): WalletNetwork {
        val existing = networkSettings.get().network("eip155:${request.chainId}")
        if (existing != null) {
            networkSettings.validateRpc(existing, request.rpcUrl).getOrThrow()
            networkSettings.saveCustomRpc(existing, request.rpcUrl)
            networkSettings.setNetworkEnabled(existing.id, true)
            return requireNotNull(networkSettings.get().network(existing.id))
        }
        return networkSettings.addCustomEvmNetwork(
            name = request.name,
            chainId = request.chainId,
            symbol = request.symbol,
            explorerUrl = request.explorerUrl,
            rpcUrl = request.rpcUrl
        )
    }

    suspend fun watchAsset(request: DappWalletControlRequest.WatchAsset) {
        customTokens.add(
            WalletCustomToken(
                walletId = request.walletId,
                networkId = request.network.id,
                contractAddress = request.contractAddress,
                symbol = request.symbol,
                decimals = request.decimals
            )
        )
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun String.hexQuantity(field: String): BigInteger {
        require(startsWith("0x", ignoreCase = true)) { "$field 必须使用 0x 十六进制格式。" }
        val clean = drop(2)
        require(clean.isNotBlank() && clean.all { it.digitToIntOrNull(16) != null }) { "$field 无效。" }
        return BigInteger(clean, 16)
    }

    private fun isSafeHttpsUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)

    private companion object {
        val LONG_MAX: BigInteger = BigInteger.valueOf(Long.MAX_VALUE)
        val EVM_ADDRESS = Regex("^0x[0-9a-fA-F]{40}$")
        const val EVM_NATIVE_DECIMALS = 18
        const val MAX_TOKEN_DECIMALS = 255
        const val MAX_SYMBOL_LENGTH = 16
        const val MAX_NETWORK_NAME = 80
    }
}

sealed interface DappWalletControlRequest {
    data class AddChain(
        val chainId: Long,
        val name: String,
        val symbol: String,
        val rpcUrl: String,
        val explorerUrl: String
    ) : DappWalletControlRequest

    data class WatchAsset(
        val walletId: String,
        val network: WalletNetwork,
        val contractAddress: String,
        val symbol: String,
        val decimals: Int
    ) : DappWalletControlRequest
}
