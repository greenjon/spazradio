package llm.slop.spazradio

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes

/** Which live mount to play. Auto takes the low one on a metered network (Android counts most mobile data as metered). */
enum class AudioQuality { AUTO, LOW, HIGH }

/** The live radio stream and the setting that picks its mount. */
object LiveStream {
    const val MEDIA_ID = "spaz_radio_stream"
    const val URL_HIGH = "https://radio.spaz.org:8060/radio.ogg"
    const val URL_LOW = "https://radio.spaz.org:8060/radio-low.ogg"
    const val PREFS = "spaz_radio_prefs"
    const val QUALITY_PREF = "audio_quality"

    fun urlFor(quality: AudioQuality, metered: Boolean): String = when (quality) {
        AudioQuality.HIGH -> URL_HIGH
        AudioQuality.LOW -> URL_LOW
        AudioQuality.AUTO -> if (metered) URL_LOW else URL_HIGH
    }

    fun savedQuality(prefs: SharedPreferences): AudioQuality =
        AudioQuality.entries.firstOrNull { it.name == prefs.getString(QUALITY_PREF, null) } ?: AudioQuality.HIGH

    /** The item for right now. RadioService swaps it if the network or the setting changes after this. */
    fun mediaItem(context: Context): MediaItem {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return MediaItem.Builder()
            .setUri(urlFor(savedQuality(prefs), connectivity.isActiveNetworkMetered))
            .setMediaId(MEDIA_ID)
            .setMimeType(MimeTypes.AUDIO_OGG)
            .build()
    }
}
