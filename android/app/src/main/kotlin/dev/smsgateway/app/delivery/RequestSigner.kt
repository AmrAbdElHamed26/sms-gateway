package dev.smsgateway.app.delivery

import dev.smsgateway.app.util.Hashing

/**
 * Device authentication: HMAC-SHA256 over "<unix-seconds>.<raw body>". The
 * secret itself is never transmitted; the timestamp lets the backend reject
 * replays outside its tolerance window.
 */
object RequestSigner {
    const val HEADER_DEVICE_ID = "X-Gateway-Device-Id"
    const val HEADER_TIMESTAMP = "X-Gateway-Timestamp"
    const val HEADER_SIGNATURE = "X-Gateway-Signature"
    const val HEADER_IDEMPOTENCY_KEY = "Idempotency-Key"

    fun sign(secret: String, timestampSec: Long, body: String): String =
        "sha256=" + Hashing.hmacSha256Hex(secret, "$timestampSec.$body")
}
