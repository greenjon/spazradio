package llm.slop.spazradio.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale

class ArchiveRepository(private val client: OkHttpClient) {

    private val inputDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
    private val outputDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    suspend fun fetchArchiveFeed(): List<ArchiveShow> = withContext(Dispatchers.IO) {
        val shows = mutableListOf<ArchiveShow>()
        val request = Request.Builder()
            .url("https://spaz.org/archives/archives.rss")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body ?: return@withContext emptyList()
                
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(body.byteStream(), null)

                var eventType = parser.eventType
                var currentTitle: String? = null
                var currentUrl: String? = null
                var currentPubDate: String? = null
                var currentImageUrl: String? = null

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (name) {
                                "item" -> {
                                    currentTitle = null
                                    currentUrl = null
                                    currentPubDate = null
                                    currentImageUrl = null
                                }
                                "title" -> {
                                    if (currentTitle == null) currentTitle = parser.nextText()
                                }
                                "pubDate" -> {
                                    currentPubDate = parser.nextText()
                                }
                                "enclosure" -> {
                                    currentUrl = parser.getAttributeValue(null, "url")
                                }
                                "itunes:image" -> {
                                    currentImageUrl = parser.getAttributeValue(null, "href")
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (name == "item" && currentTitle != null && currentUrl != null) {
                                val formattedDate = formatDate(currentPubDate)
                                shows.add(
                                    ArchiveShow(
                                        title = currentTitle,
                                        url = currentUrl,
                                        date = formattedDate,
                                        originalDate = currentPubDate ?: "",
                                        imageUrl = currentImageUrl
                                    )
                                )
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        shows
    }

    private fun formatDate(rawDate: String?): String {
        if (rawDate == null) return ""
        return try {
            val date = inputDateFormat.parse(rawDate)
            if (date != null) outputDateFormat.format(date) else rawDate
        } catch (e: Exception) {
            rawDate
        }
    }
}
