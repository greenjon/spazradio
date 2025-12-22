package llm.slop.spazradio

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import llm.slop.spazradio.data.ArchiveRepository
import llm.slop.spazradio.data.ArchiveShow
import llm.slop.spazradio.utils.DownloadTracker
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

sealed class ArchiveUiState {
    data object Loading : ArchiveUiState()
    data class Success(
        val shows: List<ArchiveShow>,
        val filteredShows: List<ArchiveShow>,
        val downloadedUrls: Set<String> = emptySet(),
        val downloadingUrls: Set<String> = emptySet(),
        val searchQuery: String = ""
    ) : ArchiveUiState()
    data class EmptySearch(val query: String) : ArchiveUiState()
    data class Error(val message: String) : ArchiveUiState()
}

class ArchiveViewModel(
    application: Application,
    private val repository: ArchiveRepository,
    private val downloadTracker: DownloadTracker
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        ArchiveRepository(application.applicationContext, OkHttpClient()),
        DownloadTracker(application)
    )

    private val _uiState = MutableStateFlow<ArchiveUiState>(ArchiveUiState.Loading)
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    private val _cachedArchiveCount = MutableStateFlow(repository.getCachedArchives().size)
    val cachedArchiveCount: StateFlow<Int> = _cachedArchiveCount.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var searchJob: Job? = null
    private var isFetching = false

    init {
        loadFromCache()
        startPolling()
        observeDownloads()
    }

    private fun loadFromCache() {
        val cachedShows = repository.getCachedArchives()
        _cachedArchiveCount.value = cachedShows.size
        if (cachedShows.isNotEmpty()) {
            _uiState.value = ArchiveUiState.Success(
                shows = cachedShows,
                filteredShows = cachedShows,
                downloadedUrls = emptySet(),
                downloadingUrls = downloadTracker.downloadingUrls.value,
                searchQuery = ""
            )
            refreshDownloadStatus()
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadTracker.downloadingUrls.collectLatest { downloading ->
                updateUiWithDownloads(downloading = downloading)
            }
        }
        viewModelScope.launch {
            downloadTracker.downloadedUrls.collectLatest {
                refreshDownloadStatus()
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                fetchArchives()
                delay(TimeUnit.HOURS.toMillis(1))
            }
        }
    }

    fun fetchArchivesIfNeeded() {
        if (_uiState.value !is ArchiveUiState.Success) {
            fetchArchives()
        }
    }

    fun fetchArchives() {
        if (isFetching) return
        isFetching = true
        _isRefreshing.value = true
        
        viewModelScope.launch {
            if (_uiState.value !is ArchiveUiState.Success) {
                _uiState.value = ArchiveUiState.Loading
            }
            
            try {
                val shows = repository.fetchArchiveFeed()
                if (shows.isNotEmpty()) {
                    _cachedArchiveCount.value = shows.size
                    
                    val currentQuery = when (val state = _uiState.value) {
                        is ArchiveUiState.Success -> state.searchQuery
                        is ArchiveUiState.EmptySearch -> state.query
                        else -> ""
                    }
                    
                    val filtered = if (currentQuery.isBlank()) shows else shows.filter { 
                        it.title.contains(currentQuery, ignoreCase = true) || 
                        it.date.contains(currentQuery, ignoreCase = true) 
                    }

                    if (filtered.isEmpty() && currentQuery.isNotBlank()) {
                        _uiState.value = ArchiveUiState.EmptySearch(currentQuery)
                    } else {
                        _uiState.value = ArchiveUiState.Success(
                            shows = shows,
                            filteredShows = filtered,
                            downloadedUrls = emptySet(),
                            downloadingUrls = downloadTracker.downloadingUrls.value,
                            searchQuery = currentQuery
                        )
                        refreshDownloadStatus()
                    }
                } else if (_uiState.value !is ArchiveUiState.Success) {
                    _uiState.value = ArchiveUiState.Error("No archives found or network error.")
                }
            } catch (e: Exception) {
                if (_uiState.value !is ArchiveUiState.Success) {
                    _uiState.value = ArchiveUiState.Error(e.message ?: "Unknown error")
                }
            } finally {
                isFetching = false
                _isRefreshing.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        val currentState = _uiState.value
        val shows = when (currentState) {
            is ArchiveUiState.Success -> currentState.shows
            is ArchiveUiState.EmptySearch -> {
                // We need to keep the original shows to re-filter
                // For simplicity in this state, we'll let the job handle it if we can find them
                null 
            }
            else -> null
        }

        // If we are in Success state, update immediately for responsive typing
        if (currentState is ArchiveUiState.Success) {
            _uiState.value = currentState.copy(searchQuery = query)
        } else if (currentState is ArchiveUiState.EmptySearch) {
            // Placeholder update while typing
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(150)
            
            // Re-fetch shows from repository cache if we lost them in the state transition
            val allShows = repository.getCachedArchives()
            
            val filtered = if (query.isBlank()) {
                allShows
            } else {
                allShows.filter { 
                    it.title.contains(query, ignoreCase = true) || 
                    it.date.contains(query, ignoreCase = true) 
                }
            }
            
            withContext(Dispatchers.Main) {
                if (filtered.isEmpty() && query.isNotBlank()) {
                    _uiState.value = ArchiveUiState.EmptySearch(query)
                } else {
                    _uiState.value = ArchiveUiState.Success(
                        shows = allShows,
                        filteredShows = filtered,
                        downloadedUrls = emptySet(), // Will be refreshed
                        downloadingUrls = downloadTracker.downloadingUrls.value,
                        searchQuery = query
                    )
                    refreshDownloadStatus()
                }
            }
        }
    }

    private fun refreshDownloadStatus() {
        val currentState = _uiState.value
        if (currentState is ArchiveUiState.Success) {
            viewModelScope.launch {
                val downloaded = getDownloadedUrls(currentState.shows)
                _uiState.value = currentState.copy(
                    downloadedUrls = downloaded,
                    downloadingUrls = downloadTracker.downloadingUrls.value
                )
            }
        }
    }

    private fun updateUiWithDownloads(downloading: Set<String>) {
        val currentState = _uiState.value
        if (currentState is ArchiveUiState.Success) {
            _uiState.value = currentState.copy(downloadingUrls = downloading)
        }
    }

    private suspend fun getDownloadedUrls(shows: List<ArchiveShow>): Set<String> = withContext(Dispatchers.IO) {
        val downloaded = mutableSetOf<String>()
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "SPAZRadio"
        )
        val filesOnDisk = directory.list()?.toSet() ?: emptySet()
        
        shows.forEach { show ->
            val fileName = downloadTracker.getFileName(show)
            if (filesOnDisk.contains(fileName)) {
                downloaded.add(show.url)
            }
        }
        downloaded
    }

    fun downloadArchive(show: ArchiveShow) {
        downloadTracker.startDownload(show)
    }

    fun getLocalFileIfDownloaded(show: ArchiveShow): File? {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "SPAZRadio"
        )
        val file = File(directory, downloadTracker.getFileName(show))
        return if (file.exists()) file else null
    }
}
