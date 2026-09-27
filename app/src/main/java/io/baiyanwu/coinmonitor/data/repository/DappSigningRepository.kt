package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.wallet.DappMessageSigner
import io.baiyanwu.coinmonitor.data.wallet.hexToBytes
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.ui.browser.DappSignatureMethod
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DappSigningRepository {
    private val signer by lazy(::DappMessageSigner)

    fun prepare(
        method: DappSignatureMethod,
        network: WalletNetwork,
        expectedAddress: String,
        params: JsonObject
    ): DappSignatureRequest = when (method) {
        DappSignatureMethod.PERSONAL_MESSAGE -> preparePersonal(network, expectedAddress, params)
        DappSignatureMethod.RAW_MESSAGE -> prepareRaw(network, expectedAddress, params)
        DappSignatureMethod.TYPED_DATA -> prepareTyped(network, expectedAddress, params)
    }

    fun sign(request: DappSignatureRequest, privateKey: ByteArray): String = when (request) {
        is DappSignatureRequest.PersonalMessage -> signer.signPersonalMessage(request.message, privateKey)
        is DappSignatureRequest.RawMessage -> signer.signRawMessage(request.bytes, privateKey)
        is DappSignatureRequest.TypedData -> signer.signTypedData(request.rawJson, privateKey)
    }

    private fun preparePersonal(
        network: WalletNetwork,
        expectedAddress: String,
        params: JsonObject
    ): DappSignatureRequest.PersonalMessage {
        val address = validatedAddress(params, expectedAddress)
        val bytes = params.requiredHexData()
        require(bytes.isNotEmpty()) { "不允许签署空消息。" }
        require(bytes.size <= MAX_MESSAGE_BYTES) { "待签名消息过大。" }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val message = runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { error("personal_sign 消息不是有效的 UTF-8 文本。") }
        return DappSignatureRequest.PersonalMessage(
            network = network,
            address = address,
            message = message,
            originalMethod = params.string("originalMethod") ?: "personal_sign"
        )
    }

    private fun prepareRaw(
        network: WalletNetwork,
        expectedAddress: String,
        params: JsonObject
    ): DappSignatureRequest.RawMessage {
        val address = validatedAddress(params, expectedAddress)
        val bytes = params.requiredHexData()
        require(bytes.isNotEmpty()) { "不允许签署空数据。" }
        require(bytes.size <= MAX_MESSAGE_BYTES) { "待签名数据过大。" }
        return DappSignatureRequest.RawMessage(
            network = network,
            address = address,
            bytes = bytes,
            dataHex = params.getValue("data").jsonPrimitive.content.lowercase(),
            originalMethod = params.string("originalMethod") ?: "eth_sign"
        )
    }

    private fun prepareTyped(
        network: WalletNetwork,
        expectedAddress: String,
        params: JsonObject
    ): DappSignatureRequest.TypedData {
        val address = validatedAddress(params, expectedAddress)
        val raw = params.string("raw") ?: error("Typed Data 缺少原始 JSON。")
        require(raw.toByteArray().size <= MAX_TYPED_DATA_BYTES) { "Typed Data 过大。" }
        val version = (params.string("version") ?: "V4").uppercase()
        require(version == "V3" || version == "V4") { "仅支持 EIP-712 V3/V4。" }
        val root = runCatching { JSON.parseToJsonElement(raw).jsonObject }
            .getOrElse { error("Typed Data JSON 无效。") }
        val domain = root["domain"] as? JsonObject ?: error("Typed Data 缺少 domain。")
        val types = root["types"] as? JsonObject ?: error("Typed Data 缺少 types。")
        val primaryType = root.string("primaryType")?.takeIf(String::isNotBlank)
            ?: error("Typed Data 缺少 primaryType。")
        require(types["EIP712Domain"] is JsonArray) { "Typed Data 未定义 EIP712Domain。" }
        require(types[primaryType] is JsonArray) { "Typed Data 未定义 primaryType。" }
        require(types.size <= MAX_TYPE_COUNT) { "Typed Data 类型数量过多。" }

        val typedChainId = domain["chainId"]?.jsonPrimitive?.contentOrNull?.parseChainId()
        val expectedChainId = requireNotNull(network.chainId) { "当前网络不是 EVM 网络。" }
        require(typedChainId == null || typedChainId == BigInteger.valueOf(expectedChainId)) {
            "Typed Data Chain ID 与当前网络不一致。"
        }
        val verifyingContract = domain.string("verifyingContract")
        require(verifyingContract == null || EVM_ADDRESS.matches(verifyingContract)) {
            "Typed Data verifyingContract 无效。"
        }
        val message = root["message"] as? JsonObject ?: error("Typed Data 缺少 message。")
        return DappSignatureRequest.TypedData(
            network = network,
            address = address,
            rawJson = raw,
            version = version,
            primaryType = primaryType,
            domainName = domain.string("name"),
            verifyingContract = verifyingContract,
            fields = flattenFields(message),
            originalMethod = params.string("originalMethod") ?: "eth_signTypedData_v4"
        )
    }

    private fun validatedAddress(params: JsonObject, expectedAddress: String): String {
        val address = params.string("address") ?: error("签名请求缺少钱包地址。")
        require(EVM_ADDRESS.matches(address)) { "签名请求的钱包地址无效。" }
        require(address.equals(expectedAddress, ignoreCase = true)) { "签名地址不是当前活动钱包。" }
        return address
    }

    private fun JsonObject.requiredHexData(): ByteArray {
        val data = string("data") ?: error("签名请求缺少 data。")
        require(data.startsWith("0x", ignoreCase = true)) { "签名数据必须使用 0x 十六进制格式。" }
        return data.hexToBytes()
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun String.parseChainId(): BigInteger {
        val value = trim()
        return if (value.startsWith("0x", ignoreCase = true)) {
            val clean = value.drop(2)
            require(clean.isNotBlank() && clean.all { it.digitToIntOrNull(16) != null }) {
                "Typed Data chainId 无效。"
            }
            BigInteger(clean, 16)
        } else {
            require(value.isNotBlank() && value.all(Char::isDigit)) { "Typed Data chainId 无效。" }
            BigInteger(value, 10)
        }
    }

    private fun flattenFields(message: JsonObject): List<DappTypedField> {
        val fields = mutableListOf<DappTypedField>()
        fun visit(prefix: String, value: JsonElement, depth: Int) {
            if (fields.size >= MAX_DISPLAY_FIELDS) return
            when {
                value is JsonObject && depth < MAX_DISPLAY_DEPTH -> value.forEach { (name, nested) ->
                    visit(if (prefix.isBlank()) name else "$prefix.$name", nested, depth + 1)
                }
                else -> fields += DappTypedField(prefix, value.compactValue())
            }
        }
        message.forEach { (name, value) -> visit(name, value, 0) }
        return fields
    }

    private fun JsonElement.compactValue(): String = when (this) {
        is JsonPrimitive -> contentOrNull ?: toString()
        is JsonArray -> "[${size} items]"
        is JsonObject -> "{${size} fields}"
        else -> toString()
    }.let { if (it.length <= MAX_DISPLAY_VALUE) it else "${it.take(MAX_DISPLAY_VALUE)}…" }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = false; explicitNulls = false }
        val EVM_ADDRESS = Regex("^0x[0-9a-fA-F]{40}$")
        const val MAX_MESSAGE_BYTES = 64 * 1024
        const val MAX_TYPED_DATA_BYTES = 256 * 1024
        const val MAX_TYPE_COUNT = 64
        const val MAX_DISPLAY_FIELDS = 10
        const val MAX_DISPLAY_DEPTH = 3
        const val MAX_DISPLAY_VALUE = 120
    }
}

sealed interface DappSignatureRequest {
    val network: WalletNetwork
    val address: String
    val originalMethod: String

    data class PersonalMessage(
        override val network: WalletNetwork,
        override val address: String,
        val message: String,
        override val originalMethod: String
    ) : DappSignatureRequest

    data class RawMessage(
        override val network: WalletNetwork,
        override val address: String,
        internal val bytes: ByteArray,
        val dataHex: String,
        override val originalMethod: String
    ) : DappSignatureRequest

    data class TypedData(
        override val network: WalletNetwork,
        override val address: String,
        internal val rawJson: String,
        val version: String,
        val primaryType: String,
        val domainName: String?,
        val verifyingContract: String?,
        val fields: List<DappTypedField>,
        override val originalMethod: String
    ) : DappSignatureRequest
}

data class DappTypedField(val name: String, val value: String)
