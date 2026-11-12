package llm.slop.spazradio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
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
import llm.slop.spazradio.visuals.PcmTap
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
    
    private val liveMediaID = LiveStream.MEDIA_ID
    private var connectivityManager: ConnectivityManager? = null
    private var prefs: SharedPreferences? = null
    private var metered = false
    private var lowMountMissing = false
    private var lastPlayingTitle: String? = null
    private var lastListenerCount: Int? = null

    private val eqProcessor = EqAudioProcessor()
    private var systemEqSession = C.AUDIO_SESSION_ID_UNSET

    // THROTTLE: Only update waveform at ~30fps to save CPU/Main Thread
    private var lastWaveformUpdateTime = 0L

    companion object {
        private val _waveformFlow = MutableStateFlow<ByteArray?>(null)
        val waveformFlow = _waveformFlow.asStateFlow()

        private val _metaDataFlow = MutableStateFlow(RadioMetadata())
        val metaDataFlow = _metaDataFlow.asStateFlow()

        /** The player's audio session, for the system equalizer panel. */
        private val _audioSessionId = MutableStateFlow(C.AUDIO_SESSION_ID_UNSET)
        val audioSessionId = _audioSessionId.asStateFlow()
    }

    private val audioBufferSink = object : TeeAudioProcessor.AudioBufferSink {
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            PcmTap.configure(sampleRateHz, channelCount)
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            PcmTap.onBuffer(buffer) // the web visuals, only while they are listening
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

        // Wrap OkHttpDataSource in DefaultDataSource to support file:// and other local URIs
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("SpazRadio/1.0")
        
        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)

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
                    .setAudioProcessors(arrayOf(TeeAudioProcessor(audioBufferSink), eqProcessor))
                    .build()
            }
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
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

        player?.setMediaItem(LiveStream.mediaItem(this))

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    _waveformFlow.value = null
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                _audioSessionId.value = audioSessionId
                applyEq()
            }

            override fun onPlayerError(error: PlaybackException) {
                // The low mount is a separate source and can be down while the main one is fine: stay on air.
                val notFound = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode == 404
                val onLowMount = player?.currentMediaItem?.localConfiguration?.uri?.toString() == LiveStream.URL_LOW
                if (notFound && onLowMount) {
                    Log.w("RadioService", "Low-bandwidth mount missing, using the normal stream")
                    lowMountMissing = true
                    switchLiveStream(LiveStream.URL_HIGH, resume = true)
                }
            }
        })

        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        metered = connectivityManager?.isActiveNetworkMetered ?: false
        connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        prefs = getSharedPreferences(LiveStream.PREFS, MODE_PRIVATE)
        prefs?.registerOnSharedPreferenceChangeListener(qualityListener)
        _audioSessionId.value = player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        applyEq()

        startMetadataPolling()
    }

    /** Fires on connect and on every capability change; switching only happens when the mount would differ. */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val nowMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            serviceScope.launch {
                metered = nowMetered
                applyQuality()
            }
        }
    }

    // SharedPreferences holds its listeners weakly, so this one has to be a field.
    private val qualityListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == LiveStream.QUALITY_PREF) {
            lowMountMissing = false // a fresh choice gets a fresh try
            applyQuality()
        }
        if (key in EqPrefs.KEYS) applyEq()
    }

    /** Built-in: our filters run. System: we announce the session and stay flat. Off: neither. */
    private fun applyEq() {
        val savedPrefs = prefs ?: return
        val mode = EqPrefs.effectiveMode(this, savedPrefs)
        eqProcessor.bands = if (mode == EqMode.BUILT_IN) EqPrefs.bands(savedPrefs) else null
        val session = if (mode == EqMode.SYSTEM) player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET else C.AUDIO_SESSION_ID_UNSET
        if (session == systemEqSession) return
        if (systemEqSession != C.AUDIO_SESSION_ID_UNSET) SystemEq.close(this, systemEqSession)
        if (session != C.AUDIO_SESSION_ID_UNSET) SystemEq.open(this, session)
        systemEqSession = session
    }

    private fun applyQuality() {
        val exoPlayer = player ?: return
        val savedPrefs = prefs ?: return
        val url = if (lowMountMissing) LiveStream.URL_HIGH else LiveStream.urlFor(LiveStream.savedQuality(savedPrefs), metered)
        // A stopped player (e.g. the Archives tab) must stay silent, so only a live, prepared one resumes.
        switchLiveStream(url, resume = exoPlayer.playWhenReady && exoPlayer.playbackState != Player.STATE_IDLE)
    }

    /** Points the live item at [url], if it isn't already. Paused, it reconnects only when played again. */
    private fun switchLiveStream(url: String, resume: Boolean) {
        val exoPlayer = player ?: return
        val current = exoPlayer.currentMediaItem ?: return
        if (current.mediaId != LiveStream.MEDIA_ID || current.localConfiguration?.uri?.toString() == url) return
        if (!resume) exoPlayer.stop()
        exoPlayer.setMediaItem(current.buildUpon().setUri(url).build())
        if (resume) exoPlayer.prepare()
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
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                
                val body = response.body ?: return
                val jsonStr = body.string()
                
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
        connectivityManager?.unregisterNetworkCallback(networkCallback)
        prefs?.unregisterOnSharedPreferenceChangeListener(qualityListener)
        if (systemEqSession != C.AUDIO_SESSION_ID_UNSET) SystemEq.close(this, systemEqSession)
        serviceJob.cancel()
        mediaSession?.run {
            player?.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
