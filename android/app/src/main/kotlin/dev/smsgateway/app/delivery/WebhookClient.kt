package dev.smsgateway.app.delivery

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** [detail] = the backend's validation summary for a 4xx, e.g. "body.sender: Input should be a valid string". */
data class WebhookResponse(val httpStatus: Int?, val error: String?, val latencyMs: Long, val detail: String? = null)

/** Plain HttpURLConnection — no extra dependency, HTTPS enforced. */
class WebhookClient(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 20_000,
) {
    fun post(url: String, deviceId: String, secret: String, eventId: String, json: String): WebhookResponse {
        val started = System.currentTimeMillis()
        var connection: HttpURLConnection? = null
        return try {
            val target = URL(url)
            require(target.protocol == "https") { "backend URL must use https" }
            val bytes = json.toByteArray(Charsets.UTF_8)
            val timestampSec = System.currentTimeMillis() / 1000

            connection = (target.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                useCaches = false
                // A redirect would re-send the body to an unconfigured host.
                instanceFollowRedirects = false
                setFixedLengthStreamingMode(bytes.size)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty(RequestSigner.HEADER_DEVICE_ID, deviceId)
                setRequestProperty(RequestSigner.HEADER_TIMESTAMP, timestampSec.toString())
                setRequestProperty(RequestSigner.HEADER_SIGNATURE, RequestSigner.sign(secret, timestampSec, json))
                setRequestProperty(RequestSigner.HEADER_IDEMPOTENCY_KEY, eventId)
            }
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            // Drain so the connection can be reused; a 4xx body is kept only as a field-level summary.
            val responseBody = runCatching {
                (if (status >= 400) connection.errorStream else connection.inputStream)
                    ?.use { it.readBytes().take(MAX_ERROR_BYTES).toByteArray().toString(Charsets.UTF_8) }
            }.getOrNull()
            val detail = if (status in 400..499) responseBody?.let(ErrorDetail::summarize) else null
            WebhookResponse(status, null, System.currentTimeMillis() - started, detail)
        } catch (e: IOException) {
            WebhookResponse(null, describe(e), System.currentTimeMillis() - started)
        } catch (e: IllegalArgumentException) {
            WebhookResponse(null, e.message, System.currentTimeMillis() - started)
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val MAX_ERROR_BYTES = 8 * 1024
    }

    private fun describe(e: IOException): String =
        (e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")).take(200)
}
