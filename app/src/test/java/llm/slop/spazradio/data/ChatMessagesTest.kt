package llm.slop.spazradio.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMessagesTest {
    private val gson = Gson()
    private fun msg(user: String, text: String, t: Long) = ChatMessage(user, text, t)

    @Test
    fun liveMessageWithoutTimeGetsTheReceiveTime() {
        // Exactly what the broker relays: no time_received.
        val raw = gson.fromJson("""{"user":"gj","message":"hi"}""", ChatMessage::class.java)
        assertEquals(msg("gj", "hi", 1_793_509_809L), ChatMessages.normalize(raw, 1_793_509_809_767L))
    }

    @Test
    fun historyTimeInMillisBecomesSeconds() {
        val raw = gson.fromJson("""{"user":"gj","message":"hi","time_received":1793509809767}""", ChatMessage::class.java)
        assertEquals(1_793_509_809L, ChatMessages.normalize(raw, 0L)!!.timeReceived)
    }

    @Test
    fun payloadWithoutUserOrMessageIsDropped() {
        assertNull(ChatMessages.normalize(gson.fromJson("""{"user":"gj"}""", ChatMessage::class.java), 1L))
        assertNull(ChatMessages.normalize(gson.fromJson("""{"message":"hi"}""", ChatMessage::class.java), 1L))
    }

    @Test
    fun firstLoadIsTheHistory() {
        val history = listOf(msg("a", "1", 100), msg("b", "2", 200))
        assertEquals(history, ChatMessages.merge(emptyList(), history))
    }

    @Test
    fun failedFetchKeepsWhatIsShown() {
        val shown = listOf(msg("a", "1", 100))
        assertEquals(shown, ChatMessages.merge(shown, emptyList()))
    }

    @Test
    fun reconnectFillsTheGapWithoutDuplicates() {
        // Shown: history from launch, then a live copy stamped by the phone 3 s off the server's stamp.
        val shown = listOf(msg("a", "1", 100), msg("b", "2", 200), msg("c", "live", 303))
        // Re-fetched after a dropout: the live one, plus two sent while offline.
        val history = listOf(msg("b", "2", 200), msg("c", "live", 300), msg("d", "missed", 400), msg("e", "missed too", 500))
        assertEquals(
            listOf(msg("a", "1", 100)) + history,
            ChatMessages.merge(shown, history),
        )
    }

    @Test
    fun repeatedTextKeepsEveryCopy() {
        val shown = listOf(msg("a", "lol", 100), msg("a", "lol", 110))
        val history = listOf(msg("a", "lol", 100), msg("a", "lol", 110), msg("a", "lol", 120))
        assertEquals(history, ChatMessages.merge(shown, history))
    }

    @Test
    fun messageArrivingAfterTheLogWasReadStaysAtTheEnd() {
        val shown = listOf(msg("a", "1", 100), msg("z", "just now", 900))
        val history = listOf(msg("a", "1", 100))
        assertEquals(shown, ChatMessages.merge(shown, history))
    }

    @Test
    fun sameTextFarApartIsNotMatched() {
        val shown = listOf(msg("a", "hello", 100))
        val history = listOf(msg("a", "hello", 100 + ChatMessages.MATCH_WINDOW_SECONDS + 1000))
        assertEquals(shown + history, ChatMessages.merge(shown, history))
    }
}
