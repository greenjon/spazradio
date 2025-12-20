package llm.slop.spazradio

sealed interface PlaybackUiState {
    data object Idle : PlaybackUiState
    data object Connecting : PlaybackUiState
    data object Buffering : PlaybackUiState
    data object Reconnecting : PlaybackUiState
    data class Playing(val title: String, val listeners: String) : PlaybackUiState
    data class Paused(val title: String, val listeners: String) : PlaybackUiState
}

fun PlaybackUiState.trackTitle(): String = when (this) {
    is PlaybackUiState.Playing -> title
    is PlaybackUiState.Paused -> title
    else -> ""
}

fun PlaybackUiState.trackListeners(): String = when (this) {
    is PlaybackUiState.Playing -> listeners
    is PlaybackUiState.Paused -> listeners
    else -> ""
}
