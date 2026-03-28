package io.baiyanwu.coinmonitor.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
import java.security.MessageDigest
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

    suspend fun loadBitmap(
        symbol: String,
        preferredIconUrl: String? = null,
        fallbackIconUrl: String? = null,
        grayscaleFallback: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        val normalized = symbol.uppercase()
        val directRequests = buildDirectIconRequests(
            preferredIconUrl = preferredIconUrl,
            fallbackIconUrl = fallbackIconUrl,
            grayscaleFallback = grayscaleFallback
        )
        directRequests.forEach { request ->
            loadCachedBitmap(request.cacheKey)?.let { return@withContext it }
        }

        cacheMutex.withLock {
            directRequests.forEach { request ->
                loadCachedBitmap(request.cacheKey)?.let { return@withContext it }
                val bitmap = downloadBitmap(request.url) ?: return@forEach
                val finalBitmap = if (request.grayscale) bitmap.toGrayscale() else bitmap
                saveToDisk(request.cacheKey, finalBitmap)
                memoryCache[request.cacheKey] = finalBitmap
                return@withContext finalBitmap
            }

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

    private fun loadCachedBitmap(cacheKey: String): Bitmap? {
        memoryCache[cacheKey]?.let { return it }
        return loadFromDisk(cacheKey)?.also { memoryCache[cacheKey] = it }
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

    private fun buildDirectIconRequests(
        preferredIconUrl: String?,
        fallbackIconUrl: String?,
        grayscaleFallback: Boolean
    ): List<IconRequest> {
        val requests = mutableListOf<IconRequest>()
        preferredIconUrl?.takeIf { it.isNotBlank() }?.let { url ->
            requests += IconRequest(
                cacheKey = "icon_${buildHash(url)}",
                url = url,
                grayscale = false
            )
        }
        fallbackIconUrl?.takeIf { it.isNotBlank() }?.let { url ->
            requests += IconRequest(
                cacheKey = if (grayscaleFallback) {
                    "chain_gray_${buildHash(url)}"
                } else {
                    "chain_${buildHash(url)}"
                },
                url = url,
                grayscale = grayscaleFallback
            )
        }
        return requests.distinctBy { it.cacheKey }
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

    /**
     * 灰阶化在下载后、缓存前完成一次，避免列表滚动和悬浮窗刷新时重复做像素处理。
     */
    private fun Bitmap.toGrayscale(): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix().apply { setSaturation(0f) }
            )
        }
        canvas.drawBitmap(this, 0f, 0f, paint)
        return output
    }

    private fun buildHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private data class IconRequest(
        val cacheKey: String,
        val url: String,
        val grayscale: Boolean
    )

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
