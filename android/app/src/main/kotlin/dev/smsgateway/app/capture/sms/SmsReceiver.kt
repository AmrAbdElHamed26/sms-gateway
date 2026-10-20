package dev.smsgateway.app.capture.sms

import dev.smsgateway.app.capture.CapturedEvent
import dev.smsgateway.app.capture.EventIngestor
import dev.smsgateway.app.config.GatewaySettings
import dev.smsgateway.app.queue.EventSource
import dev.smsgateway.app.queue.SourceType
import dev.smsgateway.app.util.Hashing
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import java.util.concurrent.Executors

/**
 * Optional SMS module. The component is disabled in the manifest and only
 * enabled by [SmsModule] when the operator turns SMS monitoring on, so with
 * the module off Android never even delivers SMS broadcasts to this app.
 * Never logs sender or body; never reads the inbox; never modifies messages.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val receivedAtMs = System.currentTimeMillis()

        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull()
            ?.filterNotNull()
            ?.map { SmsPart(it.originatingAddress ?: it.displayOriginatingAddress, it.messageBody, it.timestampMillis) }
            .orEmpty()
        if (parts.isEmpty()) return

        // goAsync keeps the process alive while the DB write runs off the main thread.
        val pending = goAsync()
        val appContext = context.applicationContext
        io.execute {
            try {
                GatewaySettings(appContext).lastSmsReceivedAt = receivedAtMs
                for (sms in SmsAssembler.assemble(parts)) {
                    val outcome = EventIngestor.ingest(appContext, capturedEvent(sms), receivedAtMs)
                    Log.i(TAG, "Incoming SMS (${parts.size} part(s)): ${outcome.label}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to persist incoming SMS: ${t.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
        private val io = Executors.newSingleThreadExecutor()

        fun capturedEvent(sms: AssembledSms, source: String = EventSource.ANDROID_SMS) = CapturedEvent(
            sourceType = SourceType.SMS,
            source = source,
            sourcePackage = null,
            sender = sms.sender,
            title = null,
            body = sms.body,
            originTimestampMs = sms.timestampMs,
            dedupeKey = dedupeKey(sms.sender, sms.body, sms.timestampMs),
        )

        /** sender + body + service-centre timestamp: a re-delivered broadcast maps to the same key. */
        fun dedupeKey(sender: String, body: String, smsTimestampMs: Long): String =
            Hashing.sha256Hex("sms\u0000$sender\u0000$body\u0000$smsTimestampMs")
    }
}
