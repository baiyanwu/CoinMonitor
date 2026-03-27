package io.baiyanwu.coinmonitor.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class CoinIconService private constructor(private val context: Context) {
    private val httpClient = OkHttpClient()
    private val cacheMutex = Mutex()
    private val memoryCache = ConcurrentHashMap<String, Bitmap>()
    private val ttlMillis = 7L * 24L * 60L * 60L * 1000L
    private val iconDir: File by lazy {
        File(context.cacheDir, "coin_icons").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun loadBitmap(symbol: String): Bitmap? = withContext(Dispatchers.IO) {
        val normalized = symbol.uppercase()
        memoryCache[normalized]?.let { return@withContext it }

        loadFromDisk(normalized)?.let { bitmap ->
            memoryCache[normalized] = bitmap
            return@withContext bitmap
        }

        cacheMutex.withLock {
            memoryCache[normalized]?.let { return@withContext it }
            loadFromDisk(normalized)?.let { bitmap ->
                memoryCache[normalized] = bitmap
                return@withContext bitmap
            }

            val remoteUrl = fetchIconUrl(normalized) ?: return@withContext null
            val bitmap = downloadBitmap(remoteUrl) ?: return@withContext null
            saveToDisk(normalized, bitmap)
            memoryCache[normalized] = bitmap
            bitmap
        }
    }

    private fun loadFromDisk(symbol: String): Bitmap? {
        val file = File(iconDir, "$symbol.png")
        if (!file.exists()) return null
        if (System.currentTimeMillis() - file.lastModified() > ttlMillis) {
            file.delete()
            return null
        }
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun saveToDisk(symbol: String, bitmap: Bitmap) {
        val file = File(iconDir, "$symbol.png")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun fetchIconUrl(symbol: String): String? {
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
            return fallbackUrl
        }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    companion object {
        @Volatile
        private var instance: CoinIconService? = null

        fun get(context: Context): CoinIconService {
            return instance ?: synchronized(this) {
                instance ?: CoinIconService(context.applicationContext).also { instance = it }
            }
        }
    }
}

