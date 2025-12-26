package llm.slop.spazradio

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class TrackInfo(
    val artist: String = "",
    val title: String = "Connecting...",
    val listeners: Int = 0
)

class MetadataViewModel : ViewModel() {
    private val _trackInfo = MutableStateFlow(TrackInfo())
    val trackInfo: StateFlow<TrackInfo> = _trackInfo

    // Configure client to match RadioService (trust all certs/hosts)
    private val client: OkHttpClient = try {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    } catch (e: Exception) {
        OkHttpClient()
    }

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val trackInfo = fetchMetadata()
                    _trackInfo.value = trackInfo
                } catch (e: Exception) {
                    Log.e("MetadataViewModel", "Error in polling loop", e)
                }
                delay(10000) // Poll every 10 seconds
            }
        }
    }

    private fun fetchMetadata(): TrackInfo {
        try {
            val request = Request.Builder()
                .url("https://radio.spaz.org/playing")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return TrackInfo(title = "Radio Spaz")
                
                val body = response.body ?: return TrackInfo(title = "Radio Spaz")
                val jsonStr = body.string()
                
                val jsonObject = JsonParser.parseString(jsonStr).asJsonObject
                
                val playing = if (jsonObject.has("playing") && !jsonObject.get("playing").isJsonNull) {
                    jsonObject.get("playing").asString
                } else {
                    "Radio Spaz"
                }
                
                val listeners = if (jsonObject.has("listeners") && !jsonObject.get("listeners").isJsonNull) {
                    try {
                        jsonObject.get("listeners").asInt
                    } catch (e: NumberFormatException) {
                        0
                    }
                } else {
                    0
                }
                
                // Return full string in title field, Artist field left empty as requested
                return TrackInfo(artist = "", title = playing, listeners = listeners)
            }
        } catch (e: Exception) {
            Log.e("MetadataViewModel", "Error fetching metadata", e)
            return TrackInfo(title = "Radio Spaz")
        }
    }
}
