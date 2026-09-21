package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.WalletRpcClient
import io.baiyanwu.coinmonitor.data.wallet.EvmContractTransaction
import io.baiyanwu.coinmonitor.data.wallet.WalletTransactionSigner
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.repository.WalletNetworkSettingsRepository
import java.math.BigInteger
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient

class DappBrowserRepository(
    private val networkSettings: WalletNetworkSettingsRepository,
    httpClient: OkHttpClient
) {
    private val rpc = WalletRpcClient(httpClient)
    private val signer by lazy(::WalletTransactionSigner)

    suspend fun rpcCall(network: WalletNetwork, method: String, params: JsonArray): JsonElement {
        val rpcUrl = rpcUrl(network)
        return rpc.call(rpcUrl, method, params)
    }

    suspend fun prepareTransaction(
        network: WalletNetwork,
        expectedAddress: String,
        params: JsonObject
    ): DappEvmTransaction {
        val chainId = requireNotNull(network.chainId) { "当前网络不是 EVM 网络。" }
        val expectedChainId = BigInteger.valueOf(chainId)
        val requestedChainId = params.string("chainId")?.hexQuantity("chainId")
        require(requestedChainId == null || requestedChainId == expectedChainId) {
            "交易 Chain ID 与当前网络不一致。"
        }
        val from = params.string("from") ?: error("交易缺少 from 地址。")
        require(from.equals(expectedAddress, ignoreCase = true)) { "交易发送地址不是当前活动钱包。" }
        val to = params.string("to") ?: error("暂不支持创建合约交易。")
        require(EVM_ADDRESS.matches(to)) { "交易目标地址无效。" }
        val data = normalizeHexData(params.string("data") ?: params.string("input") ?: "0x")
        require(data.length <= MAX_CALL_DATA_HEX_LENGTH) { "交易调用数据过大。" }
        val value = (params.string("value") ?: "0x0").hexQuantity("value")
        val type = params.string("type")?.hexQuantity("type")
        require(type == null || type == BigInteger.ZERO || type == EIP_1559_TYPE) {
            "暂不支持该 EVM 交易类型。"
        }
        require(params["accessList"] == null || params["accessList"] == JsonArray(emptyList())) {
            "暂不支持 Access List 交易。"
        }

        val url = rpcUrl(network)
        val nodeNonce = rpc.evmNonce(url, from).hexQuantity("nonce")
        val suppliedNonce = params.string("nonce")?.hexQuantity("nonce")
        require(suppliedNonce == null || suppliedNonce == nodeNonce) {
            "网页提供的 nonce 与节点 pending nonce 不一致。"
        }
        val nonce = suppliedNonce ?: nodeNonce
        val estimatedGas = rpc.evmEstimateGas(
            url = url,
            from = from,
            to = to,
            value = value.toHexQuantity(),
            data = data.takeUnless { it == "0x" }
        ).hexQuantity("gas")
        val suppliedGas = (params.string("gas") ?: params.string("gasLimit"))?.hexQuantity("gas")
        require(suppliedGas == null || suppliedGas >= estimatedGas) { "网页提供的 Gas Limit 低于节点估算值。" }
        val gasLimit = suppliedGas ?: estimatedGas

        val maxFee = params.string("maxFeePerGas")?.hexQuantity("maxFeePerGas")
        val priorityFee = params.string("maxPriorityFeePerGas")?.hexQuantity("maxPriorityFeePerGas")
        val useEip1559 = type == EIP_1559_TYPE || maxFee != null || priorityFee != null
        val gasPrice = if (useEip1559) null else {
            params.string("gasPrice")?.hexQuantity("gasPrice")
                ?: rpc.evmGasPrice(url).hexQuantity("gasPrice")
        }
        if (useEip1559) {
            require(maxFee != null && priorityFee != null) { "EIP-1559 交易缺少费用参数。" }
            require(maxFee >= priorityFee) { "maxFeePerGas 不能低于优先费。" }
        }

        val transaction = EvmContractTransaction(
            chainId = expectedChainId,
            from = from,
            to = to,
            value = value,
            data = data,
            nonce = nonce,
            gasLimit = gasLimit,
            gasPrice = gasPrice,
            maxFeePerGas = maxFee,
            maxPriorityFeePerGas = priorityFee
        )
        val balance = rpc.evmBalance(url, from).hexQuantity("balance")
        require(balance >= value + transaction.maximumFee) {
            "${network.symbol} 余额不足以支付交易金额和最大网络费。"
        }
        return DappEvmTransaction(network, transaction)
    }

    suspend fun signAndBroadcast(transaction: DappEvmTransaction, privateKey: ByteArray): String {
        val raw = signer.signEvmContract(transaction.signingData, privateKey)
        return rpc.evmSendRawTransaction(rpcUrl(transaction.network), raw)
    }

    private fun rpcUrl(network: WalletNetwork): String = networkSettings.get().rpcUrl(network)
        ?: error("${network.displayName} 尚未配置 RPC。")

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun String.hexQuantity(field: String): BigInteger {
        val clean = removePrefix("0x").removePrefix("0X")
        require(clean.isNotBlank() && clean.all { it.digitToIntOrNull(16) != null }) {
            "$field 不是有效的十六进制数量。"
        }
        return BigInteger(clean, 16)
    }

    private fun normalizeHexData(value: String): String {
        val clean = value.removePrefix("0x").removePrefix("0X")
        require(clean.length % 2 == 0 && clean.all { it.digitToIntOrNull(16) != null }) {
            "交易 data 不是有效的十六进制数据。"
        }
        return "0x${clean.lowercase()}"
    }

    private fun BigInteger.toHexQuantity(): String = "0x${toString(16)}"

    private companion object {
        const val MAX_CALL_DATA_HEX_LENGTH = 262_146
        val EIP_1559_TYPE: BigInteger = BigInteger.valueOf(2)
        val EVM_ADDRESS = Regex("^0x[0-9a-fA-F]{40}$")
    }
}

class DappEvmTransaction internal constructor(
    val network: WalletNetwork,
    internal val signingData: EvmContractTransaction
) {
    val from: String get() = signingData.from
    val to: String get() = signingData.to
    val valueAtomic: BigInteger get() = signingData.value
    val gasLimit: BigInteger get() = signingData.gasLimit
    val maximumFeeAtomic: BigInteger get() = signingData.maximumFee
    val data: String get() = signingData.data
}
