package llm.slop.spazradio

sealed interface PlaybackUiState {
    data object Idle : PlaybackUiState
    data object Connecting : PlaybackUiState
    data object Buffering : PlaybackUiState
    data object Reconnecting : PlaybackUiState
    data object Playing : PlaybackUiState
    data object Paused : PlaybackUiState
}
