package llm.slop.spazradio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
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
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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

data class RadioMetadata(
    val title: String = "",
    val listeners: Int = 0
)

@OptIn(UnstableApi::class)
class RadioService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var okHttpClient: OkHttpClient
    
    private val liveMediaID = "spaz_radio_stream"
    private var lastPlayingTitle: String? = null
    private var lastListenerCount: Int? = null

    // THROTTLE: Only update waveform at ~30fps to save CPU/Main Thread
    private var lastWaveformUpdateTime = 0L

    companion object {
        private val _waveformFlow = MutableStateFlow<ByteArray?>(null)
        val waveformFlow = _waveformFlow.asStateFlow()

        private val _metaDataFlow = MutableStateFlow(RadioMetadata())
        val metaDataFlow = _metaDataFlow.asStateFlow()
    }

    private val audioBufferSink = object : TeeAudioProcessor.AudioBufferSink {
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {}

        override fun handleBuffer(buffer: ByteBuffer) {
            val now = System.currentTimeMillis()
            if (now - lastWaveformUpdateTime < 33) return // Cap at ~30fps
            lastWaveformUpdateTime = now

            val data = buffer.asReadOnlyBuffer()
            data.order(java.nio.ByteOrder.nativeOrder())
            
            val len = data.remaining() / 2
            val bytes = ByteArray(len)
            
            for (i in 0 until len) {
                val sample = data.short
                bytes[i] = ((sample.toInt() shr 8) + 128).toByte()
            }
            _waveformFlow.value = bytes
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("SpazRadio/1.0")

        // INCREASED BUFFER: Reduce crackling by allowing more audio to sit in memory
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30000, // Min buffer 30s
                60000, // Max buffer 60s
                2500,  // Buffer for playback 2.5s
                5000   // Buffer after re-buffer 5s
            ).build()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setAudioProcessors(arrayOf(TeeAudioProcessor(audioBufferSink)))
                    .build()
            }
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(okHttpDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .build()
        
        player?.playWhenReady = false

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player!!)
            .setId("spaz_radio_session")
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(MediaSessionCallback())
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri("https://radio.spaz.org:8060/radio.ogg")
            .setMediaId(liveMediaID)
            .setMimeType(MimeTypes.AUDIO_OGG)
            .build()
            
        player?.setMediaItem(mediaItem)
        
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _waveformFlow.value = null
                }
            }
        })

        startMetadataPolling()
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<List<MediaItem>> {
            val updatedItems = mediaItems.map { item ->
                if (item.mediaId.startsWith("archive_")) {
                    item.buildUpon().setMimeType(MimeTypes.AUDIO_OGG).build()
                } else { item }
            }
            return Futures.immediateFuture(updatedItems)
        }
    }

    private fun startMetadataPolling() {
        serviceScope.launch(Dispatchers.IO) {
            fetchAndUpdateMetadata()
            while (isActive) {
                delay(10000)
                try { fetchAndUpdateMetadata() } catch (e: Exception) { Log.e("RadioService", "Error", e) }
            }
        }
    }

    private suspend fun fetchAndUpdateMetadata() {
        val request = Request.Builder().url("https://radio.spaz.org/playing").build()
        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) { response.close(); return }
            val body = response.body
            val jsonStr = body.string()
            response.close()
            val jsonObject = JsonParser.parseString(jsonStr).asJsonObject
            val playing = if (jsonObject.has("playing") && !jsonObject.get("playing").isJsonNull) jsonObject.get("playing").asString else "Radio Spaz"
            val listeners = if (jsonObject.has("listeners") && !jsonObject.get("listeners").isJsonNull) {
                try { jsonObject.get("listeners").asInt } catch (_: Exception) { 0 }
            } else { 0 }

            if (playing == lastPlayingTitle && listeners == lastListenerCount) return
            lastPlayingTitle = playing
            lastListenerCount = listeners
            _metaDataFlow.value = RadioMetadata(playing, listeners)

            withContext(Dispatchers.Main) {
                player?.let { exoPlayer ->
                    val isLiveActive = exoPlayer.currentMediaItem?.mediaId == liveMediaID
                    val isCurrentlyAudible = exoPlayer.playWhenReady || exoPlayer.playbackState == Player.STATE_BUFFERING
                    if (isLiveActive && isCurrentlyAudible) {
                        val newMetadata = MediaMetadata.Builder()
                            .setTitle(playing)
                            .setArtist(getString(R.string.listening_template, listeners))
                            .build()
                        val newItem = exoPlayer.currentMediaItem!!.buildUpon().setMediaMetadata(newMetadata).build()
                        exoPlayer.replaceMediaItem(exoPlayer.currentMediaItemIndex, newItem)
                    }
                }
            }
        } catch (e: Exception) { Log.e("RadioService", "Metadata error", e) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = player
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0)) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceJob.cancel()
        mediaSession?.run {
            player?.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
