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
        val filteredShows: List<ArchiveShow>,
        val downloadedUrls: Set<String> = emptySet(),
        val downloadingUrls: Set<String> = emptySet()
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _cachedArchiveCount = MutableStateFlow(repository.getCachedArchives().size)
    val cachedArchiveCount: StateFlow<Int> = _cachedArchiveCount.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var filterJob: Job? = null
    private var isFetching = false
    private var allShows: List<ArchiveShow> = emptyList()

    init {
        allShows = repository.getCachedArchives()
        _cachedArchiveCount.value = allShows.size
        if (allShows.isNotEmpty()) {
            filterShows("") // Initial state
            refreshDownloadStatus()
        }
        startPolling()
        observeDownloads()
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
        if (_uiState.value is ArchiveUiState.Loading || allShows.isEmpty()) {
            fetchArchives()
        }
    }

    fun fetchArchives() {
        if (isFetching) return
        isFetching = true
        _isRefreshing.value = true
        
        viewModelScope.launch {
            if (allShows.isEmpty()) {
                _uiState.value = ArchiveUiState.Loading
            }
            
            try {
                val shows = repository.fetchArchiveFeed()
                if (shows.isNotEmpty()) {
                    allShows = shows
                    _cachedArchiveCount.value = shows.size
                    filterShows(_searchQuery.value)
                } else if (allShows.isEmpty()) {
                    _uiState.value = ArchiveUiState.Error("No archives found or network error.")
                }
            } catch (e: Exception) {
                if (allShows.isEmpty()) {
                    _uiState.value = ArchiveUiState.Error(e.message ?: "Unknown error")
                }
            } finally {
                isFetching = false
                _isRefreshing.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterShows(query)
    }

    private fun filterShows(query: String) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            if (query.isNotEmpty()) {
                delay(150)
            }
            
            val filtered = if (query.isBlank()) {
                allShows
            } else {
                val isQuoted = query.length >= 2 && query.startsWith("\"") && query.endsWith("\"")
                
                if (isQuoted) {
                    val exactQuery = query.substring(1, query.length - 1)
                    allShows.filter { 
                        it.title.contains(exactQuery, ignoreCase = true) || 
                        it.date.contains(exactQuery, ignoreCase = true) 
                    }
                } else {
                    val searchTerms = query.trim().split("\\s+".toRegex())
                    allShows.filter { show ->
                        searchTerms.all { term ->
                            show.title.contains(term, ignoreCase = true) || 
                            show.date.contains(term, ignoreCase = true)
                        }
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                if (filtered.isEmpty() && query.isNotBlank()) {
                    _uiState.value = ArchiveUiState.EmptySearch(query)
                } else {
                    val downloaded = getDownloadedUrls(filtered)
                    _uiState.value = ArchiveUiState.Success(
                        filteredShows = filtered,
                        downloadedUrls = downloaded,
                        downloadingUrls = downloadTracker.downloadingUrls.value
                    )
                }
            }
        }
    }

    private fun refreshDownloadStatus() {
        val currentState = _uiState.value
        if (currentState is ArchiveUiState.Success) {
            viewModelScope.launch {
                val downloaded = getDownloadedUrls(currentState.filteredShows)
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
