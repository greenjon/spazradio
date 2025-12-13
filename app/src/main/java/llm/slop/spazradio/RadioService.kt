package llm.slop.spazradio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class RadioService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var okHttpClient: OkHttpClient
    
    private val mediaID = "spaz_radio_stream"

    companion object {
        private val _waveformFlow = MutableStateFlow<ByteArray?>(null)
        val waveformFlow = _waveformFlow.asStateFlow()
    }

    private val audioBufferSink = object : TeeAudioProcessor.AudioBufferSink {
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            // No-op
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            val data = buffer.asReadOnlyBuffer()
            data.order(java.nio.ByteOrder.nativeOrder())
            
            val len = data.remaining() / 2
            val bytes = ByteArray(len)
            
            for (i in 0 until len) {
                val sample = data.short
                // 16-bit signed to 8-bit unsigned centered at 128
                bytes[i] = ((sample.toInt() shr 8) + 128).toByte()
            }
            _waveformFlow.value = bytes
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Configure OkHttpClient with increased timeouts
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
         //   .hostnameVerifier { _, _ -> true } // Trust all hostnames for debugging
            .build()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("SpazRadio/1.0")

        // Increase buffer sizes for stream stability
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                60000,  // 1 minute min buffer
                120000, // 2 minutes max buffer
                5000,   // 5 seconds to start playback
                10000   // 10 seconds to resume after rebuffer
            )
            .build()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(TeeAudioProcessor(audioBufferSink)))
                    .build()
            }
        }

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this).setDataSourceFactory(okHttpDataSourceFactory)
            )
            .setLoadControl(loadControl)
            .build()
        
        player?.playWhenReady = true // Autostart playback when ready

        // Ensure we have a valid intent for the session activity
        val sessionActivityPendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri("https://radio.spaz.org:8060/radio.ogg")
            .setMediaId(mediaID)
            .build()
            
        player?.setMediaItem(mediaItem)
        player?.prepare()
        
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Visualizer handling moved to AudioProcessor
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _waveformFlow.value = null
                }
            }
        })

        startMetadataPolling()
    }

    private fun startMetadataPolling() {
        serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    fetchAndUpdateMetadata()
                } catch (e: Exception) {    Log.e("RadioService", "Error parsing metadata", e)
                    val errorMetadata = MediaMetadata.Builder()
                        .setTitle("Radio Spaz")
                        .setArtist("Error loading metadata")
                        .build()

                    // Update the UI with the error message
                    withContext(Dispatchers.Main) {
                        player?.let { exoPlayer ->
                            // ... your existing logic to replace the media item's metadata
                            if (exoPlayer.mediaItemCount > 0) {
                                val currentItem = exoPlayer.getMediaItemAt(0)
                                val newItem = currentItem.buildUpon().setMediaMetadata(errorMetadata).build()
                                exoPlayer.replaceMediaItem(0, newItem)
                            }
                        }
                    }
                }
                delay(10000) // Poll every 10 seconds
            }
        }
    }

    private suspend fun fetchAndUpdateMetadata() {
        val request = Request.Builder()
            .url("https://radio.spaz.org/playing")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return
            }

            val jsonStr = response.body?.string()
            response.close()

            if (jsonStr == null) return

            val jsonObject = JsonParser.parseString(jsonStr).asJsonObject

            val playing = if (jsonObject.has("playing") && !jsonObject.get("playing").isJsonNull) {
                jsonObject.get("playing").asString
            } else {
                "Radio Spaz"
            }

            val listeners = if (jsonObject.has("listeners") && !jsonObject.get("listeners").isJsonNull) {
                try {
                    jsonObject.get("listeners").asInt
                } catch (_: Exception) { 0 }
            } else { 0 }

            val newMetadata = MediaMetadata.Builder()
                .setTitle(playing)
                .setArtist(getString(R.string.listening_template, listeners))
                .build()

            withContext(Dispatchers.Main) {
                player?.let { exoPlayer ->
                    if (exoPlayer.mediaItemCount > 0) {
                        val currentItem = exoPlayer.getMediaItemAt(0)
                        if (currentItem.mediaId == mediaID) {
                            val newItem = currentItem.buildUpon()
                                .setMediaMetadata(newMetadata)
                                .build()
                            
                            // replaceMediaItem at index 0
                            exoPlayer.replaceMediaItem(0, newItem)
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("RadioService", "Error parsing metadata", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        serviceJob.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}