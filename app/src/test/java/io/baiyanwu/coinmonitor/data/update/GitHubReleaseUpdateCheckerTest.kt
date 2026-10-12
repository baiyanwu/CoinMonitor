package io.baiyanwu.coinmonitor.data.update

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseUpdateCheckerTest {
    @Test
    fun `disabled checker never requests GitHub or offers a GitHub update`() = runBlocking {
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor {
            requests.incrementAndGet()
            throw IOException("A Play build must not check GitHub updates")
        }.build()

        val update = GitHubReleaseUpdateChecker(client, enabled = false).checkForUpdate("1.0.16")

        assertNull(update)
        assertEquals(0, requests.get())
    }

    @Test
    fun `GitHub channel keeps checking and linking newer releases`() = runBlocking {
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            assertEquals("api.github.com", chain.request().url.host)
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"tag_name":"v1.0.17"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        val update = GitHubReleaseUpdateChecker(client).checkForUpdate("1.0.16")

        assertEquals(1, requests.get())
        assertEquals("1.0.17", update?.versionName)
        assertEquals("https://github.com/baiyanwu/CoinMonitor/releases/latest", update?.releaseUrl)
    }
}
