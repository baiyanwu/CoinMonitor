package io.baiyanwu.coinmonitor.data.wallet

import wallet.core.jni.Curve
import wallet.core.jni.EthereumMessageSigner
import wallet.core.jni.Hash
import wallet.core.jni.PrivateKey

internal class DappMessageSigner {
    init {
        NativeWalletCore.ensureLoaded()
    }

    fun signPersonalMessage(message: String, privateKey: ByteArray): String = withKey(privateKey) { key ->
        EthereumMessageSigner.signMessage(key, message).normalizedSignature()
    }

    fun signTypedData(rawJson: String, privateKey: ByteArray): String = withKey(privateKey) { key ->
        EthereumMessageSigner.signTypedMessage(key, rawJson).normalizedSignature()
    }

    fun signRawMessage(data: ByteArray, privateKey: ByteArray): String = withKey(privateKey) { key ->
        val digest = if (data.size == HASH_SIZE) data else Hash.keccak256(data)
        val signature = key.sign(digest, Curve.SECP256K1)
        require(signature.size == SIGNATURE_SIZE) { "Wallet Core 返回了无效的 EVM 签名。" }
        if ((signature.last().toInt() and 0xff) < LEGACY_V_BASE) {
            signature[signature.lastIndex] = (signature.last().toInt() + LEGACY_V_BASE).toByte()
        }
        "0x${signature.toHex()}"
    }

    private inline fun <T> withKey(bytes: ByteArray, block: (PrivateKey) -> T): T {
        require(bytes.size == PRIVATE_KEY_SIZE) { "EVM 私钥长度无效。" }
        return block(PrivateKey(bytes))
    }

    private fun String.normalizedSignature(): String {
        val clean = removePrefix("0x").removePrefix("0X")
        require(clean.length == SIGNATURE_SIZE * 2 && clean.all { it.digitToIntOrNull(16) != null }) {
            "Wallet Core 返回了无效的 EVM 签名。"
        }
        return "0x${clean.lowercase()}"
    }

    private companion object {
        const val PRIVATE_KEY_SIZE = 32
        const val HASH_SIZE = 32
        const val SIGNATURE_SIZE = 65
        const val LEGACY_V_BASE = 27
    }
}
