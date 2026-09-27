package io.baiyanwu.coinmonitor.data.network

import io.baiyanwu.coinmonitor.domain.model.NetworkLogProtocol
import io.baiyanwu.coinmonitor.domain.model.NetworkLogEventKind
import io.baiyanwu.coinmonitor.domain.repository.NetworkLogRepository
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.nio.charset.Charset
import java.util.Locale

internal object NetworkLogRedactor {
    private val sensitiveHeaderNames = setOf(
        "authorization",
        "proxy-authorization",
        "cookie",
        "set-cookie",
        "x-api-key",
        "ok-access-key",
        "ok-access-sign",
        "ok-access-passphrase"
    )
    private val sensitiveJsonFieldPattern = Regex(
        pattern = """("(?:apiKey|secretKey|passphrase|sign|accessKey|accessSign|accessPassphrase|authorization)"\s*:\s*")[^"]*(")""",
        option = RegexOption.IGNORE_CASE
    )
    private val sensitiveUrlPathPatterns = listOf(
        Regex("(/v2/)[^/?#]+", RegexOption.IGNORE_CASE),
        Regex("(/data/v1/)[^/?#]+", RegexOption.IGNORE_CASE),
        Regex("(/prices/v1/)[^/?#]+", RegexOption.IGNORE_CASE)
    )
    private val sensitiveUrlQueryPattern = Regex("([?&](?:apiKey|key)=)[^&#]+", RegexOption.IGNORE_CASE)

    fun redactHeaderValue(name: String, value: String): String {
        return if (name.lowercase(Locale.ROOT) in sensitiveHeaderNames) {
            REDACTED_VALUE
        } else {
            value
        }
    }

    fun redactText(value: String): String {
        return sensitiveJsonFieldPattern.replace(value) { match ->
            "${match.groupValues[1]}$REDACTED_VALUE${match.groupValues[2]}"
        }
    }

    fun redactUrl(value: String): String {
        val pathRedacted = sensitiveUrlPathPatterns.fold(value) { current, pattern ->
            pattern.replace(current) { match -> "${match.groupValues[1]}$REDACTED_VALUE" }
        }
        return sensitiveUrlQueryPattern.replace(pathRedacted) { match ->
            "${match.groupValues[1]}$REDACTED_VALUE"
        }
    }

    private const val REDACTED_VALUE = "***"
}

/**
 * 统一收口 HTTP 请求/响应摘要。
 *
 * 请求体会记录脱敏预览；响应体只在非 2xx 或 JSON 含 error 时通过
 * peekBody 记录有限预览，不消费调用方实际读取的响应流。
 */
class NetworkLogInterceptor(
    private val networkLogRepository: NetworkLogRepository
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startAt = System.nanoTime()
        val safeUrl = NetworkLogRedactor.redactUrl(request.url.toString())
        val requestLine = "HTTP -> ${request.method} $safeUrl"
        val requestDetail = buildString {
            appendLine(requestLine)
            if (request.headers.size > 0) {
                appendLine("Headers:")
                request.headers.forEach { header ->
                    appendLine("${header.first}: ${NetworkLogRedactor.redactHeaderValue(header.first, header.second)}")
                }
            }
            buildRequestBodyPreview(request)?.let { bodyPreview ->
                appendLine("Body:")
                append(bodyPreview)
            }
        }.trim()
        networkLogRepository.append(
            protocol = NetworkLogProtocol.HTTP,
            kind = NetworkLogEventKind.HTTP_REQUEST,
            line = requestLine,
            detail = requestDetail
        )

        return try {
            val response = chain.proceed(request)
            val durationMs = (System.nanoTime() - startAt) / 1_000_000
            val responseLine = "HTTP <- ${response.code} ${request.method} $safeUrl ${durationMs}ms"
            val errorPreview = responseErrorPreview(response)
            val responseDetail = buildString {
                appendLine(responseLine)
                appendLine("Message: ${response.message}")
                if (response.headers.size > 0) {
                    appendLine("Headers:")
                    response.headers.forEach { header ->
                        appendLine("${header.first}: ${NetworkLogRedactor.redactHeaderValue(header.first, header.second)}")
                    }
                }
                errorPreview?.let { preview ->
                    appendLine("Body:")
                    append(preview)
                }
            }.trim()
            networkLogRepository.append(
                protocol = NetworkLogProtocol.HTTP,
                kind = if (response.isSuccessful) {
                    NetworkLogEventKind.HTTP_RESPONSE
                } else {
                    NetworkLogEventKind.HTTP_FAILURE
                },
                line = responseLine,
                detail = responseDetail
            )
            response
        } catch (error: IOException) {
            val durationMs = (System.nanoTime() - startAt) / 1_000_000
            val failureLine = "HTTP xx ${request.method} $safeUrl ${durationMs}ms ${error.javaClass.simpleName}"
            val failureDetail = buildString {
                appendLine(failureLine)
                append(NetworkLogRedactor.redactText(NetworkLogRedactor.redactUrl(error.stackTraceToString())))
            }.trim()
            networkLogRepository.append(
                protocol = NetworkLogProtocol.HTTP,
                kind = NetworkLogEventKind.HTTP_FAILURE,
                line = failureLine,
                detail = failureDetail
            )
            throw error
        }
    }

    private fun buildRequestBodyPreview(request: okhttp3.Request): String? {
        val body = request.body ?: return null
        return runCatching {
            val buffer = Buffer()
            body.writeTo(buffer)
            val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            val rawText = buffer.readString(charset)
            truncateBodyPreview(NetworkLogRedactor.redactText(rawText))
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

    private fun responseErrorPreview(response: Response): String? {
        val contentType = response.body?.contentType()?.toString().orEmpty()
        if (!contentType.contains("json", ignoreCase = true) && response.isSuccessful) return null
        val preview = runCatching { response.peekBody(MAX_BODY_PREVIEW_LENGTH.toLong()).string() }.getOrNull()
            ?.trim()
            .orEmpty()
        if (preview.isBlank()) return null
        if (response.isSuccessful && !preview.contains("\"error\"", ignoreCase = true)) return null
        return truncateBodyPreview(NetworkLogRedactor.redactUrl(NetworkLogRedactor.redactText(preview)))
    }

    private companion object {
        private const val MAX_BODY_PREVIEW_LENGTH = 4000
    }
}
