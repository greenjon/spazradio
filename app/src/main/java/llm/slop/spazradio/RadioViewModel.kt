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

    private val _trackTitle = MutableStateFlow("SPAZ.Radio")
    private val _trackListeners = MutableStateFlow("")
    val trackListeners: StateFlow<String> = _trackListeners.asStateFlow()

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

    // Default to INFO (Schedule) on launch
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
                
                // Auto-play radio on launch if enabled
                if (_appMode.value == AppMode.RADIO && _autoPlayEnabled.value) {
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
        
        val artist = metadata.artist?.toString() ?: ""
        val mediaId = controller.currentMediaItem?.mediaId
        val isLive = mediaId == liveStreamId

        val listenerPrefix = context.getString(R.string.listening_template).split("%d").first()
        if (isLive && artist.startsWith(listenerPrefix)) {
            _trackListeners.value = artist
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
                if (_appMode.value == AppMode.ARCHIVES && !_isArchivePlaying.value) PlaybackUiState.Idle
                else PlaybackUiState.Connecting
            }
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
        val controller = mediaController
        val currentMediaId = controller?.currentMediaItem?.mediaId
        val isLive = currentMediaId == liveStreamId

        _headerTitle.value = "SPAZ.Radio"
        
        _headerSubtitle.value = when {
            _appMode.value == AppMode.ARCHIVES -> {
                context.getString(R.string.label_archives)
            }
            isLive -> {
                _trackListeners.value
            }
            else -> ""
        }

        _trackSubtitle.value = when (val state = _playbackUiState.value) {
            PlaybackUiState.Idle -> ""
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
        } else if (!controller.isPlaying) {
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
        
        // Logical side effects
        if (mode == AppMode.RADIO) {
            if (_autoPlayEnabled.value) {
                showLiveStream()
            }
        } else if (mode == AppMode.ARCHIVES) {
            // Halt radio stream when switching to browse archives
            stopPlayback()
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
            controller.play()
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
