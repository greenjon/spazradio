package llm.slop.spazradio

import llm.slop.spazradio.utils.LogCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleParserTest {
    @Test
    fun badRowsAreSkippedNotFatal() {
        val shows = ScheduleParser.parse(
            """[
              {"start_timestamp":1000,"end_timestamp":2000,"name":"Good","url":"u"},
              {"start_timestamp":1000,"end_timestamp":2000,"url":"u"},
              {"start_timestamp":"x","end_timestamp":2000,"name":"Bad type"},
              {"start_timestamp":3000,"end_timestamp":3000,"name":"Empty span"},
              null,
              5
            ]"""
        )
        assertEquals(listOf("Good", ScheduleParser.FALLBACK_NAME), shows.map { it.name })
    }

    @Test
    fun nonArrayBodyIsAnError() {
        for (body in listOf("{}", "null", "\"x\"")) {
            val failed = try { ScheduleParser.parse(body); false } catch (e: IllegalArgumentException) { true }
            assertTrue(body, failed)
        }
    }

    @Test
    fun logsDropTheNickname() {
        val out = LogCollector.redact("11-26 D/ChatRepo: Published presence: Bob (Online: true)\nother line")
        assertEquals("11-26 D/ChatRepo: Published presence: <redacted>\nother line", out)
    }
}
