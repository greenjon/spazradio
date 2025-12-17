package llm.slop.spazradio

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import llm.slop.spazradio.data.ArchiveShow

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("spaz_radio_prefs", Context.MODE_PRIVATE)
    private val liveStreamUrl = "https://radio.spaz.org:8060/radio.ogg"
    private val liveStreamId = "spaz_radio_stream"

    // --- Playback State ---
    private val _playbackUiState = MutableStateFlow<PlaybackUiState>(PlaybackUiState.Connecting)
    val playbackUiState: StateFlow<PlaybackUiState> = _playbackUiState.asStateFlow()

    private val _trackTitle = MutableStateFlow("SPAZ.Radio")
    private val _trackListeners = MutableStateFlow("")

    private val _headerTitle = MutableStateFlow("SPAZ.Radio")
    val headerTitle: StateFlow<String> = _headerTitle.asStateFlow()

    private val _trackSubtitle = MutableStateFlow("Connecting…")
    val trackSubtitle: StateFlow<String> = _trackSubtitle.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // --- UI / Navigation State ---
    private val _infoDisplay = MutableStateFlow(InfoDisplay.NONE)
    val infoDisplay: StateFlow<InfoDisplay> = _infoDisplay.asStateFlow()

    private val _showSchedulePref = MutableStateFlow(prefs.getBoolean("show_schedule", true))
    val showSchedulePref: StateFlow<Boolean> = _showSchedulePref.asStateFlow()

    private val _lissajousMode = MutableStateFlow(prefs.getBoolean("visuals_enabled", true))
    val lissajousMode: StateFlow<Boolean> = _lissajousMode.asStateFlow()

    private val _currentInfoDisplay = MutableStateFlow(InfoDisplay.NONE)
    val currentInfoDisplay: StateFlow<InfoDisplay> = _currentInfoDisplay.asStateFlow()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    init {
        val context = application.applicationContext
        val token = SessionToken(context, ComponentName(context, RadioService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                mediaController = controller
                setupController(controller)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
        
        updateCurrentInfoDisplay()
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
        })
    }

    private fun updateState(controller: MediaController) {
        val state = controller.playbackState
        val isPlaying = controller.isPlaying
        _isPlaying.value = isPlaying
        
        val metadata = controller.mediaMetadata
        _trackTitle.value = metadata.title?.toString() ?: _trackTitle.value
        _trackListeners.value = metadata.artist?.toString() ?: _trackListeners.value

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
            "SPAZ Archive"
        }

        _trackSubtitle.value = when (val state = _playbackUiState.value) {
            PlaybackUiState.Connecting -> "Connecting…"
            PlaybackUiState.Buffering -> "Buffering…"
            PlaybackUiState.Reconnecting -> "Reconnecting…"
            is PlaybackUiState.Playing -> state.title
            is PlaybackUiState.Paused -> state.title
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
        
        // Auto-close info box once playing an archive
        closeInfoBox()
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

        _infoDisplay.value = InfoDisplay.NONE
        updateCurrentInfoDisplay()
    }

    // --- Settings / UI Actions ---

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun toggleSettings() {
        _infoDisplay.value = if (_infoDisplay.value == InfoDisplay.SETTINGS) InfoDisplay.NONE else InfoDisplay.SETTINGS
        updateCurrentInfoDisplay()
    }

    fun showArchives() {
        _infoDisplay.value = InfoDisplay.ARCHIVES
        updateCurrentInfoDisplay()
    }

    fun closeInfoBox() {
        if (_infoDisplay.value != InfoDisplay.NONE) {
            _infoDisplay.value = InfoDisplay.NONE
        } else if (_showSchedulePref.value) {
            setShowSchedule(false)
        }
        updateCurrentInfoDisplay()
    }

    fun setShowSchedule(enabled: Boolean) {
        _showSchedulePref.value = enabled
        prefs.edit().putBoolean("show_schedule", enabled).apply()
        updateCurrentInfoDisplay()
    }

    fun setLissajousMode(enabled: Boolean) {
        _lissajousMode.value = enabled
        prefs.edit().putBoolean("visuals_enabled", enabled).apply()
    }

    private fun updateCurrentInfoDisplay() {
        _currentInfoDisplay.value = when {
            _infoDisplay.value != InfoDisplay.NONE -> _infoDisplay.value
            _showSchedulePref.value -> InfoDisplay.SCHEDULE
            else -> InfoDisplay.NONE
        }
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
