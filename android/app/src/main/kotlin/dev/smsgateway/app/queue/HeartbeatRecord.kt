package dev.smsgateway.app.queue

/**
 * One heartbeat attempt. [dueAtMs] is when it was scheduled to go out, so
 * `sentAtMs - dueAtMs` is how long Android (Doze, OEM battery managers) held it.
 */
data class HeartbeatRecord(
    val id: Long = 0,
    val trigger: String,
    val dueAtMs: Long,
    val sentAtMs: Long,
    val httpStatus: Int?,
    val error: String?,
    val latencyMs: Long,
) {
    val ok: Boolean get() = httpStatus in 200..299

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "trigger" to trigger,
        "dueAt" to dueAtMs,
        "sentAt" to sentAtMs,
        "httpStatus" to httpStatus,
        "error" to error,
        "latencyMs" to latencyMs,
        "ok" to ok,
    )

    companion object {
        const val TRIGGER_ALARM = "alarm"
        const val TRIGGER_MANUAL = "manual"
    }
}
