package llm.slop.spazradio

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
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

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val _playbackUiState = MutableStateFlow<PlaybackUiState>(PlaybackUiState.Connecting)
    val playbackUiState: StateFlow<PlaybackUiState> = _playbackUiState.asStateFlow()

    private val _trackTitle = MutableStateFlow("SPAZ.Radio")
    val trackTitle: StateFlow<String> = _trackTitle.asStateFlow()

    private val _trackListeners = MutableStateFlow("")
    val trackListeners: StateFlow<String> = _trackListeners.asStateFlow()

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
        
        _trackTitle.value = controller.mediaMetadata.title?.toString() ?: _trackTitle.value
        _trackListeners.value = controller.mediaMetadata.artist?.toString() ?: _trackListeners.value

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
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
