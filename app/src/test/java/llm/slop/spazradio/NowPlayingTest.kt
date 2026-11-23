package llm.slop.spazradio

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same cases as the web TV's parsePlaying tests (liquid-lsd web/tools/schedule.test.mjs). */
class NowPlayingTest {
    private fun parse(json: String) = NowPlaying.parse(JsonParser.parseString(json).asJsonObject)

    @Test
    fun jukeboxShowHasItsFile() {
        val m = parse("""{"playing":"2026-10-22 - collages","listeners":3,"live":null,"download":"archives/imports/2026-10-22-collages.ogg"}""")
        assertEquals("2026-10-22 - collages", m.title)
        assertEquals(3, m.listeners)
        assertFalse(m.live)
        assertEquals("https://spaz.org/archives/imports/2026-10-22-collages.ogg", m.download)
    }

    @Test
    fun liveShowHasNoFile() {
        val m = parse("""{"playing":"[LIVE!] Spukkin Faceship&#039;s - bass","live":"true","download":"archives/imports/x.ogg"}""")
        assertTrue(m.live)
        assertEquals("", m.download)
        assertEquals("[LIVE!] Spukkin Faceship's - bass", m.title)
        assertTrue(parse("""{"playing":"Show","live":true,"download":"a.ogg"}""").live)
        assertFalse(parse("""{"playing":"Rerun","live":"false"}""").live)
    }

    @Test
    fun fileUrls() {
        assertEquals("https://spaz.org/archives/imports/x.ogg", NowPlaying.fileUrl("https://spaz.org/archives/imports/x.ogg"))
        assertEquals("", NowPlaying.fileUrl(null))
        assertEquals("", NowPlaying.fileUrl(""))
        assertEquals("", NowPlaying.fileUrl("javascript:alert(1)"))
        assertEquals("https://spaz.org/archives/imports/a%20b.ogg", NowPlaying.fileUrl("archives/imports/a b.ogg"))
        assertEquals("", parse("""{"playing":"x","download":null}""").download)
    }

    @Test
    fun missingFieldsFallBack() {
        val m = parse("{}")
        assertEquals("Radio Spaz", m.title)
        assertEquals(0, m.listeners)
        assertEquals(0, parse("""{"listeners":"lots"}""").listeners)
    }

    @Test
    fun entities() {
        assertEquals("Tom & Jerry's <\"x\">", NowPlaying.decodeEntities("Tom &amp; Jerry&#x27;s &lt;&quot;x&quot;&gt;"))
        assertEquals("&bogus; &#99999999;", NowPlaying.decodeEntities("&bogus; &#99999999;"))
    }
}
