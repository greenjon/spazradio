package llm.slop.spazradio

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveStreamTest {
    private val low = "https://radio.spaz.org:8060/radio-low.ogg"
    private val high = "https://radio.spaz.org:8060/radio.ogg"

    @Test
    fun autoFollowsTheNetwork() {
        assertEquals(low, LiveStream.urlFor(AudioQuality.AUTO, metered = true))
        assertEquals(high, LiveStream.urlFor(AudioQuality.AUTO, metered = false))
    }

    @Test
    fun lowAndHighIgnoreTheNetwork() {
        assertEquals(low, LiveStream.urlFor(AudioQuality.LOW, metered = false))
        assertEquals(high, LiveStream.urlFor(AudioQuality.HIGH, metered = true))
    }
}
