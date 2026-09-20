package io.baiyanwu.coinmonitor.data.wallet

import com.google.protobuf.ByteString
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.repository.WalletTransferEstimate
import wallet.core.java.AnySigner
import wallet.core.jni.CoinType
import wallet.core.jni.SolanaAddress
import wallet.core.jni.proto.Common
import wallet.core.jni.proto.Ethereum
import wallet.core.jni.proto.Solana
import java.math.BigInteger

internal class WalletTransactionSigner {
    init {
        NativeWalletCore.ensureLoaded()
    }

    fun isValidAddress(network: WalletNetwork, address: String): Boolean = wallet.core.jni.AnyAddress.isValid(
        address,
        if (network.isEvm) CoinType.ETHEREUM else CoinType.SOLANA
    )

    fun solanaTokenAddress(owner: String, mint: String): String = SolanaAddress(owner).defaultTokenAddress(mint)

    fun signEvm(
        network: WalletNetwork,
        recipient: String,
        tokenAddress: String?,
        privateKey: ByteArray,
        estimate: WalletTransferEstimate
    ): String {
        val amount = BigInteger(estimate.amountAtomic)
        val builder = Ethereum.SigningInput.newBuilder().apply {
            chainId = ByteString.copyFrom(BigInteger.valueOf(requireNotNull(network.chainId)).unsignedBytes())
            nonce = ByteString.copyFrom(hexQuantityToBigInteger(estimate.nonceOrBlockhash).unsignedBytes())
            gasPrice = ByteString.copyFrom(hexQuantityToBigInteger(requireNotNull(estimate.gasPrice)).unsignedBytes())
            gasLimit = ByteString.copyFrom(hexQuantityToBigInteger(requireNotNull(estimate.gasLimit)).unsignedBytes())
            toAddress = tokenAddress ?: recipient
            this.privateKey = ByteString.copyFrom(privateKey)
            transaction = Ethereum.Transaction.newBuilder().apply {
                if (tokenAddress == null) {
                    transfer = Ethereum.Transaction.Transfer.newBuilder()
                        .setAmount(ByteString.copyFrom(amount.unsignedBytes()))
                        .build()
                } else {
                    erc20Transfer = Ethereum.Transaction.ERC20Transfer.newBuilder()
                        .setTo(recipient)
                        .setAmount(ByteString.copyFrom(amount.unsignedBytes()))
                        .build()
                }
            }.build()
        }
        val output = AnySigner.sign(builder.build(), CoinType.ETHEREUM, Ethereum.SigningOutput.parser())
        require(output.error == Common.SigningError.OK) { output.errorMessage.ifBlank { "EVM 交易签名失败。" } }
        return "0x${output.encoded.toByteArray().toHex()}"
    }

    fun signSolana(
        recipient: String,
        tokenAddress: String?,
        privateKey: ByteArray,
        estimate: WalletTransferEstimate,
        decimals: Int
    ): String {
        val amount = BigInteger(estimate.amountAtomic)
        require(amount.bitLength() <= 63) { "Solana 转账金额超过当前签名器支持范围。" }
        val builder = Solana.SigningInput.newBuilder().apply {
            recentBlockhash = estimate.nonceOrBlockhash
            this.privateKey = ByteString.copyFrom(privateKey)
            if (tokenAddress == null) {
                transferTransaction = Solana.Transfer.newBuilder()
                    .setRecipient(recipient)
                    .setValue(amount.toLong())
                    .build()
            } else if (estimate.recipientTokenAccountExists) {
                tokenTransferTransaction = Solana.TokenTransfer.newBuilder()
                    .setTokenMintAddress(tokenAddress)
                    .setSenderTokenAddress(requireNotNull(estimate.senderTokenAddress))
                    .setRecipientTokenAddress(requireNotNull(estimate.recipientTokenAddress))
                    .setAmount(amount.toLong())
                    .setDecimals(decimals)
                    .build()
            } else {
                createAndTransferTokenTransaction = Solana.CreateAndTransferToken.newBuilder()
                    .setRecipientMainAddress(recipient)
                    .setTokenMintAddress(tokenAddress)
                    .setSenderTokenAddress(requireNotNull(estimate.senderTokenAddress))
                    .setRecipientTokenAddress(requireNotNull(estimate.recipientTokenAddress))
                    .setAmount(amount.toLong())
                    .setDecimals(decimals)
                    .build()
            }
        }
        val output = AnySigner.sign(builder.build(), CoinType.SOLANA, Solana.SigningOutput.parser())
        require(output.error == Common.SigningError.OK) { output.errorMessage.ifBlank { "Solana 交易签名失败。" } }
        return output.encoded
    }

    private fun BigInteger.unsignedBytes(): ByteArray {
        if (this == BigInteger.ZERO) return byteArrayOf(0)
        val bytes = toByteArray()
        return if (bytes.size > 1 && bytes.first() == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private fun hexQuantityToBigInteger(value: String): BigInteger = BigInteger(value.removePrefix("0x").ifBlank { "0" }, 16)
}
