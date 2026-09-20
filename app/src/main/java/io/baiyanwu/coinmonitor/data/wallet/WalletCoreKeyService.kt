package io.baiyanwu.coinmonitor.data.wallet

import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.model.WalletPrivateKeyType
import wallet.core.jni.AnyAddress
import wallet.core.jni.Base58
import wallet.core.jni.CoinType
import wallet.core.jni.HDWallet
import wallet.core.jni.Mnemonic
import wallet.core.jni.PrivateKey

internal data class DerivedWalletKeys(
    val mnemonic: String,
    val evmAddress: String,
    val solanaAddress: String
)

internal data class ImportedPrivateKey(
    val normalizedHex: String,
    val address: String
)

internal class WalletCoreKeyService {
    init {
        NativeWalletCore.ensureLoaded()
    }

    fun createMnemonicWallet(): DerivedWalletKeys = deriveMnemonicWallet(HDWallet(128, "").mnemonic())

    fun deriveMnemonicWallet(rawMnemonic: String): DerivedWalletKeys {
        val mnemonic = rawMnemonic.trim().split(Regex("\\s+")).joinToString(" ")
        require(Mnemonic.isValid(mnemonic)) { "助记词无效，请检查单词和顺序。" }
        val wallet = HDWallet(mnemonic, "")
        return DerivedWalletKeys(
            mnemonic = wallet.mnemonic(),
            evmAddress = wallet.getAddressForCoin(CoinType.ETHEREUM),
            solanaAddress = wallet.getAddressForCoin(CoinType.SOLANA)
        )
    }

    fun importPrivateKey(rawPrivateKey: String, type: WalletPrivateKeyType): ImportedPrivateKey {
        val bytes = decodePrivateKey(rawPrivateKey, type)
        require(bytes.size == 32) { "私钥必须是 32 字节。" }
        val key = PrivateKey(bytes)
        val address = if (type == WalletPrivateKeyType.EVM) {
            CoinType.ETHEREUM.deriveAddress(key)
        } else {
            AnyAddress(key.publicKeyEd25519, CoinType.SOLANA).description()
        }
        return ImportedPrivateKey(bytes.toHex(), address).also { bytes.fill(0) }
    }

    fun privateKeyFromMnemonic(mnemonic: String, network: WalletNetwork): ByteArray {
        val wallet = HDWallet(mnemonic, "")
        return wallet.getKeyForCoin(if (network.isEvm) CoinType.ETHEREUM else CoinType.SOLANA).data()
    }

    fun decodeStoredPrivateKey(hex: String): ByteArray = hex.hexToBytes().also {
        require(it.size == 32) { "已保存的私钥格式无效。" }
    }

    private fun decodePrivateKey(value: String, type: WalletPrivateKeyType): ByteArray {
        val normalized = value.trim()
        val hex = normalized.removePrefix("0x").removePrefix("0X")
        if (hex.matches(Regex("[0-9a-fA-F]{64}|[0-9a-fA-F]{128}"))) {
            return hex.hexToBytes().copyOfRange(0, 32)
        }
        require(type == WalletPrivateKeyType.SOLANA) { "EVM 私钥必须是 32 字节十六进制。" }
        val decoded = Base58.decodeNoCheck(normalized)
        require(decoded.size == 32 || decoded.size == 64) { "Solana 私钥必须是 32/64 字节 Base58 或十六进制。" }
        return decoded.copyOfRange(0, 32)
    }
}

internal object NativeWalletCore {
    @Volatile private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (!loaded) {
                System.loadLibrary("TrustWalletCore")
                loaded = true
            }
        }
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

internal fun String.hexToBytes(): ByteArray {
    val clean = removePrefix("0x").removePrefix("0X")
    require(clean.length % 2 == 0 && clean.matches(Regex("[0-9a-fA-F]+"))) { "十六进制格式无效。" }
    return ByteArray(clean.length / 2) { index ->
        clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
