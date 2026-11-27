package llm.slop.spazradio

import llm.slop.spazradio.data.ArchiveShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The feed half of [ArchiveViewModel.jukeboxShow]; the HEAD half needs the network. */
class JukeboxShowTest {
    private fun show(url: String) = ArchiveShow(title = url, url = url, date = "", originalDate = "")
    private val feed = listOf(
        show("https://spaz.org/archives/imports/2019-03-09-_Stones_boneS_.ogg"),
        show("https://spaz.org/archives/imports/2026-10-22-collages.ogg")
    )

    @Test
    fun sameUrl() {
        val file = JukeboxFile("collages", "https://spaz.org/archives/imports/2026-10-22-collages.ogg")
        assertEquals(feed[1], findInArchive(file, feed))
    }

    @Test
    fun oldeFileFoundByNameInImports() {
        val file = JukeboxFile("Stones", "https://spaz.org/archives/olde/2019-03-09-_Stones_boneS_.ogg")
        assertEquals(feed[0], findInArchive(file, feed))
    }

    @Test
    fun notInFeed() {
        assertNull(findInArchive(JukeboxFile("x", "https://spaz.org/archives/olde/elsewhere.ogg"), feed))
        assertNull(findInArchive(JukeboxFile("x", "https://spaz.org/archives/olde/"), feed))
        assertNull(findInArchive(JukeboxFile("x", "https://spaz.org/archives/olde/elsewhere.ogg"), emptyList()))
    }
}
