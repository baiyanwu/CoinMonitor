package io.baiyanwu.coinmonitor.overlay

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** 只解析交易所币种符号对应的图片 URL；图片下载、解码和缓存由 Coil 负责。 */
class CoinIconService private constructor() {
    private val httpClient = OkHttpClient()
    private val resolvedIconUrls = ConcurrentHashMap<String, String>()
    private val unresolvedIconSymbols = ConcurrentHashMap.newKeySet<String>()
    private val resolutionLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun resolveIconUrl(symbol: String): String? = withContext(Dispatchers.IO) {
        val normalized = symbol.uppercase()
        resolutionLocks.getOrPut(normalized) { Mutex() }.withLock {
            resolvedIconUrls[normalized]?.let { return@withLock it }
            if (unresolvedIconSymbols.contains(normalized)) return@withLock null
            val resolved = fetchIconUrl(normalized)
            if (resolved == null) unresolvedIconSymbols += normalized else resolvedIconUrls[normalized] = resolved
            resolved
        }
    }

    private fun fetchIconUrl(symbol: String): String? {
        return try {
            val encoded = URLEncoder.encode(symbol, Charsets.UTF_8.name())
            val request = Request.Builder()
                .url("https://api.coingecko.com/api/v3/search?query=$encoded")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return null
                val coins = JSONObject(body).optJSONArray("coins") ?: return null

                var fallbackUrl: String? = null
                for (index in 0 until coins.length()) {
                    val item = coins.optJSONObject(index) ?: continue
                    val coinSymbol = item.optString("symbol").uppercase()
                    val large = item.optString("large").takeIf { it.isNotBlank() }
                    val thumb = item.optString("thumb").takeIf { it.isNotBlank() }
                    if (fallbackUrl == null) {
                        fallbackUrl = large ?: thumb
                    }
                    if (coinSymbol == symbol) {
                        return large ?: thumb ?: fallbackUrl
                    }
                }
                fallbackUrl
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        @Volatile private var instance: CoinIconService? = null

        fun get(@Suppress("UNUSED_PARAMETER") context: Context): CoinIconService = instance ?: synchronized(this) {
            instance ?: CoinIconService().also { instance = it }
        }
    }
}
