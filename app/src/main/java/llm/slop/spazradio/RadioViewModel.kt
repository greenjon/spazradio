package llm.slop.spazradio

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import llm.slop.spazradio.data.ArchiveShow
import llm.slop.spazradio.visuals.ContentUpdater
import llm.slop.spazradio.visuals.ContentUpdates
import llm.slop.spazradio.visuals.ReducedMotion
import llm.slop.spazradio.visuals.VisualQuality
import llm.slop.spazradio.visuals.VisualsSettings

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("spaz_radio_prefs", Context.MODE_PRIVATE)
    private val liveStreamUrl = "https://radio.spaz.org:8060/radio.ogg"
    private val liveStreamId = "spaz_radio_stream"

    // --- Playback State ---
    private val _playbackUiState = MutableStateFlow<PlaybackUiState>(PlaybackUiState.Idle)
    val playbackUiState: StateFlow<PlaybackUiState> = _playbackUiState.asStateFlow()

    // Internal persistent state for the live radio info (polled from service)
    private val _liveTitle = MutableStateFlow("")
    private val _liveListeners = MutableStateFlow("")
    
    // Internal state for the current archive track
    private val _archiveTitle = MutableStateFlow("")

    // Expose live listener count for the RADIO tab in the header
    val trackListeners: StateFlow<String> = _liveListeners.asStateFlow()

    private val _headerTitle = MutableStateFlow("SPAZ.Radio")
    val headerTitle: StateFlow<String> = _headerTitle.asStateFlow()

    private val _headerSubtitle = MutableStateFlow("")
    val headerSubtitle: StateFlow<String> = _headerSubtitle.asStateFlow()

    private val _trackSubtitle = MutableStateFlow("")
    val trackSubtitle: StateFlow<String> = _trackSubtitle.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isArchivePlaying = MutableStateFlow(false)
    val isArchivePlaying: StateFlow<Boolean> = _isArchivePlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration: StateFlow<Long> = _playbackDuration.asStateFlow()

    // --- UI / Navigation State ---
    private val _appMode = MutableStateFlow(AppMode.RADIO)
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

    private val _activeUtility = MutableStateFlow(ActiveUtility.INFO)
    val activeUtility: StateFlow<ActiveUtility> = _activeUtility.asStateFlow()

    private val _lissajousMode = MutableStateFlow(prefs.getBoolean("visuals_enabled", true))
    val lissajousMode: StateFlow<Boolean> = _lissajousMode.asStateFlow()

    /** Fade the controls out over the visuals after a few seconds; the first tap brings them back. */
    private val _autoHideControls = MutableStateFlow(prefs.getBoolean("visuals_auto_hide", true))
    val autoHideControls: StateFlow<Boolean> = _autoHideControls.asStateFlow()

    /** The web visuals couldn't start on this device (no WebView or WebGL2); the oscilloscope is shown instead. */
    val webVisualsFailed = MutableStateFlow(false)

    private val _visuals = MutableStateFlow(
        VisualsSettings(
            quality = enumPref("visuals_quality", VisualQuality.AUTO),
            reactivity = migratedReactivity(),
            reducedMotion = enumPref("visuals_reduced_motion", ReducedMotion.AUTO),
            live = prefs.getBoolean("visuals_live", false),
            syncMs = prefs.getInt("visuals_sync_ms", VisualsSettings.DEFAULT_SYNC_MS),
            contentUpdates = prefs.getBoolean("visuals_content_updates", true),
        )
    )
    val visuals: StateFlow<VisualsSettings> = _visuals.asStateFlow()

    enum class ContentStatus { IDLE, CHECKING, UPDATED, UP_TO_DATE, NEEDS_NEWER_APP, FAILED }

    /** Version (epoch seconds) of the downloaded looks in use, or null when only the bundled ones are. */
    private val _contentVersion = MutableStateFlow<Long?>(ContentUpdates.store(context).version())
    val contentVersion: StateFlow<Long?> = _contentVersion.asStateFlow()
    private val _contentStatus = MutableStateFlow(ContentStatus.IDLE)
    val contentStatus: StateFlow<ContentStatus> = _contentStatus.asStateFlow()

    /** "Check now" in Settings. New looks are picked up the next time the picture starts. */
    fun checkContentNow() {
        if (_contentStatus.value == ContentStatus.CHECKING) return
        _contentStatus.value = ContentStatus.CHECKING
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = ContentUpdates.runCheck(context)
            _contentVersion.value = ContentUpdates.store(context).version()
            _contentStatus.value = when (result) {
                is ContentUpdater.Result.Updated -> ContentStatus.UPDATED
                is ContentUpdater.Result.UpToDate -> ContentStatus.UP_TO_DATE
                is ContentUpdater.Result.Rejected -> if (result.reason == "needs a newer app") ContentStatus.NEEDS_NEWER_APP else ContentStatus.FAILED
                null -> ContentStatus.FAILED
            }
        }
    }

    private fun <E : Enum<E>> enumPref(key: String, default: E): E =
        default.javaClass.enumConstants
            ?.firstOrNull { it.name == prefs.getString(key, null) } ?: default

    private val _autoPlayEnabled = MutableStateFlow(prefs.getBoolean("autoplay_enabled", true))
    val autoPlayEnabled: StateFlow<Boolean> = _autoPlayEnabled.asStateFlow()

    private val _appTheme = MutableStateFlow(
        AppTheme.valueOf(prefs.getString("app_theme", AppTheme.NEON.name) ?: AppTheme.NEON.name)
    )
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    init {
        ContentUpdates.schedule(context, _visuals.value.contentUpdates)
        val token = SessionToken(context, ComponentName(context, RadioService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                mediaController = controller
                setupController(controller)
                
                if (_appMode.value == AppMode.RADIO) {
                    if (_autoPlayEnabled.value) {
                        showLiveStream()
                    } else {
                        // Switch to live stream item but do not start playback
                        val isAlreadyLive = controller.currentMediaItem?.mediaId == liveStreamId
                        if (!isAlreadyLive) {
                            val mediaItem = MediaItem.Builder()
                                .setUri(liveStreamUrl)
                                .setMediaId(liveStreamId)
                                .build()
                            controller.setMediaItem(mediaItem)
                        }
                        controller.playWhenReady = false
                        updateState(controller)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
        
        startPositionPolling()
        observeMetadata()
    }

    private fun observeMetadata() {
        viewModelScope.launch {
            RadioService.metaDataFlow.collect { metadata ->
                _liveTitle.value = metadata.title
                _liveListeners.value = if (metadata.listeners > 0) {
                    context.getString(R.string.listening_template, metadata.listeners)
                } else {
                    ""
                }
                updateDisplayStrings()
            }
        }
    }

    private fun setupController(controller: MediaController) {
        updateState(controller)
        controller.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                updateState(controller)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState(controller)
            }

            override fun onPlayerError(error: PlaybackException) {
                _playbackUiState.value = PlaybackUiState.Reconnecting
                updateDisplayStrings()
            }

            override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                if (_isArchivePlaying.value) {
                    updateState(controller)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateState(controller)
            }
        })
    }

    private fun updateState(controller: MediaController) {
        val state = controller.playbackState
        val isPlaying = controller.isPlaying
        _isPlaying.value = isPlaying
        
        val metadata = controller.mediaMetadata
        val mediaId = controller.currentMediaItem?.mediaId
        val isLive = mediaId == liveStreamId

        if (!isLive && mediaId != null) {
            _archiveTitle.value = metadata.title?.toString() ?: ""
        }

        _isArchivePlaying.value = mediaId?.startsWith("archive_") == true || (mediaId != liveStreamId && mediaId != null)
        
        if (_isArchivePlaying.value) {
            _playbackDuration.value = if (controller.duration > 0) controller.duration else 0L
        } else {
            _playbackDuration.value = 0L
            _playbackPosition.value = 0L
        }

        _playbackUiState.value = when (state) {
            Player.STATE_IDLE -> {
                if (_appMode.value == AppMode.ARCHIVES && !_isArchivePlaying.value) {
                    PlaybackUiState.Idle
                } else if (controller.playWhenReady) {
                    PlaybackUiState.Connecting
                } else {
                    // IDLE and NOT trying to play means we are paused or waiting for manual start
                    PlaybackUiState.Paused("", "") 
                }
            }
            Player.STATE_BUFFERING -> PlaybackUiState.Buffering
            Player.STATE_READY -> {
                if (isPlaying) PlaybackUiState.Playing("", "")
                else PlaybackUiState.Paused("", "")
            }
            Player.STATE_ENDED -> PlaybackUiState.Reconnecting
            else -> PlaybackUiState.Connecting
        }
        updateDisplayStrings()
    }

    private fun updateDisplayStrings() {
        val controller = mediaController ?: return
        val currentMediaId = controller.currentMediaItem?.mediaId
        val isLive = currentMediaId == liveStreamId
        val currentAppMode = _appMode.value

        _headerTitle.value = "SPAZ.Radio"
        
        // Header subtitle always shows listener count in Radio mode, even if Archives screen is active
        _headerSubtitle.value = when {
            currentAppMode == AppMode.ARCHIVES -> context.getString(R.string.label_archives)
            else -> _liveListeners.value
        }

        _trackSubtitle.value = when (currentAppMode) {
            AppMode.RADIO -> {
                when (_playbackUiState.value) {
                    PlaybackUiState.Connecting -> context.getString(R.string.status_connecting)
                    PlaybackUiState.Buffering -> context.getString(R.string.status_buffering)
                    PlaybackUiState.Reconnecting -> context.getString(R.string.status_reconnecting)
                    else -> _liveTitle.value.ifEmpty { "Radio Spaz" }
                }
            }
            AppMode.ARCHIVES -> {
                if (_isArchivePlaying.value && !isLive) {
                    if (_playbackUiState.value == PlaybackUiState.Buffering) {
                        context.getString(R.string.status_buffering)
                    } else {
                        _archiveTitle.value
                    }
                } else {
                    ""
                }
            }
        }
    }

    private fun startPositionPolling() {
        viewModelScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (_isArchivePlaying.value && controller.isPlaying) {
                        _playbackPosition.value = controller.currentPosition
                        if (_playbackDuration.value <= 0 && controller.duration > 0) {
                            _playbackDuration.value = controller.duration
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    // --- Playback Actions ---

    fun playArchive(show: ArchiveShow) {
        val controller = mediaController ?: return
        val mediaItem = MediaItem.Builder()
            .setUri(show.url)
            .setMediaId("archive_${show.originalDate}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(show.title)
                    .setArtist(show.date)
                    .build()
            )
            .build()
        
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    fun showLiveStream() {
        val controller = mediaController ?: return
        val isAlreadyLive = controller.currentMediaItem?.mediaId == liveStreamId
        
        if (!isAlreadyLive) {
            val mediaItem = MediaItem.Builder()
                .setUri(liveStreamUrl)
                .setMediaId(liveStreamId)
                .build()
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        } else if (!controller.isPlaying) {
            if (controller.playbackState == Player.STATE_IDLE) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun stopPlayback() {
        mediaController?.stop()
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
        _playbackPosition.value = position
    }

    // --- Navigation & Utility Actions ---

    fun setAppMode(mode: AppMode) {
        if (_appMode.value == mode) {
            _activeUtility.value = ActiveUtility.INFO
            return
        }
        
        _appMode.value = mode
        _activeUtility.value = ActiveUtility.INFO
        
        val controller = mediaController ?: return

        if (mode == AppMode.RADIO) {
            val isAlreadyLive = controller.currentMediaItem?.mediaId == liveStreamId
            if (!isAlreadyLive) {
                // If we're not already on Radio stream, stop any playing archive
                stopPlayback()
                val mediaItem = MediaItem.Builder()
                    .setUri(liveStreamUrl)
                    .setMediaId(liveStreamId)
                    .build()
                controller.setMediaItem(mediaItem)
            }

            if (_autoPlayEnabled.value) {
                showLiveStream()
            } else {
                // Ensure the player is NOT trying to connect if autoplay is off
                controller.playWhenReady = false
                updateState(controller)
            }
        } else if (mode == AppMode.ARCHIVES) {
            // Only stop if the radio stream is playing
            if (controller.currentMediaItem?.mediaId == liveStreamId) {
                stopPlayback()
            }
        }
        
        updateDisplayStrings()
    }

    fun setActiveUtility(utility: ActiveUtility) {
        if (_activeUtility.value == utility) {
            _activeUtility.value = ActiveUtility.NONE
        } else {
            _activeUtility.value = utility
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.currentMediaItem?.mediaId == liveStreamId) {
                showLiveStream()
            } else {
                controller.play()
            }
        }
    }

    fun closeInfoBox() {
        _activeUtility.value = ActiveUtility.NONE
    }

    // --- Prefs ---

    fun setLissajousMode(enabled: Boolean) {
        _lissajousMode.value = enabled
        prefs.edit().putBoolean("visuals_enabled", enabled).apply()
    }

    fun setAutoHideControls(enabled: Boolean) {
        _autoHideControls.value = enabled
        prefs.edit().putBoolean("visuals_auto_hide", enabled).apply()
    }

    /**
     * The saved reactivity. Before 1.6.4-beta4 it was on the old web TV scale (0.25..4, default 2, no auto
     * gain); auto gain made 1 the equivalent of the old 2, so a saved value is halved once.
     */
    private fun migratedReactivity(): Float {
        val saved = prefs.getFloat("visuals_reactivity", VisualsSettings.DEFAULT_REACTIVITY)
        if (prefs.getBoolean("visuals_reactivity_agc", false)) return saved
        val v = if (prefs.contains("visuals_reactivity")) saved / 2f else saved
        val clamped = v.coerceIn(VisualsSettings.MIN_REACTIVITY, VisualsSettings.MAX_REACTIVITY)
        prefs.edit().putFloat("visuals_reactivity", clamped).putBoolean("visuals_reactivity_agc", true).apply()
        return clamped
    }

    fun updateVisuals(change: (VisualsSettings) -> VisualsSettings) {
        val old = _visuals.value
        val v = change(old)
        _visuals.value = v
        if (v.contentUpdates != old.contentUpdates) ContentUpdates.schedule(context, v.contentUpdates)
        prefs.edit()
            .putString("visuals_quality", v.quality.name)
            .putFloat("visuals_reactivity", v.reactivity)
            .putString("visuals_reduced_motion", v.reducedMotion.name)
            .putBoolean("visuals_live", v.live)
            .putInt("visuals_sync_ms", v.syncMs)
            .putBoolean("visuals_content_updates", v.contentUpdates)
            .apply()
    }

    fun setAutoPlayEnabled(enabled: Boolean) {
        _autoPlayEnabled.value = enabled
        prefs.edit().putBoolean("autoplay_enabled", enabled).apply()
    }

    fun setAppTheme(theme: AppTheme) {
        _appTheme.value = theme
        prefs.edit().putString("app_theme", theme.name).apply()
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
