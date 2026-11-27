package llm.slop.spazradio.data

import android.content.Context
import android.util.Xml
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class ArchiveRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val gson: Gson = Gson()
) {
    private val cacheFile = File(context.cacheDir, "archives_cache.json")
    private val inputDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
    private val outputDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    fun getCachedArchives(): List<ArchiveShow> {
        return try {
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                val type = object : TypeToken<List<ArchiveShow>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun saveArchivesToCache(shows: List<ArchiveShow>) {
        try {
            val json = gson.toJson(shows)
            cacheFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
                // Enabling namespace processing to correctly handle itunes: tags
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                try {
                    // Tolerate undefined entities such as &nbsp; in titles.
                    parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true)
                } catch (_: Exception) {
                }
                parser.setInput(body.byteStream(), null)
                shows.addAll(parseArchiveFeed(parser, ::formatDate))
            }

            if (shows.isNotEmpty()) {
                saveArchivesToCache(shows)
            }
        } catch (e: Exception) {
            // A feed that can't be read at all counts as a failed fetch: the caller keeps its list.
            e.printStackTrace()
            return@withContext emptyList()
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

private const val ITUNES_NAMESPACE = "http://www.itunes.com/dtds/podcast-1.0.dtd"

/** Text of the current element, tolerating child tags (their text is kept, the tags dropped). */
private fun readText(parser: XmlPullParser): String {
    val sb = StringBuilder()
    var depth = 1
    while (depth > 0) {
        when (parser.next()) {
            XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> sb.append(parser.text ?: "")
            XmlPullParser.START_TAG -> depth++
            XmlPullParser.END_TAG -> depth--
            XmlPullParser.END_DOCUMENT -> return sb.toString()
        }
    }
    return sb.toString()
}

/**
 * Reads the shows from an RSS feed. Each `<item>` is parsed on its own: one that throws is skipped and
 * the items after it are still read. Throws only when the parser cannot continue past a bad item.
 */
internal fun parseArchiveFeed(parser: XmlPullParser, formatDate: (String?) -> String): List<ArchiveShow> {
    val shows = mutableListOf<ArchiveShow>()
    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
            try {
                parseItem(parser, formatDate)?.let { shows.add(it) }
            } catch (e: Exception) {
                e.printStackTrace()
                skipToItemEnd(parser) // throws if the parser is unusable
            }
        }
        eventType = parser.next()
    }
    return shows
}

private fun skipToItemEnd(parser: XmlPullParser) {
    var depth = 1
    while (depth > 0) {
        when (parser.next()) {
            XmlPullParser.START_TAG -> depth++
            XmlPullParser.END_TAG -> depth--
            XmlPullParser.END_DOCUMENT -> return
        }
    }
}

/** Reads one item, ending on its END_TAG. Returns null when it lacks a title or an enclosure URL. */
private fun parseItem(parser: XmlPullParser, formatDate: (String?) -> String): ArchiveShow? {
    var title: String? = null
    var url: String? = null
    var pubDate: String? = null
    var imageUrl: String? = null
    var duration: String? = null
    var depth = 1
    while (depth > 0) {
        when (parser.next()) {
            XmlPullParser.START_TAG -> {
                val isItunes = parser.namespace == ITUNES_NAMESPACE
                when {
                    parser.name == "title" && depth == 1 -> {
                        val t = readText(parser)
                        if (title == null) title = t
                    }
                    parser.name == "pubDate" && depth == 1 -> pubDate = readText(parser)
                    parser.name == "enclosure" -> {
                        url = parser.getAttributeValue(null, "url")
                        depth++
                    }
                    parser.name == "image" && isItunes -> {
                        imageUrl = parser.getAttributeValue(null, "href")
                        depth++
                    }
                    parser.name == "duration" && isItunes && depth == 1 -> duration = readText(parser)
                    else -> depth++
                }
            }
            XmlPullParser.END_TAG -> depth--
            XmlPullParser.END_DOCUMENT -> return null
        }
    }
    val t = title
    val u = url
    if (t == null || u == null) return null
    return ArchiveShow(
        title = t,
        url = u,
        date = formatDate(pubDate),
        originalDate = pubDate ?: "",
        imageUrl = imageUrl,
        duration = duration
    )
}
