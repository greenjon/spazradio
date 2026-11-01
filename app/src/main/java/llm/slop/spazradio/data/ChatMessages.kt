package llm.slop.spazradio.data

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

    /**
     * Turns a parsed message into the app's form: time in seconds, and the receive time when the message has
     * none (every live message). Returns null for a payload without a user or message, which Gson would
     * otherwise hand over as null in non-null fields.
     */
    fun normalize(raw: ChatMessage, nowMillis: Long): ChatMessage? {
        @Suppress("SENSELESS_COMPARISON")
        if (raw.user == null || raw.message == null) return null
        val millis = if (raw.timeReceived > 0) raw.timeReceived else nowMillis
        return raw.copy(timeReceived = millis / 1000L)
    }

    /**
     * Merges a freshly fetched history into the messages already shown, so a reconnect fills in what was sent
     * while the connection was down without showing anything twice.
     *
     * Each shown message is matched to at most one history entry with the same user and text, stamped within
     * [MATCH_WINDOW_SECONDS], so a repeated "lol" keeps every copy. History only holds the last 100 messages,
     * so unmatched shown messages that come before the first match go first (older than the log), then the
     * history, then the other unmatched ones (sent after the log was read). With no match at all (a dropout
     * longer than the log), the shown messages are split by time against the log's oldest entry.
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
        return older + history + newer
    }
}
