package dev.smsgateway.app.delivery

import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns a 4xx response body into a short, content-free reason for the
 * dashboard. For FastAPI validation errors it keeps only each error's
 * location and message, never the echoed `input` (which may hold the SMS text).
 */
object ErrorDetail {
    private const val MAX_LENGTH = 200

    fun summarize(body: String): String? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val summary = when (val detail = json.opt("detail")) {
            is JSONArray -> (0 until detail.length())
                .mapNotNull { detail.optJSONObject(it) }
                .joinToString("; ") { error ->
                    val loc = error.optJSONArray("loc")?.let { loc -> (0 until loc.length()).joinToString(".") { loc.get(it).toString() } }
                    listOfNotNull(loc, error.optString("msg").ifEmpty { null }).joinToString(": ")
                }
            is String -> detail
            is JSONObject -> stringsOf(detail)
            else -> listOf("message", "error", "code").firstNotNullOfOrNull { key ->
                json.opt(key)?.let { if (it is JSONObject) stringsOf(it) else it.toString() }
            }
        }
        return summary?.takeIf { it.isNotBlank() }?.take(MAX_LENGTH)
    }

    /** Top-level string values of a custom error object, skipping any echoed input. */
    private fun stringsOf(obj: JSONObject): String? =
        obj.keys().asSequence()
            .filter { it != "input" && it != "body" }
            .mapNotNull { key -> (obj.opt(key) as? String)?.let { "$key: $it" } }
            .joinToString("; ")
            .ifEmpty { null }
}
