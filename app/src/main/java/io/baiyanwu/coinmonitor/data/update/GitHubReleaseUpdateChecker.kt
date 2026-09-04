package io.baiyanwu.coinmonitor.data.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class AvailableAppUpdate(
    val versionName: String,
    val releaseUrl: String
)

class GitHubReleaseUpdateChecker(
    private val httpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(currentVersion: String): AvailableAppUpdate? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(LATEST_RELEASE_API_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2026-03-10")
                    .header("User-Agent", "CoinMonitor-Android/$currentVersion")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@runCatching null
                    }

                    val responseBody = response.body?.string() ?: return@runCatching null
                    val release = json.decodeFromString<LatestReleaseResponse>(responseBody)
                    if (!AppUpdatePolicy.isRemoteVersionNewer(release.tagName, currentVersion)) {
                        return@runCatching null
                    }

                    AvailableAppUpdate(
                        versionName = AppUpdatePolicy.normalizeVersion(release.tagName),
                        releaseUrl = LATEST_RELEASE_PAGE_URL
                    )
                }
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                null
            }
        }

    private companion object {
        const val LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/baiyanwu/CoinMonitor/releases/latest"
        const val LATEST_RELEASE_PAGE_URL =
            "https://github.com/baiyanwu/CoinMonitor/releases/latest"
    }
}

@Serializable
private data class LatestReleaseResponse(
    @SerialName("tag_name") val tagName: String
)

internal object AppUpdatePolicy {
    fun isRemoteVersionNewer(remoteTag: String, currentVersion: String): Boolean {
        val remoteParts = parseVersion(remoteTag) ?: return false
        val currentParts = parseVersion(currentVersion) ?: return false
        val partCount = maxOf(remoteParts.size, currentParts.size)

        repeat(partCount) { index ->
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (remotePart != currentPart) {
                return remotePart > currentPart
            }
        }
        return false
    }

    fun normalizeVersion(value: String): String {
        return value.trim().removePrefix("v").removePrefix("V")
    }

    private fun parseVersion(value: String): List<Int>? {
        val versionCore = normalizeVersion(value)
            .substringBefore('-')
            .substringBefore('+')
        if (versionCore.isBlank()) return null

        val parts = versionCore.split('.')
        if (parts.any { part -> part.isEmpty() || part.any { !it.isDigit() } }) return null
        return parts.map { it.toIntOrNull() ?: return null }
    }
}
