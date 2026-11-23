package llm.slop.spazradio

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.net.URI

/**
 * What radio.spaz.org/playing says is on the stream.
 *
 * [download] is the jukebox show's file on spaz.org, or "" while a show is live (a live show has no file yet).
 */
data class RadioMetadata(
    val title: String = "",
    val listeners: Int = 0,
    val live: Boolean = false,
    val download: String = ""
)

/** Reads /playing the same way the web TV's parsePlaying does (liquid-lsd web/schedule.js). */
object NowPlaying {
    private const val SITE = "https://spaz.org/"

    fun parse(json: JsonObject): RadioMetadata {
        val live = json.get("live").let { it.isTrue() }
        val title = json.get("playing").stringOrNull()?.let(::decodeEntities) ?: "Radio Spaz"
        val listeners = try {
            json.get("listeners")?.takeUnless { it.isJsonNull }?.asInt ?: 0
        } catch (_: Exception) {
            0
        }
        val download = if (live) "" else fileUrl(json.get("download").stringOrNull())
        return RadioMetadata(title, listeners, live, download)
    }

    /** A path or URL from the server as an http(s) URL on spaz.org's terms, or "" for anything else. */
    fun fileUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return try {
            val url = URI(SITE).resolve(path.trim().replace(" ", "%20"))
            if (url.scheme == "https" || url.scheme == "http") url.toString() else ""
        } catch (_: Exception) {
            ""
        }
    }

    private val ENTITY = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|amp|lt|gt|quot|apos);")

    /** The server sends titles with HTML entities in them (`Faceship&#039;s`). */
    fun decodeEntities(s: String): String = ENTITY.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> e.drop(2).toIntOrNull(16)?.let(::codePoint)
            e.startsWith("#") -> e.drop(1).toIntOrNull()?.let(::codePoint)
            e == "amp" -> "&"
            e == "lt" -> "<"
            e == "gt" -> ">"
            e == "quot" -> "\""
            else -> "'"
        } ?: m.value
    }

    private fun codePoint(cp: Int): String? =
        if (Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else null

    private fun JsonElement?.stringOrNull(): String? =
        if (this == null || isJsonNull || !isJsonPrimitive) null else asString

    private fun JsonElement?.isTrue(): Boolean =
        this != null && isJsonPrimitive && asJsonPrimitive.let {
            (it.isBoolean && it.asBoolean) || (it.isString && it.asString == "true")
        }
}

/** The jukebox show playing on the stream, as [RadioViewModel] offers it for download. */
data class JukeboxFile(val title: String, val url: String)
