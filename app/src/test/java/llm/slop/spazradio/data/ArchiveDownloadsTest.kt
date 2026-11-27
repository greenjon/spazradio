package llm.slop.spazradio.data

import llm.slop.spazradio.JukeboxFile
import llm.slop.spazradio.findInArchive
import llm.slop.spazradio.utils.DownloadTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveDownloadsTest {

    @Test
    fun secureUrlOnlyAllowsSpazOrg() {
        assertEquals("https://spaz.org/a.ogg", DownloadTracker.secureUrl("http://spaz.org/a.ogg"))
        assertEquals("https://radio.spaz.org/a.ogg", DownloadTracker.secureUrl("https://radio.spaz.org/a.ogg"))
        assertNull(DownloadTracker.secureUrl("https://evil.example/a.ogg"))
        assertNull(DownloadTracker.secureUrl("https://notspaz.org/a.ogg"))
        assertNull(DownloadTracker.secureUrl("ftp://spaz.org/a.ogg"))
    }

    @Test
    fun findInArchiveComparesDecodedNames() {
        val show = ArchiveShow(title = "t", url = "https://spaz.org/archives/imports/My Show (1).ogg", date = "", originalDate = "")
        val file = JukeboxFile(url = "https://spaz.org/archives/olde/My%20Show%20(1).ogg", title = "My Show (1)")
        assertNotNull(findInArchive(file, listOf(show)))
    }
}
