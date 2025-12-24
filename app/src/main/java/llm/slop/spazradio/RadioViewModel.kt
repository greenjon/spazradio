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

    private val _autoPlayEnabled = MutableStateFlow(prefs.getBoolean("autoplay_enabled", true))
    val autoPlayEnabled: StateFlow<Boolean> = _autoPlayEnabled.asStateFlow()

    private val _appTheme = MutableStateFlow(
        AppTheme.valueOf(prefs.getString("app_theme", AppTheme.NEON.name) ?: AppTheme.NEON.name)
    )
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    init {
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
