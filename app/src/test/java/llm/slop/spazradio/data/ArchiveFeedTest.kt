package llm.slop.spazradio.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

class ArchiveFeedTest {

    private fun parse(xml: String): List<ArchiveShow> {
        // The same setup as ArchiveRepository.fetchArchiveFeed (Android's Xml.newPullParser is kxml too).
        val parser = KXmlParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true)
        parser.setInput(xml.reader())
        return parseArchiveFeed(parser) { it ?: "" }
    }

    private fun item(title: String, url: String?) =
        "<item><title>$title</title>" + (url?.let { "<enclosure url=\"$it\" type=\"audio/ogg\"/>" } ?: "") + "</item>"

    private fun feed(vararg items: String) =
        "<?xml version=\"1.0\"?><rss xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\"><channel>" +
            "<title>Spaz archives</title>" + items.joinToString("") + "</channel></rss>"

    @Test
    fun readsEveryItem() {
        val shows = parse(feed(item("One", "https://spaz.org/1.ogg"), item("Two", "https://spaz.org/2.ogg")))
        assertEquals(listOf("One", "Two"), shows.map { it.title })
    }

    @Test
    fun anUndefinedEntityDoesNotCutTheListShort() {
        val shows = parse(feed(item("A&nbsp;B", "https://spaz.org/1.ogg"), item("After", "https://spaz.org/2.ogg")))
        assertEquals(2, shows.size)
        assertEquals("After", shows[1].title)
    }

    @Test
    fun aTagInsideATitleKeepsItsText() {
        val shows = parse(feed(item("Live <b>loud</b> set", "https://spaz.org/1.ogg"), item("Next", "https://spaz.org/2.ogg")))
        assertEquals(listOf("Live loud set", "Next"), shows.map { it.title })
    }

    @Test
    fun anItemWithoutAnEnclosureIsSkipped() {
        val shows = parse(feed(item("No file", null), item("Has file", "https://spaz.org/2.ogg")))
        assertEquals(listOf("Has file"), shows.map { it.title })
    }
}
