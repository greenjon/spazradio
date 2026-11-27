package llm.slop.spazradio.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMessagesTest {
    private fun msg(user: String, text: String, t: Long) = ChatMessage(user, text, t)

    private fun live(json: String, now: Long) = ChatMessages.parse(JsonParser.parseString(json), now, trustTime = false)
    private fun logged(json: String, now: Long) = ChatMessages.parse(JsonParser.parseString(json), now, trustTime = true)
    private val now = 1_793_509_809_767L

    @Test
    fun liveMessageWithoutTimeGetsTheReceiveTime() {
        // Exactly what the broker relays: no time_received.
        assertEquals(msg("gj", "hi", 1_793_509_809L), live("""{"user":"gj","message":"hi"}""", now))
    }

    @Test
    fun liveMessageIgnoresATimeOnTheWire() {
        assertEquals(1_793_509_809L, live("""{"user":"gj","message":"hi","time_received":99999999999999999}""", now)!!.timeReceived)
        assertEquals(1_793_509_809L, live("""{"user":"gj","message":"hi","time_received":1}""", now)!!.timeReceived)
    }

    @Test
    fun historyTimeInMillisBecomesSeconds() {
        assertEquals(1_793_509_800L, logged("""{"user":"gj","message":"hi","time_received":1793509800000}""", now)!!.timeReceived)
    }

    @Test
    fun unbelievableHistoryTimesGetTheReceiveTime() {
        for (t in listOf("1e20", "99999999999999999", "0", "-5", "\"soon\"", "Infinity", "NaN", "null", "{}")) {
            assertEquals(t, 1_793_509_809L, logged("""{"user":"gj","message":"hi","time_received":$t}""", now)!!.timeReceived)
        }
        // Up to a day ahead is a clock difference, believed.
        val ahead = now + 86_000_000L
        assertEquals(ahead / 1000, logged("""{"user":"gj","message":"hi","time_received":$ahead}""", now)!!.timeReceived)
    }

    @Test
    fun payloadWithoutUserOrMessageIsDropped() {
        assertNull(live("""{"user":"gj"}""", 1L))
        assertNull(live("""{"message":"hi"}""", 1L))
        assertNull(live("""{"user":null,"message":"hi"}""", 1L))
        assertNull(live("""{"user":{"x":1},"message":"hi"}""", 1L))
        assertNull(live("""{"user":"gj","message":["hi"]}""", 1L))
        assertNull(live("""[1,2]""", 1L))
        assertNull(live(""""just a string"""", 1L))
        assertNull(ChatMessages.parse(null, 1L, trustTime = false))
    }

    @Test
    fun oversizedNamesAndTextsAreCut() {
        val m = live("""{"user":"${"n".repeat(500)}","message":"${"x".repeat(50_000)}"}""", now)!!
        assertEquals(ChatMessages.MAX_NAME, m.user.length)
        assertEquals(ChatMessages.MAX_TEXT, m.message.length)
    }

    @Test
    fun oneBadHistoryEntryCostsOnlyItself() {
        val body = """{"status":"OK","history":[
            {"user":"a","message":"1","time_received":1793509700000},
            {"user":"b","message":"2","time_received":Infinity},
            {"user":"c","message":"3","time_received":1e20},
            {"user":"d"},
            "junk",
            {"user":"e","message":"5","time_received":1793509800000}]}"""
        val history = ChatMessages.parseHistory(body, now)!!
        assertEquals(listOf("a", "b", "c", "e"), history.map { it.user })
        assertEquals(listOf(1_793_509_700L, 1_793_509_809L, 1_793_509_809L, 1_793_509_800L), history.map { it.timeReceived })
    }

    @Test
    fun historyThatIsNotOneFails() {
        assertNull(ChatMessages.parseHistory("<html>502</html>", now))
        assertNull(ChatMessages.parseHistory("""{"status":"OK"}""", now))
        assertNull(ChatMessages.parseHistory("""[]""", now))
    }

    @Test
    fun presenceNamesAreTrimmedAndCut() {
        assertEquals("gj", ChatMessages.presenceName("  gj \n"))
        assertEquals(ChatMessages.MAX_NAME, ChatMessages.presenceName("n".repeat(100_000)).length)
        assertEquals("", ChatMessages.presenceName("   "))
    }

    @Test
    fun mergeKeepsTheNewest() {
        val shown = (1..400L).map { msg("u", "$it", it * 1000) }
        val merged = ChatMessages.merge(shown, listOf(msg("u", "new", 999_999)))
        assertEquals(ChatMessages.MAX_MESSAGES, merged.size)
        assertEquals("new", merged.last().message)
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

    @Test
    fun parseStripsTagsAndDecodesEntities() {
        val m = live("""{"user":"<b>gj</b>","message":"<font color=\"#000\">hi</font> &lt;3 &amp; &#65;&#x42; <img src=x>"}""", now)!!
        assertEquals("gj", m.user)
        assertEquals("hi <3 & AB ", m.message)
    }

    @Test
    fun cleanTextLeavesBareAngleBracketsAndUnknownEntities() {
        assertEquals("1 < 2 and &bogus; &#0;", ChatMessages.cleanText("1 < 2 and &bogus; &#0;"))
    }

    @Test
    fun parseCapsAfterDecoding() {
        val m = live("""{"user":"${"u".repeat(100)}","message":"${"x".repeat(5000)}"}""", now)!!
        assertEquals(ChatMessages.MAX_NAME, m.user.length)
        assertEquals(ChatMessages.MAX_TEXT, m.message.length)
    }

    @Test
    fun nicknameAndOutgoingTextAreTrimmedAndCapped() {
        assertEquals("a b", ChatMessages.cleanNick("  a \t  b  "))
        assertEquals(ChatMessages.MAX_NAME, ChatMessages.cleanNick("n".repeat(80)).length)
        assertEquals("", ChatMessages.cleanNick("   "))
        assertEquals("hi", ChatMessages.outgoingText("  hi \n"))
        assertEquals(ChatMessages.MAX_TEXT, ChatMessages.outgoingText("x".repeat(3000)).length)
    }

    @Test
    fun rosterIsBoundedAndIgnoresNestedTopics() {
        var roster = emptyMap<String, String>()
        for (i in 0 until ChatMessages.MAX_ROSTER + 50) roster = ChatMessages.updateRoster(roster, "id$i", "n$i")
        assertEquals(ChatMessages.MAX_ROSTER, roster.size)
        // known ids still update and clear past the cap
        roster = ChatMessages.updateRoster(roster, "id0", "renamed")
        assertEquals("renamed", roster["id0"])
        roster = ChatMessages.updateRoster(roster, "id1", "")
        assertEquals(ChatMessages.MAX_ROSTER - 1, roster.size)
        assertEquals(roster, ChatMessages.updateRoster(roster, "a/b", "x"))
    }

    @Test
    fun rosterNamesDedupeAndSortWithoutCase() {
        val roster = mapOf("1" to "bob", "2" to "Bob", "3" to "alice", "4" to "Zed")
        assertEquals(listOf("alice", "bob", "Zed"), ChatMessages.rosterNames(roster))
    }
}
