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
    private val _playbackUiState = MutableStateFlow<PlaybackUiState>(PlaybackUiState.Connecting)
    val playbackUiState: StateFlow<PlaybackUiState> = _playbackUiState.asStateFlow()

    private val _trackTitle = MutableStateFlow("SPAZ.Radio")
    private val _trackListeners = MutableStateFlow("")

    private val _headerTitle = MutableStateFlow("SPAZ.Radio")
    val headerTitle: StateFlow<String> = _headerTitle.asStateFlow()

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

    // --- UI / Navigation State (Phase 1 Overhaul) ---
    private val _appMode = MutableStateFlow(AppMode.RADIO)
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

    private val _activeUtility = MutableStateFlow(ActiveUtility.NONE)
    val activeUtility: StateFlow<ActiveUtility> = _activeUtility.asStateFlow()

    private val _lissajousMode = MutableStateFlow(prefs.getBoolean("visuals_enabled", true))
    val lissajousMode: StateFlow<Boolean> = _lissajousMode.asStateFlow()

    // Deprecated InfoDisplay states (will remove in later phase)
    private val _currentInfoDisplay = MutableStateFlow(InfoDisplay.NONE)
    val currentInfoDisplay: StateFlow<InfoDisplay> = _currentInfoDisplay.asStateFlow()

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
                
                // Auto-play radio on launch
                if (_appMode.value == AppMode.RADIO) {
                    showLiveStream()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
        
        startPositionPolling()
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
                _trackTitle.value = metadata.title?.toString() ?: "SPAZ.Radio"
                _trackListeners.value = metadata.artist?.toString() ?: ""
                updateState(controller)
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
        _trackTitle.value = metadata.title?.toString() ?: _trackTitle.value
        _trackListeners.value = metadata.artist?.toString() ?: _trackListeners.value

        val mediaId = controller.currentMediaItem?.mediaId
        _isArchivePlaying.value = mediaId?.startsWith("archive_") == true || (mediaId != liveStreamId && mediaId != null)
        
        if (_isArchivePlaying.value) {
            _playbackDuration.value = if (controller.duration > 0) controller.duration else 0L
        } else {
            _playbackDuration.value = 0L
            _playbackPosition.value = 0L
        }

        _playbackUiState.value = when (state) {
            Player.STATE_IDLE -> PlaybackUiState.Connecting
            Player.STATE_BUFFERING -> PlaybackUiState.Buffering
            Player.STATE_READY -> {
                if (isPlaying) PlaybackUiState.Playing(_trackTitle.value, _trackListeners.value)
                else PlaybackUiState.Paused(_trackTitle.value, _trackListeners.value)
            }
            Player.STATE_ENDED -> PlaybackUiState.Reconnecting
            else -> PlaybackUiState.Connecting
        }
        updateDisplayStrings()
    }

    private fun updateDisplayStrings() {
        val listeners = _trackListeners.value
        val controller = mediaController
        val isLive = controller?.currentMediaItem?.mediaId == liveStreamId

        _headerTitle.value = if (isLive && listeners.isNotBlank()) {
            "SPAZ.Radio   -   $listeners"
        } else if (isLive) {
            "SPAZ.Radio"
        } else {
            context.getString(R.string.label_archives)
        }

        _trackSubtitle.value = when (val state = _playbackUiState.value) {
            PlaybackUiState.Connecting -> context.getString(R.string.status_connecting)
            PlaybackUiState.Buffering -> context.getString(R.string.status_buffering)
            PlaybackUiState.Reconnecting -> context.getString(R.string.status_reconnecting)
            is PlaybackUiState.Playing -> state.title
            is PlaybackUiState.Paused -> state.title
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
        }
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
        _playbackPosition.value = position
    }

    // --- Navigation & Utility Actions ---

    fun setAppMode(mode: AppMode) {
        if (_appMode.value == mode) return
        _appMode.value = mode
        
        // Logical side effects
        if (mode == AppMode.RADIO) {
            showLiveStream()
        }
        
        // When switching modes, we might want to update the active utility 
        // if it's the dynamic INFO button, but for now we'll just let it stay open.
    }

    fun setActiveUtility(utility: ActiveUtility) {
        // Toggle behavior
        if (_activeUtility.value == utility) {
            _activeUtility.value = ActiveUtility.NONE
        } else {
            _activeUtility.value = utility
        }
        syncDeprecatedState()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    // Temporary sync for Phase 1 to keep UI working while we refactor components
    private fun syncDeprecatedState() {
        _currentInfoDisplay.value = when (_activeUtility.value) {
            ActiveUtility.SETTINGS -> InfoDisplay.SETTINGS
            ActiveUtility.CHAT -> InfoDisplay.CHAT
            ActiveUtility.INFO -> if (_appMode.value == AppMode.RADIO) InfoDisplay.SCHEDULE else InfoDisplay.ARCHIVES
            ActiveUtility.VISUALS -> InfoDisplay.NONE // Will handle in later phase
            ActiveUtility.NONE -> InfoDisplay.NONE
        }
    }

    fun closeInfoBox() {
        _activeUtility.value = ActiveUtility.NONE
        syncDeprecatedState()
    }

    // --- Prefs ---

    fun setLissajousMode(enabled: Boolean) {
        _lissajousMode.value = enabled
        prefs.edit().putBoolean("visuals_enabled", enabled).apply()
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
