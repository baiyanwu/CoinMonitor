package io.baiyanwu.coinmonitor.ui.components

import java.util.concurrent.CancellationException

/** 跨列表复用失败冷却，防止坏图在滚出/滚入后反复请求；仅保存有限数量的 URL。 */
internal class CoinIconFailureCooldown(
    private val retryDelayMillis: Long = 60_000,
    private val maximumEntries: Int = 256,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private val retryAt = linkedMapOf<String, Long>()

    init {
        require(retryDelayMillis > 0)
        require(maximumEntries > 0)
    }

    @Synchronized
    fun canRequest(url: String): Boolean {
        val deadline = retryAt[url] ?: return true
        if (clock() < deadline) return false
        retryAt.remove(url)
        return true
    }

    @Synchronized
    fun recordFailure(url: String, cause: Throwable? = null): Boolean {
        if (cause is CancellationException) return false
        retryAt.remove(url)
        retryAt[url] = clock() + retryDelayMillis
        while (retryAt.size > maximumEntries) retryAt.remove(retryAt.keys.first())
        return true
    }

    @Synchronized
    fun clear(url: String) { retryAt.remove(url) }
}
