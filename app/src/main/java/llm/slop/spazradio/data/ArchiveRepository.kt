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

    private val ITUNES_NAMESPACE = "http://www.itunes.com/dtds/podcast-1.0.dtd"

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
                parser.setInput(body.byteStream(), null)

                var eventType = parser.eventType
                var currentTitle: String? = null
                var currentUrl: String? = null
                var currentPubDate: String? = null
                var currentImageUrl: String? = null
                var currentDuration: String? = null

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name
                    val namespace = parser.namespace
                    
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (name) {
                                "item" -> {
                                    currentTitle = null
                                    currentUrl = null
                                    currentPubDate = null
                                    currentImageUrl = null
                                    currentDuration = null
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
                                "image" -> {
                                    if (namespace == ITUNES_NAMESPACE) {
                                        currentImageUrl = parser.getAttributeValue(null, "href")
                                    }
                                }
                                "duration" -> {
                                    if (namespace == ITUNES_NAMESPACE) {
                                        currentDuration = parser.nextText()
                                    }
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
                                        imageUrl = currentImageUrl,
                                        duration = currentDuration
                                    )
                                )
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
            
            if (shows.isNotEmpty()) {
                saveArchivesToCache(shows)
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
