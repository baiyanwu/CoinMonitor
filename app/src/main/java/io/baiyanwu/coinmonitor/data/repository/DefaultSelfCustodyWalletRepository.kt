package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import io.baiyanwu.coinmonitor.data.network.AlchemyWalletClient
import io.baiyanwu.coinmonitor.data.network.WalletRpcClient
import io.baiyanwu.coinmonitor.data.wallet.WalletTransactionSigner
import io.baiyanwu.coinmonitor.domain.model.WalletActivity
import io.baiyanwu.coinmonitor.domain.model.WalletActivityDirection
import io.baiyanwu.coinmonitor.domain.model.WalletActivityStatus
import io.baiyanwu.coinmonitor.domain.model.SelfCustodyAsset
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.model.WalletPortfolio
import io.baiyanwu.coinmonitor.domain.model.WalletProfile
import io.baiyanwu.coinmonitor.domain.model.WalletRefreshFailures
import io.baiyanwu.coinmonitor.domain.repository.SelfCustodyWalletRepository
import io.baiyanwu.coinmonitor.domain.repository.WalletNetworkSettingsRepository
import io.baiyanwu.coinmonitor.domain.repository.WalletTransferEstimate
import io.baiyanwu.coinmonitor.domain.repository.WalletTransferRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.UUID

