package io.baiyanwu.coinmonitor.data.network

import io.baiyanwu.coinmonitor.domain.model.NetworkLogProtocol
import io.baiyanwu.coinmonitor.domain.repository.NetworkLogRepository
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 简单日志模式只记录请求摘要，不主动展开 body，避免影响正常请求性能。
 * 但会把方法、地址、状态码、耗时等核心信息统一收口到可观察仓库里。
 */
class NetworkLogInterceptor(
    private val networkLogRepository: NetworkLogRepository
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startAt = System.nanoTime()
        val requestLine = "HTTP -> ${request.method} ${request.url}"
        val requestDetail = buildString {
            appendLine(requestLine)
            if (request.headers.size > 0) {
                appendLine("Headers:")
                request.headers.forEach { header ->
                    appendLine("${header.first}: ${header.second}")
                }
            }
        }.trim()
        networkLogRepository.append(
            protocol = NetworkLogProtocol.HTTP,
            line = requestLine,
            detail = requestDetail
        )

        return try {
            val response = chain.proceed(request)
            val durationMs = (System.nanoTime() - startAt) / 1_000_000
            val responseLine = "HTTP <- ${response.code} ${request.method} ${request.url} ${durationMs}ms"
            val responseDetail = buildString {
                appendLine(responseLine)
                appendLine("Message: ${response.message}")
                if (response.headers.size > 0) {
                    appendLine("Headers:")
                    response.headers.forEach { header ->
                    appendLine("${header.first}: ${header.second}")
                }
            }
        }.trim()
            networkLogRepository.append(
                protocol = NetworkLogProtocol.HTTP,
                line = responseLine,
                detail = responseDetail
            )
            response
        } catch (error: IOException) {
            val durationMs = (System.nanoTime() - startAt) / 1_000_000
            val failureLine = "HTTP xx ${request.method} ${request.url} ${durationMs}ms ${error.javaClass.simpleName}"
            val failureDetail = buildString {
                appendLine(failureLine)
                append(error.stackTraceToString())
            }.trim()
            networkLogRepository.append(
                protocol = NetworkLogProtocol.HTTP,
                line = failureLine,
                detail = failureDetail
            )
            throw error
        }
    }
}
