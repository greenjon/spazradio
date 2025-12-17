package llm.slop.spazradio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import llm.slop.spazradio.data.ArchiveRepository
import llm.slop.spazradio.data.ArchiveShow
import okhttp3.OkHttpClient

sealed class ArchiveUiState {
    data object Loading : ArchiveUiState()
    data class Success(val shows: List<ArchiveShow>) : ArchiveUiState()
    data class Error(val message: String) : ArchiveUiState()
}

class ArchiveViewModel(private val repository: ArchiveRepository) : ViewModel() {

    // Simple constructor for manual DI or a factory
    constructor() : this(ArchiveRepository(OkHttpClient()))

    private val _uiState = MutableStateFlow<ArchiveUiState>(ArchiveUiState.Loading)
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    private var hasFetched = false

    fun fetchArchivesIfNeeded() {
        if (hasFetched) return
        fetchArchives()
    }

    fun fetchArchives() {
        viewModelScope.launch {
            _uiState.value = ArchiveUiState.Loading
            try {
                val shows = repository.fetchArchiveFeed()
                if (shows.isNotEmpty()) {
                    _uiState.value = ArchiveUiState.Success(shows)
                    hasFetched = true
                } else {
                    _uiState.value = ArchiveUiState.Error("No archives found or network error.")
                }
            } catch (e: Exception) {
                _uiState.value = ArchiveUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