class DefaultSelfCustodyWalletRepository(
    context: Context,
    private val networkSettings: WalletNetworkSettingsRepository,
    httpClient: OkHttpClient
) : SelfCustodyWalletRepository {
    private val rpc = WalletRpcClient(httpClient)
    private val alchemy = AlchemyWalletClient(httpClient)
    private val signer = WalletTransactionSigner()
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val storageMutex = Mutex()

    override suspend fun loadPortfolio(wallet: WalletProfile): WalletPortfolio = coroutineScope {
        val config = networkSettings.get()
        val supported = config.enabledNetworks.filter(wallet::supports)
        val assetIndexFailures = linkedSetOf<WalletNetwork>()
        val nativeBalanceFailures = linkedSetOf<WalletNetwork>()
        val activityIndexFailures = linkedSetOf<WalletNetwork>()
        var aggregateAssetIndexFailure = false
        var assets: List<SelfCustodyAsset> = if (config.hasAlchemy) {
            runCatching { alchemy.loadPortfolio(wallet, config.alchemyApiKey, supported) }
                .fold(
                    onSuccess = {
                        assetIndexFailures += it.partialNetworks
                        saveAssetCache(wallet.id, it.assets)
                        it.assets
                    },
                    onFailure = {
                        aggregateAssetIndexFailure = true
                        readAssetCache(wallet.id)
                    }
                )
        } else {
            emptyList()
        }

        val nativeRefreshes = supported.mapNotNull { network ->
            val url = config.rpcUrl(network) ?: return@mapNotNull null
            async {
                runCatching { loadNativeAsset(wallet, network, url) }
                    .onFailure { synchronized(nativeBalanceFailures) { nativeBalanceFailures += network } }
                    .getOrNull()
            }
        }.awaitAll().filterNotNull()
        if (nativeRefreshes.isNotEmpty()) {
            val refreshedIds = nativeRefreshes.mapTo(hashSetOf(), SelfCustodyAsset::id)
            assets = assets.filterNot { it.id in refreshedIds } + nativeRefreshes
        }

        val localActivities = readLocalActivities(wallet.id).map { activity ->
            if (activity.status == WalletActivityStatus.PENDING || activity.status == WalletActivityStatus.STALE) {
                runCatching { refreshActivityStatus(activity) }.getOrDefault(activity)
            } else activity
        }
        saveLocalActivities(wallet.id, localActivities.filter(WalletActivity::locallySubmitted))

        val indexedActivities = if (config.hasAlchemy) {
            runCatching { alchemy.loadActivities(wallet, config.alchemyApiKey, supported, assets) }
                .onSuccess { activityIndexFailures += it.second }
                .getOrElse {
                    emptyList<WalletActivity>() to supported
                        .filter(WalletNetwork::alchemyTransfersSupported)
                        .toSet()
                }
                .first
        } else emptyList()

        val activities = (localActivities + indexedActivities)
            .groupBy { "${it.network}:${it.transactionHash}:${it.symbol}:${it.direction}" }
            .map { (_, versions) -> versions.minByOrNull { activityStatusRank(it.status) }!! }
            .sortedByDescending(WalletActivity::timestampMillis)

        WalletPortfolio(
            assets = assets.filter { it.balance.signum() != 0 }.sortedWith(
                compareByDescending<SelfCustodyAsset> { it.valueUsd ?: BigDecimal.valueOf(-1) }
                    .thenBy { it.network.displayName }
                    .thenBy(SelfCustodyAsset::symbol)
            ),
            activities = activities,
            refreshFailures = WalletRefreshFailures(
                aggregateAssetIndex = aggregateAssetIndexFailure,
                assetIndex = assetIndexFailures,
                nativeBalance = nativeBalanceFailures,
                activityIndex = activityIndexFailures
            ),
            tokenIndexAvailable = config.hasAlchemy
        )
    }

    override suspend fun estimateTransfer(request: WalletTransferRequest): WalletTransferEstimate {
        val config = networkSettings.get()
        val rpcUrl = config.rpcUrl(request.network) ?: error("${request.network.displayName} 尚未配置 RPC。")
        require(signer.isValidAddress(request.network, request.recipient)) { "收款地址格式无效。" }
        val sender = request.wallet.addressFor(request.network) ?: error("当前钱包不支持该网络。")
        return if (request.network.isEvm) {
            estimateEvm(request, rpcUrl, sender)
        } else {
            estimateSolana(request, rpcUrl, sender)
        }
    }

    override suspend fun signAndBroadcast(estimate: WalletTransferEstimate, privateKey: ByteArray): WalletActivity {
        val request = estimate.request
        val config = networkSettings.get()
        val rpcUrl = config.rpcUrl(request.network) ?: error("${request.network.displayName} 尚未配置 RPC。")
        val hash = try {
            if (request.network.isEvm) {
                val raw = signer.signEvm(request.network, request.recipient, request.tokenAddress, privateKey, estimate)
                rpc.evmSendRawTransaction(rpcUrl, raw)
            } else {
                val raw = signer.signSolana(request.recipient, request.tokenAddress, privateKey, estimate, request.tokenDecimals)
                rpc.solanaSendTransaction(rpcUrl, raw)
            }
        } finally {
            privateKey.fill(0)
        }
        val activity = WalletActivity(
            id = UUID.randomUUID().toString(),
            walletId = request.wallet.id,
            network = request.network,
            transactionHash = hash,
            direction = WalletActivityDirection.OUTGOING,
            symbol = request.symbol,
            amount = request.amount.toBigDecimalOrNull(),
            counterparty = request.recipient,
            timestampMillis = System.currentTimeMillis(),
            status = WalletActivityStatus.PENDING,
            locallySubmitted = true
        )
        saveLocalActivities(request.wallet.id, listOf(activity) + readLocalActivities(request.wallet.id))
        return activity
    }

    override suspend fun refreshActivityStatus(activity: WalletActivity): WalletActivity {
        val rpcUrl = networkSettings.get().rpcUrl(activity.network) ?: return activity
        val updated = if (activity.network.isEvm) {
            val receipt = rpc.evmReceipt(rpcUrl, activity.transactionHash) ?: return staleIfNeeded(activity)
            val status = receipt["status"]?.toString()?.trim('"')
            activity.copy(status = if (status == "0x1") WalletActivityStatus.CONFIRMED else WalletActivityStatus.FAILED)
        } else {
            val status = rpc.solanaSignatureStatus(rpcUrl, activity.transactionHash) ?: return staleIfNeeded(activity)
            val failed = status["err"] != null && status["err"].toString() != "null"
            val confirmed = status["confirmationStatus"]?.toString()?.contains("confirmed") == true ||
                status["confirmationStatus"]?.toString()?.contains("finalized") == true
            activity.copy(status = when {
                failed -> WalletActivityStatus.FAILED
                confirmed -> WalletActivityStatus.CONFIRMED
                else -> WalletActivityStatus.PENDING
            })
        }
        replaceLocalActivity(updated)
        return updated
    }

    override suspend fun clearLocalData(walletIds: Collection<String>) = storageMutex.withLock {
        val editor = preferences.edit()
        walletIds.forEach { walletId -> editor.remove(assetKey(walletId)).remove(activityKey(walletId)) }
        check(editor.commit()) { "钱包本地缓存清理失败。" }
    }

    private suspend fun loadNativeAsset(wallet: WalletProfile, network: WalletNetwork, url: String): SelfCustodyAsset {
        val address = requireNotNull(wallet.addressFor(network))
        val raw = if (network.isEvm) {
            rpc.evmBalance(url, address).hexQuantity()
        } else {
            BigInteger.valueOf(rpc.solanaBalance(url, address))
        }
        val balance = BigDecimal(raw).movePointLeft(network.decimals).stripTrailingZeros()
        val cached = readAssetCache(wallet.id).firstOrNull { it.network == network && it.isNative }
        return SelfCustodyAsset(
            id = "${network.name}:native",
            network = network,
            tokenAddress = null,
            name = network.displayName,
            symbol = network.symbol,
            decimals = network.decimals,
            rawBalance = raw.toString(),
            balance = balance,
            priceUsd = cached?.priceUsd,
            valueUsd = cached?.priceUsd?.multiply(balance)?.stripTrailingZeros(),
            logoUrl = cached?.logoUrl,
            verified = true,
            isNative = true
        )
    }

    private suspend fun estimateEvm(request: WalletTransferRequest, url: String, sender: String): WalletTransferEstimate {
        val gasPriceHex = rpc.evmGasPrice(url)
        val gasPrice = gasPriceHex.hexQuantity()
        val requestedAtomic = amountToAtomic(request.amount, request.tokenDecimals)
        val data = request.tokenAddress?.let { encodeErc20Transfer(request.recipient, requestedAtomic) }
        val nativeBalance = rpc.evmBalance(url, sender).hexQuantity()
        val value = when {
            request.tokenAddress != null -> BigInteger.ZERO
            request.sendMaximum -> {
                val provisionalFee = gasPrice * BigInteger.valueOf(EVM_NATIVE_TRANSFER_GAS)
                require(nativeBalance > provisionalFee) { "余额不足以支付网络费。" }
                nativeBalance - provisionalFee
            }
            else -> requestedAtomic
        }
        val gasLimitHex = rpc.evmEstimateGas(
            url = url,
            from = sender,
            to = request.tokenAddress ?: request.recipient,
            value = value.toHexQuantity(),
            data = data
        )
        val gasLimit = gasLimitHex.hexQuantity()
        val fee = gasPrice.multiply(gasLimit)
        val amount = if (request.sendMaximum && request.tokenAddress == null) {
            require(nativeBalance > fee) { "余额不足以支付网络费。" }
            nativeBalance - fee
        } else requestedAtomic
        require(nativeBalance >= fee + if (request.tokenAddress == null) amount else BigInteger.ZERO) {
            "原生币余额不足以支付转账金额和网络费。"
        }
        return WalletTransferEstimate(
            request = request,
            amountAtomic = amount.toString(),
            feeAtomic = fee.toString(),
            fee = BigDecimal(fee).movePointLeft(request.network.decimals),
            nonceOrBlockhash = rpc.evmNonce(url, sender),
            gasLimit = gasLimitHex,
            gasPrice = gasPriceHex
        )
    }

    private suspend fun estimateSolana(request: WalletTransferRequest, url: String, sender: String): WalletTransferEstimate {
        var amount = amountToAtomic(request.amount, request.tokenDecimals)
        val recipientToken = request.tokenAddress?.let { signer.solanaTokenAddress(request.recipient, it) }
        val recipientExists = recipientToken?.let { rpc.solanaAccountExists(url, it) } ?: true
        val accountRent = if (request.tokenAddress != null && !recipientExists) {
            BigInteger.valueOf(rpc.solanaMinimumBalanceForRentExemption(url, SPL_TOKEN_ACCOUNT_SIZE))
        } else BigInteger.ZERO
        val fee = BigInteger.valueOf(SOLANA_BASE_FEE_LAMPORTS) + accountRent
        val solBalance = BigInteger.valueOf(rpc.solanaBalance(url, sender))
        if (request.sendMaximum && request.tokenAddress == null) {
            require(solBalance > fee) { "余额不足以支付网络费。" }
            amount = solBalance - fee
        }
        require(solBalance >= fee + if (request.tokenAddress == null) amount else BigInteger.ZERO) {
            "SOL 余额不足以支付转账金额、账户租金和网络费。"
        }
        val senderToken = request.tokenAddress?.let { signer.solanaTokenAddress(sender, it) }
        return WalletTransferEstimate(
            request = request,
            amountAtomic = amount.toString(),
            feeAtomic = fee.toString(),
            fee = BigDecimal(fee).movePointLeft(WalletNetwork.SOLANA.decimals),
            nonceOrBlockhash = rpc.solanaLatestBlockhash(url),
            senderTokenAddress = senderToken,
            recipientTokenAddress = recipientToken,
            recipientTokenAccountExists = recipientExists
        )
    }

    private fun amountToAtomic(value: String, decimals: Int): BigInteger {
        val amount = value.trim().toBigDecimalOrNull() ?: error("金额格式无效。")
        require(amount > BigDecimal.ZERO) { "转账金额必须大于零。" }
        val atomic = amount.movePointRight(decimals)
        require(atomic.stripTrailingZeros().scale() <= 0) { "金额小数位超过代币精度。" }
        return atomic.toBigIntegerExact()
    }

    private fun encodeErc20Transfer(recipient: String, amount: BigInteger): String {
        val address = recipient.removePrefix("0x").lowercase().padStart(64, '0')
        val value = amount.toString(16).padStart(64, '0')
        return "0xa9059cbb$address$value"
    }

    private fun String.hexQuantity(): BigInteger = BigInteger(removePrefix("0x").ifBlank { "0" }, 16)
    private fun BigInteger.toHexQuantity(): String = "0x${toString(16)}"

    private fun staleIfNeeded(activity: WalletActivity): WalletActivity = if (
        System.currentTimeMillis() - activity.timestampMillis > STALE_AFTER_MILLIS
    ) activity.copy(status = WalletActivityStatus.STALE) else activity

    private fun activityStatusRank(status: WalletActivityStatus): Int = when (status) {
        WalletActivityStatus.CONFIRMED -> 0
        WalletActivityStatus.FAILED -> 1
        WalletActivityStatus.STALE -> 2
        WalletActivityStatus.PENDING -> 3
    }

    private suspend fun replaceLocalActivity(activity: WalletActivity) {
        val current = readLocalActivities(activity.walletId)
        saveLocalActivities(activity.walletId, current.map { if (it.transactionHash == activity.transactionHash) activity else it })
    }

    private suspend fun saveAssetCache(walletId: String, assets: List<SelfCustodyAsset>) = storageMutex.withLock {
        preferences.edit().putString(assetKey(walletId), json.encodeToString(assets.map(CachedAsset::from))).commit()
    }

    private fun readAssetCache(walletId: String): List<SelfCustodyAsset> = preferences.getString(assetKey(walletId), null)?.let {
        runCatching { json.decodeFromString<List<CachedAsset>>(it).map(CachedAsset::toDomain) }.getOrDefault(emptyList())
    }.orEmpty()

    private suspend fun saveLocalActivities(walletId: String, activities: List<WalletActivity>) = storageMutex.withLock {
        val compact = activities.sortedByDescending(WalletActivity::timestampMillis).distinctBy(WalletActivity::transactionHash).take(100)
        preferences.edit().putString(activityKey(walletId), json.encodeToString(compact.map(CachedActivity::from))).commit()
    }

    private fun readLocalActivities(walletId: String): List<WalletActivity> = preferences.getString(activityKey(walletId), null)?.let {
        runCatching { json.decodeFromString<List<CachedActivity>>(it).map(CachedActivity::toDomain) }.getOrDefault(emptyList())
    }.orEmpty()

    private fun assetKey(walletId: String) = "assets_$walletId"
    private fun activityKey(walletId: String) = "activities_$walletId"

    @Serializable
    private data class CachedAsset(
        val id: String,
        val network: WalletNetwork,
        val tokenAddress: String?,
        val name: String,
        val symbol: String,
        val decimals: Int,
        val rawBalance: String,
        val priceUsd: String?,
        val logoUrl: String?,
        val verified: Boolean,
        val isNative: Boolean
    ) {
        fun toDomain(): SelfCustodyAsset {
            val balance = BigDecimal(rawBalance).movePointLeft(decimals).stripTrailingZeros()
            val price = priceUsd?.toBigDecimalOrNull()
            return SelfCustodyAsset(
                id, network, tokenAddress, name, symbol, decimals, rawBalance, balance, price,
                price?.multiply(balance)?.setScale(8, RoundingMode.HALF_UP)?.stripTrailingZeros(),
                logoUrl, verified, isNative
            )
        }

        companion object {
            fun from(asset: SelfCustodyAsset) = CachedAsset(
                asset.id, asset.network, asset.tokenAddress, asset.name, asset.symbol, asset.decimals,
                asset.rawBalance, asset.priceUsd?.toPlainString(), asset.logoUrl, asset.verified, asset.isNative
            )
        }
    }

    @Serializable
    private data class CachedActivity(
        val id: String,
        val walletId: String,
        val network: WalletNetwork,
        val hash: String,
        val direction: WalletActivityDirection,
        val symbol: String,
        val amount: String?,
        val counterparty: String?,
        val timestamp: Long,
        val status: WalletActivityStatus,
        val locallySubmitted: Boolean
    ) {
        fun toDomain() = WalletActivity(
            id, walletId, network, hash, direction, symbol, amount?.toBigDecimalOrNull(), counterparty,
            timestamp, status, locallySubmitted
        )

        companion object {
            fun from(value: WalletActivity) = CachedActivity(
                value.id, value.walletId, value.network, value.transactionHash, value.direction,
                value.symbol, value.amount?.toPlainString(), value.counterparty, value.timestampMillis,
                value.status, value.locallySubmitted
            )
        }
    }

    private companion object {
        const val PREFS_NAME = "self_custody_wallet_cache"
        const val SOLANA_BASE_FEE_LAMPORTS = 5_000L
        const val SPL_TOKEN_ACCOUNT_SIZE = 165
        const val EVM_NATIVE_TRANSFER_GAS = 21_000L
        const val STALE_AFTER_MILLIS = 30 * 60 * 1000L
    }
}
