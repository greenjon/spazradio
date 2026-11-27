package llm.slop.spazradio.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlin.math.abs

/**
 * Pure helpers for chat messages, kept apart from MQTT and Android so they can be unit tested.
 *
 * Wire facts (checked against the live broker 2026-10-31): a live message on the `spazradio` topic is exactly
 * what the sender published, `{"user","message"}` with no time. Only the server's chat log
 * (`/djdash/chatlog`) stamps `time_received`, in milliseconds.
 */
object ChatMessages {
    /** How far apart a live copy and the history copy of one message may be stamped (phone clock vs server). */
    const val MATCH_WINDOW_SECONDS = 120L
    /** The broker has no login, so anyone can publish anything: names and texts are cut to these lengths... */
    const val MAX_NAME = 32
    const val MAX_TEXT = 1000
    /** ...the board keeps the last this many messages (a flood can't grow it without end)... */
    const val MAX_MESSAGES = 300
    /** ...and a history time more than a day ahead of the phone's clock is not believed. */
    const val MAX_TIME_SKEW_MILLIS = 86_400_000L

    /**
     * One message from the wire or the history in the app's form (time in seconds), or null when it has no
     * string `user` and `message`. Each is checked here rather than by Gson, which would fail a whole history on
     * one bad time and leave nulls in non-null fields. The time is the history's `time_received` only with
     * [trustTime] and only when it is a number above 0 and at most [MAX_TIME_SKEW_MILLIS] ahead of [nowMillis];
     * anything else, and every live message (the wire carries no time, and a sender could add any), gets the
     * receive time. Same rules as the web TV (liquid-lsd web/chat.js normalize, TV v71).
     */
    fun parse(json: JsonElement?, nowMillis: Long, trustTime: Boolean): ChatMessage? {
        val o = json as? JsonObject ?: return null
        val user = o.string("user") ?: return null
        val message = o.string("message") ?: return null
        val wireTime = if (trustTime) o.millis("time_received") else null
        val millis = wireTime?.takeIf { it > 0 && it <= nowMillis + MAX_TIME_SKEW_MILLIS } ?: nowMillis
        return ChatMessage(user.take(MAX_NAME), message.take(MAX_TEXT), millis / 1000L)
    }

    /** The history body's entries, each parsed on its own so one bad entry costs only itself; null if it isn't one. */
    fun parseHistory(body: String, nowMillis: Long): List<ChatMessage>? {
        val entries = try {
            (JsonParser.parseString(body) as? JsonObject)?.get("history") as? JsonArray
        } catch (_: Exception) {
            null
        } ?: return null
        return entries.mapNotNull { parse(it, nowMillis, trustTime = true) }.takeLast(MAX_MESSAGES)
    }

    /** A presence name as shown: trimmed and cut to [MAX_NAME]; empty means gone. */
    fun presenceName(payload: String): String = payload.take(MAX_NAME * 4).trim().take(MAX_NAME)

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.asString

    private fun JsonObject.millis(key: String): Long? {
        val d = (get(key) as? JsonPrimitive)?.takeIf { it.isNumber }?.let {
            try { it.asDouble } catch (_: NumberFormatException) { null }
        } ?: return null
        return if (d.isFinite()) d.toLong() else null
    }

    /**
     * Merges a freshly fetched history into the messages already shown, so a reconnect fills in what was sent
     * while the connection was down without showing anything twice.
     *
     * Each shown message is matched to at most one history entry with the same user and text, stamped within
     * [MATCH_WINDOW_SECONDS], so a repeated "lol" keeps every copy. History only holds the last 100 messages,
     * so unmatched shown messages that come before the first match go first (older than the log), then the
     * history, then the other unmatched ones (sent after the log was read). With no match at all (a dropout
     * longer than the log), the shown messages are split by time against the log's oldest entry. Only the newest
     * [MAX_MESSAGES] are kept.
     */
    fun merge(current: List<ChatMessage>, history: List<ChatMessage>): List<ChatMessage> {
        if (history.isEmpty()) return current
        val used = BooleanArray(history.size)
        val matched = current.map { msg ->
            val i = history.indices.firstOrNull { j ->
                !used[j] && history[j].user == msg.user && history[j].message == msg.message &&
                    abs(history[j].timeReceived - msg.timeReceived) <= MATCH_WINDOW_SECONDS
            }
            if (i != null) used[i] = true
            i != null
        }
        val firstMatch = matched.indexOf(true)
        val oldest = history.first().timeReceived
        val older = mutableListOf<ChatMessage>()
        val newer = mutableListOf<ChatMessage>()
        current.forEachIndexed { k, msg ->
            if (matched[k]) return@forEachIndexed
            val isOlder = if (firstMatch >= 0) k < firstMatch else msg.timeReceived < oldest
            if (isOlder) older += msg else newer += msg
        }
        return (older + history + newer).takeLast(MAX_MESSAGES)
    }
}
