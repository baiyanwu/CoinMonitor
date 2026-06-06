package io.baiyanwu.coinmonitor.data.network

import io.baiyanwu.coinmonitor.domain.model.NetworkLogProtocol
import io.baiyanwu.coinmonitor.domain.repository.NetworkLogRepository
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.nio.charset.Charset

/**
 * 统一收口 HTTP 请求/响应摘要。
 *
 * 这里会额外记录请求体预览，方便排查 AI 请求到底发出了什么内容；
 * 但不会主动读取响应体，避免影响 SSE/流式接口的正常消费。
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
                    appendLine("${header.first}: ${redactHeaderValue(header.first, header.second)}")
                }
            }
            buildRequestBodyPreview(request)?.let { bodyPreview ->
                appendLine("Body:")
                append(bodyPreview)
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
                        appendLine("${header.first}: ${redactHeaderValue(header.first, header.second)}")
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

    private fun redactHeaderValue(name: String, value: String): String {
        return if (name.equals("Authorization", ignoreCase = true)) {
            "Bearer ***"
        } else {
            value
        }
    }

    private fun buildRequestBodyPreview(request: okhttp3.Request): String? {
        val body = request.body ?: return null
        return runCatching {
            val buffer = Buffer()
            body.writeTo(buffer)
            val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            val rawText = buffer.readString(charset)
            truncateBodyPreview(rawText)
        }.getOrNull()
    }

    private fun truncateBodyPreview(body: String): String {
        if (body.isBlank()) return "(empty)"
        val normalized = body.trim()
        return if (normalized.length <= MAX_BODY_PREVIEW_LENGTH) {
            normalized
        } else {
            normalized.take(MAX_BODY_PREVIEW_LENGTH) + "\n...(truncated)"
        }
    }

    private companion object {
        private const val MAX_BODY_PREVIEW_LENGTH = 4000
    }
}
