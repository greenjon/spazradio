package com.greenjon.spazradiotest

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
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
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class RadioService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var visualizer: Visualizer? = null
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var okHttpClient: OkHttpClient
    
    private val MEDIA_ID = "spaz_radio_stream"

    companion object {
        private val _waveformFlow = MutableStateFlow<ByteArray?>(null)
        val waveformFlow = _waveformFlow.asStateFlow()
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
            .setUserAgent("SpazRadioTest/1.0")

        // Increase buffer sizes for stream stability
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                60000,  // 1 minute min buffer
                120000, // 2 minutes max buffer
                5000,   // 5 seconds to start playback
                10000   // 10 seconds to resume after rebuffer
            )
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this).setDataSourceFactory(okHttpDataSourceFactory)
            )
            .setLoadControl(loadControl)
            .build()
        
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
            .setMediaId(MEDIA_ID)
            .build()
            
        player?.setMediaItem(mediaItem)
        player?.prepare()
        
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startVisualizer()
                } else {
                    stopVisualizer()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    stopVisualizer()
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
                } catch (e: Exception) {
                    Log.e("RadioService", "Metadata polling error", e)
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
                } catch (e: Exception) { 0 }
            } else { 0 }

            val newMetadata = MediaMetadata.Builder()
                .setTitle(playing)
                .setArtist("$listeners listening")
                .build()

            withContext(Dispatchers.Main) {
                player?.let { exoPlayer ->
                    if (exoPlayer.mediaItemCount > 0) {
                        val currentItem = exoPlayer.getMediaItemAt(0)
                        if (currentItem.mediaId == MEDIA_ID) {
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

    private fun startVisualizer() {
        val sessionId = player?.audioSessionId ?: return
        if (sessionId == 0) return 
        
        stopVisualizer()

        try {
            visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            // Clone the array to ensure StateFlow emits a new value (reference equality check)
                            _waveformFlow.value = waveform?.clone()
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            // Not using FFT for now
                        }
                    },
                    Visualizer.getMaxCaptureRate(),
                    true,
                    false
                )
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVisualizer() {
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        _waveformFlow.value = null
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
        stopVisualizer()
        super.onDestroy()
    }
}
