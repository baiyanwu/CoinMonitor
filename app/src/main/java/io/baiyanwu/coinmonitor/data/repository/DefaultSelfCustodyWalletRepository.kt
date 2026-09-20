package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import io.baiyanwu.coinmonitor.data.network.AlchemyWalletClient
import io.baiyanwu.coinmonitor.data.network.OkxWalletClient
import io.baiyanwu.coinmonitor.data.network.OkxWalletTokenMetadata
import io.baiyanwu.coinmonitor.data.network.OkxWalletTokenRow
import io.baiyanwu.coinmonitor.data.network.WalletRpcClient
import io.baiyanwu.coinmonitor.data.network.okxTokenMetadataId
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
import io.baiyanwu.coinmonitor.domain.repository.OkxWalletCredentialsRepository
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
    okxCredentials: OkxWalletCredentialsRepository,
    httpClient: OkHttpClient
) : SelfCustodyWalletRepository {
    private val rpc = WalletRpcClient(httpClient)
    private val alchemy = AlchemyWalletClient(httpClient)
    private val okx = OkxWalletClient(httpClient, okxCredentials::getCredentials)
    private val signer = WalletTransactionSigner()
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val storageMutex = Mutex()

    override suspend fun loadCachedPortfolio(wallet: WalletProfile): WalletPortfolio? {
        val supported = networkSettings.get().enabledNetworks.filter(wallet::supports)
        val supportedIds = supported.mapTo(hashSetOf(), WalletNetwork::id)
        val localActivities = readLocalActivities(wallet.id)
        val hasSnapshot = preferences.contains(assetKey(wallet.id)) ||
            preferences.contains(indexedActivityKey(wallet.id)) ||
            localActivities.isNotEmpty()
        if (!hasSnapshot) return null

        return WalletPortfolio(
            assets = sortAssets(readAssetCache(wallet.id).filter { it.network.id in supportedIds }),
            activities = mergeActivities(
                localActivities + readIndexedActivities(wallet.id).filter { it.network.id in supportedIds }
            ),
            tokenIndexAvailable = preferences.contains(assetKey(wallet.id)),
            updatedAtMillis = maxOf(
                preferences.getLong(assetUpdatedAtKey(wallet.id), 0L),
                preferences.getLong(indexedActivityUpdatedAtKey(wallet.id), 0L)
            ).takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    override suspend fun loadPortfolio(wallet: WalletProfile): WalletPortfolio = coroutineScope {
        val config = networkSettings.get()
        val supported = config.enabledNetworks.filter(wallet::supports)
        val assetIndexFailures = linkedSetOf<WalletNetwork>()
        val activityIndexFailures = linkedSetOf<WalletNetwork>()
        val cachedAssets = readAssetCache(wallet.id)
        var aggregateAssetIndexFailure = !okx.isConfigured()
        var assets: List<SelfCustodyAsset> = if (okx.isConfigured()) {
            runCatching { loadOkxPortfolio(wallet, supported, cachedAssets) }
                .fold(
                    onSuccess = {
                        assetIndexFailures += it.partialNetworks
                        saveAssetCache(wallet.id, it.assets)
                        it.assets
                    },
                    onFailure = {
                        aggregateAssetIndexFailure = true
                        cachedAssets
                    }
                )
        } else {
            cachedAssets
        }

        val localActivities = readLocalActivities(wallet.id).map { activity ->
            if (activity.status == WalletActivityStatus.PENDING || activity.status == WalletActivityStatus.STALE) {
                runCatching { refreshActivityStatus(activity) }.getOrDefault(activity)
            } else activity
        }
        saveLocalActivities(wallet.id, localActivities.filter(WalletActivity::locallySubmitted))

        val cachedIndexedActivities = readIndexedActivities(wallet.id)
            .filter { activity -> supported.any { it.id == activity.network.id } }
        val indexedNetworks = supported.filter(WalletNetwork::alchemyTransfersSupported).toSet()
        val indexedActivities = if (config.hasAlchemy) {
            runCatching { alchemy.loadActivities(wallet, config.alchemyApiKey, supported, assets) }
                .fold(
                    onSuccess = { (fresh, failures) ->
                        activityIndexFailures += failures
                        val refreshedNetworkIds = (indexedNetworks - failures).mapTo(hashSetOf(), WalletNetwork::id)
                        val merged = fresh + cachedIndexedActivities.filter { it.network.id !in refreshedNetworkIds }
                        saveIndexedActivities(wallet.id, merged)
                        merged
                    },
                    onFailure = {
                        activityIndexFailures += indexedNetworks
                        cachedIndexedActivities
                    }
                )
        } else cachedIndexedActivities

        val activities = mergeActivities(localActivities + indexedActivities)

        WalletPortfolio(
            assets = sortAssets(assets),
            activities = activities,
            refreshFailures = WalletRefreshFailures(
                aggregateAssetIndex = aggregateAssetIndexFailure,
                assetIndex = assetIndexFailures,
                nativeBalance = emptySet(),
                activityIndex = activityIndexFailures
            ),
            tokenIndexAvailable = okx.isConfigured()
        )
    }

    private suspend fun loadOkxPortfolio(
        wallet: WalletProfile,
        networks: List<WalletNetwork>,
        cachedAssets: List<SelfCustodyAsset>
    ): OkxPortfolioResult = coroutineScope {
        val supportedIndexes = okx.getSupportedChainIndexes()
        val mapped = networks.mapNotNull { network -> network.okxChainIndex()?.let { network to it } }
        val partialNetworks = mapped.filterNot { (_, index) -> index in supportedIndexes }
            .mapTo(linkedSetOf()) { it.first }
        val requests = mapped.filter { (_, index) -> index in supportedIndexes }
            .groupBy(
                keySelector = { (network) -> wallet.addressFor(network).orEmpty() },
                valueTransform = { it }
            )
            .filterKeys(String::isNotBlank)

        val rows = mutableListOf<OkxWalletTokenRow>()
        requests.map { (address, entries) ->
            async {
                val requestNetworks = entries.map { it.first }
                runCatching { okx.getAllTokenBalances(address, entries.map { it.second }) }
                    .onSuccess { result -> synchronized(rows) { rows += result } }
                    .onFailure { synchronized(partialNetworks) { partialNetworks += requestNetworks } }
            }
        }.awaitAll()
        if (requests.isNotEmpty() && rows.isEmpty() && partialNetworks.containsAll(mapped.map { it.first })) {
            error("OKX 资产索引暂时不可用。")
        }

        val metadata = runCatching {
            okx.getTokenBasicInfo(rows.map { it.chainIndex to it.contractAddress })
        }.getOrDefault(emptyMap())
        val networkByIndex = mapped.associate { (network, index) -> index to network }
        val cachedById = cachedAssets.associateBy { asset ->
            selfCustodyAssetId(asset.network, asset.tokenAddress.orEmpty())
        }
        val refreshedAssets = rows.mapNotNull { row ->
                val network = networkByIndex[row.chainIndex] ?: return@mapNotNull null
                val assetId = selfCustodyAssetId(network, row.contractAddress)
                row.toSelfCustodyAsset(
                    network = network,
                    metadata = metadata[okxTokenMetadataId(row.chainIndex, row.contractAddress)],
                    cached = cachedById[assetId]
                )
            }
        OkxPortfolioResult(
            assets = (refreshedAssets + cachedAssets.filter { it.network in partialNetworks })
                .distinctBy(SelfCustodyAsset::id),
            partialNetworks = partialNetworks
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
            locallySubmitted = true,
            tokenAddress = request.tokenAddress
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
        walletIds.forEach { walletId ->
            editor.remove(assetKey(walletId))
                .remove(assetUpdatedAtKey(walletId))
                .remove(activityKey(walletId))
                .remove(indexedActivityKey(walletId))
                .remove(indexedActivityUpdatedAtKey(walletId))
        }
        check(editor.commit()) { "钱包本地缓存清理失败。" }
    }

    private fun WalletNetwork.okxChainIndex(): String? = when {
        isEvm -> chainId?.toString()
        id == WalletNetwork.SOLANA.id -> OKX_SOLANA_CHAIN_INDEX
        else -> null
    }

    private fun selfCustodyAssetId(network: WalletNetwork, contractAddress: String): String {
        val tokenId = when {
            contractAddress.isBlank() -> "native"
            network.isEvm -> contractAddress.lowercase()
            else -> contractAddress
        }
        return "${network.name}:$tokenId"
    }

    private fun OkxWalletTokenRow.toSelfCustodyAsset(
        network: WalletNetwork,
        metadata: OkxWalletTokenMetadata?,
        cached: SelfCustodyAsset?
    ): SelfCustodyAsset? {
        val parsedBalance = balance.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
        val isNative = contractAddress.isBlank()
        val decimals = when {
            isNative -> network.decimals
            metadata != null -> metadata.decimals
            cached != null -> cached.decimals
            else -> inferDecimals(rawBalance, parsedBalance)
        }
        val atomicBalance = rawBalance?.toBigIntegerOrNull()
            ?: decimals?.let { precision ->
                runCatching { parsedBalance.movePointRight(precision).toBigIntegerExact() }.getOrNull()
            }
            ?: cached?.rawBalance?.toBigIntegerOrNull()
        val price = tokenPrice?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        val resolvedDecimals = decimals ?: 0
        val resolvedSymbol = metadata?.symbol?.takeIf(String::isNotBlank)
            ?: this.symbol.takeIf { it.isNotBlank() && it != "?" }
            ?: cached?.symbol
            ?: "UNKNOWN"
        val name = if (isNative) {
            network.displayName
        } else {
            metadata?.name?.takeIf(String::isNotBlank) ?: cached?.name ?: resolvedSymbol
        }
        return SelfCustodyAsset(
            id = selfCustodyAssetId(network, contractAddress),
            network = network,
            tokenAddress = contractAddress.takeIf(String::isNotBlank),
            name = name,
            symbol = resolvedSymbol,
            decimals = resolvedDecimals,
            rawBalance = atomicBalance?.toString().orEmpty(),
            balance = parsedBalance.stripTrailingZeros(),
            priceUsd = price,
            valueUsd = price?.multiply(parsedBalance)?.setScale(8, RoundingMode.HALF_UP)?.stripTrailingZeros(),
            logoUrl = metadata?.logoUrl ?: cached?.logoUrl,
            verified = isNative || !isRiskToken,
            isNative = isNative,
            transferable = isNative || (decimals != null && atomicBalance != null)
        )
    }

    private fun inferDecimals(rawBalance: String?, balance: BigDecimal): Int? {
        val raw = rawBalance?.toBigIntegerOrNull() ?: return null
        return (0..MAX_TOKEN_DECIMALS).firstOrNull { decimals ->
            BigDecimal(raw).movePointLeft(decimals).compareTo(balance) == 0
        }
    }

    private suspend fun estimateEvm(request: WalletTransferRequest, url: String, sender: String): WalletTransferEstimate {
        val gasPriceHex = rpc.evmGasPrice(url)
        val gasPrice = gasPriceHex.hexQuantity()
        val requestedAtomic = amountToAtomic(request.amount, request.tokenDecimals)
        val data = request.tokenAddress?.let { encodeErc20Transfer(request.recipient, requestedAtomic) }
        val nativeBalance = rpc.evmBalance(url, sender).hexQuantity()
        request.tokenAddress?.let { tokenAddress ->
            val tokenBalance = rpc.evmTokenBalance(url, tokenAddress, sender).hexQuantity()
            require(tokenBalance >= requestedAtomic) { "代币链上余额不足。" }
        }
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
        senderToken?.let { tokenAccount ->
            val tokenBalance = rpc.solanaTokenBalance(url, tokenAccount).toBigInteger()
            require(tokenBalance >= amount) { "代币链上余额不足。" }
        }
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

    private fun activityIdentity(activity: WalletActivity): String =
        "${activity.network.id}:${activity.transactionHash}:${activity.tokenAddress ?: "native"}:${activity.direction}"

    private fun mergeActivities(activities: List<WalletActivity>): List<WalletActivity> = activities
        .groupBy(::activityIdentity)
        .map { (_, versions) -> versions.minByOrNull { activityStatusRank(it.status) }!! }
        .sortedByDescending(WalletActivity::timestampMillis)

    private fun sortAssets(assets: List<SelfCustodyAsset>): List<SelfCustodyAsset> = assets
        .filter { it.balance.signum() != 0 }
        .sortedWith(
            compareByDescending<SelfCustodyAsset> { it.valueUsd ?: BigDecimal.valueOf(-1) }
                .thenBy { it.network.displayName }
                .thenBy(SelfCustodyAsset::symbol)
        )

    private suspend fun replaceLocalActivity(activity: WalletActivity) {
        val current = readLocalActivities(activity.walletId)
        saveLocalActivities(activity.walletId, current.map { if (it.transactionHash == activity.transactionHash) activity else it })
    }

    private suspend fun saveAssetCache(walletId: String, assets: List<SelfCustodyAsset>) = storageMutex.withLock {
        preferences.edit()
            .putString(assetKey(walletId), json.encodeToString(assets.map(CachedAsset::from)))
            .putLong(assetUpdatedAtKey(walletId), System.currentTimeMillis())
            .commit()
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

    private suspend fun saveIndexedActivities(walletId: String, activities: List<WalletActivity>) = storageMutex.withLock {
        val compact = mergeActivities(activities).take(MAX_INDEXED_ACTIVITY_CACHE)
        preferences.edit()
            .putString(indexedActivityKey(walletId), json.encodeToString(compact.map(CachedActivity::from)))
            .putLong(indexedActivityUpdatedAtKey(walletId), System.currentTimeMillis())
            .commit()
    }

    private fun readIndexedActivities(walletId: String): List<WalletActivity> =
        preferences.getString(indexedActivityKey(walletId), null)?.let {
            runCatching { json.decodeFromString<List<CachedActivity>>(it).map(CachedActivity::toDomain) }
                .getOrDefault(emptyList())
        }.orEmpty()

    private fun assetKey(walletId: String) = "assets_$walletId"
    private fun assetUpdatedAtKey(walletId: String) = "assets_updated_at_$walletId"
    private fun activityKey(walletId: String) = "activities_$walletId"
    private fun indexedActivityKey(walletId: String) = "indexed_activities_$walletId"
    private fun indexedActivityUpdatedAtKey(walletId: String) = "indexed_activities_updated_at_$walletId"

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
        val isNative: Boolean,
        val balance: String? = null,
        val transferable: Boolean = true
    ) {
        fun toDomain(): SelfCustodyAsset {
            val parsedBalance = balance?.toBigDecimalOrNull()
                ?: rawBalance.toBigDecimalOrNull()?.movePointLeft(decimals)
                ?: BigDecimal.ZERO
            val price = priceUsd?.toBigDecimalOrNull()
            return SelfCustodyAsset(
                id, network, tokenAddress, name, symbol, decimals, rawBalance, parsedBalance.stripTrailingZeros(), price,
                price?.multiply(parsedBalance)?.setScale(8, RoundingMode.HALF_UP)?.stripTrailingZeros(),
                logoUrl, verified, isNative, transferable
            )
        }

        companion object {
            fun from(asset: SelfCustodyAsset) = CachedAsset(
                asset.id, asset.network, asset.tokenAddress, asset.name, asset.symbol, asset.decimals,
                asset.rawBalance, asset.priceUsd?.toPlainString(), asset.logoUrl, asset.verified, asset.isNative,
                asset.balance.toPlainString(), asset.transferable
            )
        }
    }

    private data class OkxPortfolioResult(
        val assets: List<SelfCustodyAsset>,
        val partialNetworks: Set<WalletNetwork>
    )

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
        val locallySubmitted: Boolean,
        val tokenAddress: String? = null
    ) {
        fun toDomain() = WalletActivity(
            id, walletId, network, hash, direction, symbol, amount?.toBigDecimalOrNull(), counterparty,
            timestamp, status, locallySubmitted, tokenAddress
        )

        companion object {
            fun from(value: WalletActivity) = CachedActivity(
                value.id, value.walletId, value.network, value.transactionHash, value.direction,
                value.symbol, value.amount?.toPlainString(), value.counterparty, value.timestampMillis,
                value.status, value.locallySubmitted, value.tokenAddress
            )
        }
    }

    private companion object {
        const val PREFS_NAME = "self_custody_wallet_cache"
        const val SOLANA_BASE_FEE_LAMPORTS = 5_000L
        const val SPL_TOKEN_ACCOUNT_SIZE = 165
        const val EVM_NATIVE_TRANSFER_GAS = 21_000L
        const val OKX_SOLANA_CHAIN_INDEX = "501"
        const val MAX_TOKEN_DECIMALS = 36
        const val STALE_AFTER_MILLIS = 30 * 60 * 1000L
        const val MAX_INDEXED_ACTIVITY_CACHE = 500
    }
}
