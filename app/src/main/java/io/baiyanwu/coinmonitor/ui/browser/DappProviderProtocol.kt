package io.baiyanwu.coinmonitor.ui.browser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DappBridgeRequest(
    val id: Long,
    val method: String,
    val params: JsonElement?,
    val origin: String
)

enum class DappSignatureMethod {
    PERSONAL_MESSAGE,
    RAW_MESSAGE,
    TYPED_DATA
}

sealed interface DappProviderAction {
    data object RequestAccounts : DappProviderAction
    data object ForwardRpc : DappProviderAction
    data object SwitchChain : DappProviderAction
    data object AddChain : DappProviderAction
    data object WatchAsset : DappProviderAction
    data object SendTransaction : DappProviderAction
    data class Sign(val method: DappSignatureMethod) : DappProviderAction
    data class Unsupported(val method: String) : DappProviderAction
}

object DappProviderProtocol {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun parse(raw: String, origin: String): DappBridgeRequest {
        val root = json.parseToJsonElement(raw).jsonObject
        return DappBridgeRequest(
            id = root.getValue("id").jsonPrimitive.content.toLong(),
            method = root.getValue("name").jsonPrimitive.content,
            params = root["params"] ?: root["object"],
            origin = origin
        )
    }

    fun route(request: DappBridgeRequest): DappProviderAction = when (request.method) {
        "requestAccounts", "wallet_requestPermissions" -> DappProviderAction.RequestAccounts
        "rpcCall" -> DappProviderAction.ForwardRpc
        "switchEthereumChain" -> DappProviderAction.SwitchChain
        "addEthereumChain" -> DappProviderAction.AddChain
        "watchAsset" -> DappProviderAction.WatchAsset
        "signTransaction" -> DappProviderAction.SendTransaction
        "signPersonalMessage" -> DappProviderAction.Sign(DappSignatureMethod.PERSONAL_MESSAGE)
        "signMessage" -> DappProviderAction.Sign(DappSignatureMethod.RAW_MESSAGE)
        "signTypedMessage" -> DappProviderAction.Sign(DappSignatureMethod.TYPED_DATA)
        else -> DappProviderAction.Unsupported(request.method)
    }
}
