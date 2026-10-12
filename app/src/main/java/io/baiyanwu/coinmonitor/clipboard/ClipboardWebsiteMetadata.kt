package io.baiyanwu.coinmonitor.clipboard

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Like the Mac editor: read the site's declared icon before trying /favicon.ico. */
internal object ClipboardWebsiteMetadata {
    private val client = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).build()
    private val tags = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val attributes = Regex("([a-zA-Z_-]+)\\s*=\\s*[\"']([^\"']*)[\"']")

    fun iconCandidates(html: String, baseUrl: String): List<String> {
        if (safeClipboardUrl(baseUrl) == null) return emptyList()
        val base = URI(baseUrl)
        val icons = tags.findAll(html).mapNotNull { tag ->
            val values = attributes.findAll(tag.value).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val rel = values["rel"].orEmpty().lowercase().split(Regex("\\s+"))
            if ("icon" !in rel && "apple-touch-icon" !in rel) return@mapNotNull null
            val href = values["href"]?.replace("&amp;", "&") ?: return@mapNotNull null
            runCatching { safeClipboardUrl(base.resolve(href).toString()) }.getOrNull()
        }.toList()
        return (icons + base.resolve("/favicon.ico").toString()).distinct()
    }

    suspend fun loadIcon(sourceUrl: String): String? {
        if (safeClipboardUrl(sourceUrl) == null) return null
        val source = URI(sourceUrl)
        val homepage = URI(source.scheme, null, source.host, source.port, "/", null, null).toString()
        val html = try { fetch(homepage, 256 * 1024, image = false).decodeToString() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { "" }
        for (candidate in iconCandidates(html, homepage).take(8)) {
            try {
                fetch(candidate, 2 * 1024 * 1024, image = true)
                return candidate
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Try the next declared icon or the conventional fallback. */ }
        }
        return null
    }

    private suspend fun fetch(url: String, limit: Long, image: Boolean): ByteArray = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        val body = it.body ?: throw IOException("Empty website response")
                        if (!it.isSuccessful || body.contentLength() > limit || image && body.contentType()?.type != "image") {
                            throw IOException("Invalid website response")
                        }
                        val source = body.source()
                        if (source.request(limit + 1) && source.buffer.size > limit) throw IOException("Website response too large")
                        source.readByteArray().takeIf { bytes -> bytes.isNotEmpty() && bytes.size <= limit }
                            ?: throw IOException("Invalid website response size")
                    }
                    if (continuation.isActive) continuation.resume(bytes)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
}
