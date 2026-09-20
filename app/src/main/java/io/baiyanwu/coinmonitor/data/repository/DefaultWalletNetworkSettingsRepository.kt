package io.baiyanwu.coinmonitor.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.baiyanwu.coinmonitor.data.network.WalletRpcClient
import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.domain.model.WalletNetworkConfiguration
import io.baiyanwu.coinmonitor.domain.repository.WalletNetworkSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class DefaultWalletNetworkSettingsRepository(
    context: Context,
    private val httpClient: OkHttpClient
) : WalletNetworkSettingsRepository {
    private val secure = createPreferences(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val state = MutableStateFlow(read())
    private val rpcClient = WalletRpcClient(httpClient)

    override fun observe(): Flow<WalletNetworkConfiguration> = state.asStateFlow()
    override fun get(): WalletNetworkConfiguration = state.value
    override fun isSecureStorageAvailable(): Boolean = secure.available

    override suspend fun save(configuration: WalletNetworkConfiguration) = withContext(Dispatchers.IO) {
        val normalizedUrls = configuration.customRpcUrls.mapValues { it.value.trim().trimEnd('/') }
            .filterValues(String::isNotBlank)
        configuration.alchemyApiKey.trim().takeIf(String::isNotBlank)?.let { key ->
            validateAlchemy(key, configuration.enabledNetworks)
        }
        normalizedUrls.forEach { (networkId, url) ->
            val network = requireNotNull(configuration.network(networkId)) { "未知网络：$networkId" }
            validateRpc(network, url).getOrThrow()
        }
        val editor = requirePreferences().edit()
            .putString(KEY_ALCHEMY_API_KEY, configuration.alchemyApiKey.trim())
            .putStringSet(KEY_ENABLED_NETWORK_IDS, configuration.enabledNetworkIds)
        configuration.availableNetworks.forEach { network ->
            normalizedUrls[network.id]?.let { editor.putString(rpcKey(network.id), it) }
                ?: editor.remove(rpcKey(network.id))
        }
        check(editor.commit()) { "钱包网络配置保存失败。" }
        state.value = read()
    }

    private suspend fun validateAlchemy(apiKey: String, networks: List<WalletNetwork> = state.value.enabledNetworks) {
        val network = networks.firstOrNull {
            it.id == WalletNetwork.ETHEREUM.id && it.alchemyRpcHost != null
        } ?: networks.firstOrNull { it.alchemyRpcHost != null }
            ?: WalletNetwork.ETHEREUM
        val url = "https://${requireNotNull(network.alchemyRpcHost)}/v2/$apiKey"
        validateRpc(network, url).getOrElse { error ->
            throw IllegalArgumentException("Alchemy ${network.displayName} 连接失败：${error.message}")
        }
    }

    override suspend fun saveAlchemyApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        val normalized = apiKey.trim()
        normalized.takeIf(String::isNotBlank)?.let { validateAlchemy(it) }
        check(requirePreferences().edit().putString(KEY_ALCHEMY_API_KEY, normalized).commit()) {
            "Alchemy API Key 保存失败。"
        }
        state.value = read()
    }

    override suspend fun saveCustomRpc(network: WalletNetwork, url: String) = withContext(Dispatchers.IO) {
        validateRpc(network, url).getOrThrow()
        val enabled = state.value.enabledNetworkIds + network.id
        requirePreferences().edit()
            .putString(rpcKey(network.id), url.trim().trimEnd('/'))
            .putStringSet(KEY_ENABLED_NETWORK_IDS, enabled)
            .commit()
        state.value = read()
    }

    override suspend fun clearCustomRpc(network: WalletNetwork) = withContext(Dispatchers.IO) {
        requirePreferences().edit().remove(rpcKey(network.id)).commit()
        state.value = read()
    }

    override suspend fun refreshNetworkCatalog(): Result<List<WalletNetwork>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(CATALOG_URL).get().build()
            val remote = httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "网络目录 HTTP ${response.code}" }
                val body = response.body?.string().orEmpty()
                val manifest = json.decodeFromString(NetworkCatalogManifest.serializer(), body)
                require(manifest.schemaVersion == CATALOG_SCHEMA_VERSION) { "网络目录版本不受支持。" }
                manifest.networks.mapNotNull(::sanitizeRemoteNetwork)
            }
            requirePreferences().edit()
                .putString(KEY_REMOTE_CATALOG, json.encodeToString(remote))
                .commit()
            state.value = read()
            state.value.availableNetworks
        }
    }

    override suspend fun setNetworkEnabled(networkId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        require(state.value.availableNetworks.any { it.id == networkId }) { "未知网络：$networkId" }
        val ids = state.value.enabledNetworkIds.toMutableSet().apply {
            if (enabled) add(networkId) else remove(networkId)
        }
        check(requirePreferences().edit().putStringSet(KEY_ENABLED_NETWORK_IDS, ids).commit()) {
            "网络启用状态保存失败。"
        }
        state.value = read()
    }

    override suspend fun addCustomEvmNetwork(
        name: String,
        chainId: Long,
        symbol: String,
        explorerUrl: String,
        rpcUrl: String
    ): WalletNetwork = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "请输入网络名称。" }
        require(chainId > 0) { "Chain ID 必须大于 0。" }
        require(symbol.isNotBlank()) { "请输入原生币符号。" }
        val normalizedExplorer = explorerUrl.trim().trimEnd('/')
        require(normalizedExplorer.isBlank() || normalizedExplorer.startsWith("https://")) {
            "区块浏览器地址必须使用 https://。"
        }
        val normalizedRpc = rpcUrl.trim().trimEnd('/')
        require(normalizedRpc.startsWith("https://") || normalizedRpc.startsWith("http://")) {
            "RPC 地址必须以 http:// 或 https:// 开头。"
        }
        val actualChainId = rpcClient.evmChainId(normalizedRpc)
        require(actualChainId == chainId) { "RPC Chain ID 为 $actualChainId，与填写的 $chainId 不一致。" }

        val existing = state.value.availableNetworks.firstOrNull { it.id == "eip155:$chainId" }
        val network = existing ?: WalletNetwork.evm(
            chainId = chainId,
            alchemyNetwork = null,
            host = null,
            symbol = symbol.trim().uppercase(),
            name = name.trim(),
            explorer = normalizedExplorer,
            userDefined = true
        )
        val customNetworks = readCustomNetworks().filterNot { it.id == network.id } +
            network.copy(userDefined = true)
        val enabled = state.value.enabledNetworkIds + network.id
        check(
            requirePreferences().edit()
                .putString(KEY_CUSTOM_NETWORKS, json.encodeToString(customNetworks))
                .putString(rpcKey(network.id), normalizedRpc)
                .putStringSet(KEY_ENABLED_NETWORK_IDS, enabled)
                .commit()
        ) { "自定义 EVM 网络保存失败。" }
        state.value = read()
        requireNotNull(state.value.network(network.id))
    }

    override suspend fun removeCustomEvmNetwork(networkId: String) = withContext(Dispatchers.IO) {
        val target = state.value.network(networkId)
        require(target?.userDefined == true) { "只能删除用户添加的网络。" }
        val customNetworks = readCustomNetworks().filterNot { it.id == networkId }
        val enabled = state.value.enabledNetworkIds - networkId
        check(
            requirePreferences().edit()
                .putString(KEY_CUSTOM_NETWORKS, json.encodeToString(customNetworks))
                .putStringSet(KEY_ENABLED_NETWORK_IDS, enabled)
                .remove(rpcKey(networkId))
                .commit()
        ) { "自定义 EVM 网络删除失败。" }
        state.value = read()
    }

    override suspend fun validateRpc(network: WalletNetwork, url: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = url.trim()
            require(normalized.startsWith("https://") || normalized.startsWith("http://")) {
                "RPC 地址必须以 http:// 或 https:// 开头。"
            }
            if (network.isEvm) {
                val actual = rpcClient.evmChainId(normalized)
                require(actual == network.chainId) {
                    "RPC Chain ID 为 $actual，与 ${network.displayName} (${network.chainId}) 不一致。"
                }
            } else {
                val genesisHash = rpcClient.solanaGenesisHash(normalized)
                require(genesisHash == SOLANA_MAINNET_GENESIS_HASH) { "该 RPC 不是 Solana Mainnet。" }
            }
        }
    }

    private fun read(): WalletNetworkConfiguration {
        val preferences = secure.preferences ?: return WalletNetworkConfiguration()
        val remote = readNetworkList(KEY_REMOTE_CATALOG)
        val custom = readCustomNetworks()
        val catalog = (WalletNetwork.seedCatalog + remote + custom)
            .associateBy(WalletNetwork::id)
            .values
            .sortedWith(compareBy<WalletNetwork> { !it.isEvm }.thenBy(WalletNetwork::displayName))
        val defaultEnabled = WalletNetwork.defaultEnabledNetworkIds
        val enabled = preferences.getStringSet(KEY_ENABLED_NETWORK_IDS, null)?.toSet() ?: defaultEnabled
        return WalletNetworkConfiguration(
            alchemyApiKey = preferences.getString(KEY_ALCHEMY_API_KEY, "").orEmpty(),
            customRpcUrls = catalog.mapNotNull { network ->
                preferences.getString(rpcKey(network.id), null)
                    ?.takeIf(String::isNotBlank)
                    ?.let { network.id to it }
            }.toMap(),
            availableNetworks = catalog,
            enabledNetworkIds = enabled
        )
    }

    private fun readCustomNetworks(): List<WalletNetwork> = readNetworkList(KEY_CUSTOM_NETWORKS)

    private fun readNetworkList(key: String): List<WalletNetwork> {
        val raw = secure.preferences?.getString(key, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<WalletNetwork>>(raw) }.getOrDefault(emptyList())
    }

    private fun sanitizeRemoteNetwork(network: WalletNetwork): WalletNetwork? {
        val host = network.alchemyRpcHost ?: return null
        val chainId = network.chainId ?: return null
        if (!network.isEvm || chainId <= 0 || network.alchemyNetwork.isNullOrBlank()) return null
        if (!host.matches(Regex("^[a-z0-9-]+\\.g\\.alchemy\\.com$"))) return null
        if (network.explorerUrl.isNotBlank() && !network.explorerUrl.startsWith("https://")) return null
        return network.copy(id = "eip155:$chainId", decimals = 18, isEvm = true, userDefined = false)
    }

    private fun requirePreferences(): SharedPreferences = secure.preferences
        ?: error("当前设备的安全存储不可用，无法保存钱包网络配置。")

    private fun createPreferences(context: Context): SecurePreferencesResult = try {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        SecurePreferencesResult(
            true,
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        )
    } catch (error: Throwable) {
        Log.e("WalletNetwork", "Unable to create secure wallet network settings", error)
        SecurePreferencesResult(false, null)
    }

    private data class SecurePreferencesResult(val available: Boolean, val preferences: SharedPreferences?)

    private fun rpcKey(networkId: String): String = "rpc_${networkId.replace(':', '_')}"

    @Serializable
    private data class NetworkCatalogManifest(
        val schemaVersion: Int,
        val networks: List<WalletNetwork>
    )

    companion object {
        internal const val PREFS_NAME = "wallet_network_settings_secure"
        private const val KEY_ALCHEMY_API_KEY = "alchemy_api_key"
        private const val KEY_ENABLED_NETWORK_IDS = "enabled_network_ids"
        private const val KEY_REMOTE_CATALOG = "remote_evm_network_catalog"
        private const val KEY_CUSTOM_NETWORKS = "custom_evm_networks"
        private const val CATALOG_SCHEMA_VERSION = 1
        private const val CATALOG_URL =
            "https://raw.githubusercontent.com/baiyanwu/CoinMonitor/main/docs/wallet-evm-networks.json"
        private const val SOLANA_MAINNET_GENESIS_HASH = "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp"
    }
}
