package llm.slop.spazradio

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import llm.slop.spazradio.data.ArchiveRepository
import llm.slop.spazradio.data.ArchiveShow
import okhttp3.OkHttpClient
import java.io.File

sealed class ArchiveUiState {
    data object Loading : ArchiveUiState()
    data class Success(
        val shows: List<ArchiveShow>,
        val filteredShows: List<ArchiveShow>,
        val downloadedUrls: Set<String> = emptySet(),
        val downloadingUrls: Set<String> = emptySet(),
        val searchQuery: String = ""
    ) : ArchiveUiState()
    data class Error(val message: String) : ArchiveUiState()
}

class ArchiveViewModel(application: Application, private val repository: ArchiveRepository) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application, 
        ArchiveRepository(application.applicationContext, OkHttpClient())
    )

    private val _uiState = MutableStateFlow<ArchiveUiState>(ArchiveUiState.Loading)
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    private var hasFetched = false
    private val activeDownloadIds = mutableMapOf<String, Long>()
    private var searchJob: Job? = null

    fun fetchArchivesIfNeeded() {
        if (hasFetched) {
            refreshDownloadStatus()
            return
        }
        
        // Try loading from cache first
        val cachedShows = repository.getCachedArchives()
        if (cachedShows.isNotEmpty()) {
            viewModelScope.launch {
                val downloaded = getDownloadedUrls(cachedShows)
                _uiState.value = ArchiveUiState.Success(
                    shows = cachedShows,
                    filteredShows = cachedShows,
                    downloadedUrls = downloaded,
                    downloadingUrls = emptySet(),
                    searchQuery = ""
                )
            }
        }
        
        fetchArchives()
    }

    fun fetchArchives() {
        viewModelScope.launch {
            // Only show Loading if we don't already have Success state (from cache)
            if (_uiState.value !is ArchiveUiState.Success) {
                _uiState.value = ArchiveUiState.Loading
            }
            
            try {
                val shows = repository.fetchArchiveFeed()
                if (shows.isNotEmpty()) {
                    val downloaded = getDownloadedUrls(shows)
                    
                    // Maintain search query if user is already searching
                    val currentQuery = (_uiState.value as? ArchiveUiState.Success)?.searchQuery ?: ""
                    val filtered = if (currentQuery.isBlank()) shows else shows.filter { 
                        it.title.contains(currentQuery, ignoreCase = true) || 
                        it.date.contains(currentQuery, ignoreCase = true) 
                    }

                    _uiState.value = ArchiveUiState.Success(
                        shows = shows,
                        filteredShows = filtered,
                        downloadedUrls = downloaded,
                        downloadingUrls = emptySet(),
                        searchQuery = currentQuery
                    )
                    hasFetched = true
                } else if (_uiState.value !is ArchiveUiState.Success) {
                    _uiState.value = ArchiveUiState.Error("No archives found or network error.")
                }
            } catch (e: Exception) {
                if (_uiState.value !is ArchiveUiState.Success) {
                    _uiState.value = ArchiveUiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        val currentState = _uiState.value
        if (currentState is ArchiveUiState.Success) {
            _uiState.value = currentState.copy(searchQuery = query)
            
            searchJob?.cancel()
            searchJob = viewModelScope.launch(Dispatchers.Default) {
                delay(100)
                
                val filtered = if (query.isBlank()) {
                    currentState.shows
                } else {
                    currentState.shows.filter { 
                        it.title.contains(query, ignoreCase = true) || 
                        it.date.contains(query, ignoreCase = true) 
                    }
                }
                
                withContext(Dispatchers.Main) {
                    val latestState = _uiState.value
                    if (latestState is ArchiveUiState.Success) {
                        _uiState.value = latestState.copy(
                            filteredShows = filtered
                        )
                    }
                }
            }
        }
    }

    private fun refreshDownloadStatus() {
        val currentState = _uiState.value
        if (currentState is ArchiveUiState.Success) {
            viewModelScope.launch {
                val downloaded = getDownloadedUrls(currentState.shows)
                val stillDownloading = currentState.downloadingUrls.filterNot { downloaded.contains(it) }.toSet()
                _uiState.value = currentState.copy(
                    downloadedUrls = downloaded,
                    downloadingUrls = stillDownloading
                )
            }
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
            val fileName = getFileName(show)
            if (filesOnDisk.contains(fileName) && !activeDownloadIds.containsKey(show.url)) {
                downloaded.add(show.url)
            }
        }
        downloaded
    }

    fun downloadArchive(show: ArchiveShow) {
        val context = getApplication<Application>()
        val currentState = _uiState.value
        if (currentState is ArchiveUiState.Success) {
            if (currentState.downloadingUrls.contains(show.url) || currentState.downloadedUrls.contains(show.url)) return

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileName = getFileName(show)
            val request = DownloadManager.Request(Uri.parse(show.url))
                .setTitle(show.title)
                .setDescription("Downloading Spaz Radio Archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "SPAZRadio/$fileName")

            try {
                val id = downloadManager.enqueue(request)
                activeDownloadIds[show.url] = id
                
                _uiState.value = currentState.copy(
                    downloadingUrls = currentState.downloadingUrls + show.url
                )
                
                Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
                startPollingForDownload(show.url, id)
            } catch (e: Exception) {
                Log.e("ArchiveViewModel", "Download failed", e)
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startPollingForDownload(url: String, downloadId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val downloadManager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            var isFinished = false
            
            while (!isFinished) {
                delay(2000)
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor: Cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIndex != -1) {
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            isFinished = true
                            activeDownloadIds.remove(url)
                            withContext(Dispatchers.Main) {
                                refreshDownloadStatus()
                            }
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            isFinished = true
                            activeDownloadIds.remove(url)
                            withContext(Dispatchers.Main) {
                                handleDownloadFailure(url)
                            }
                        }
                    }
                } else {
                    isFinished = true
                    activeDownloadIds.remove(url)
                    withContext(Dispatchers.Main) {
                        refreshDownloadStatus()
                    }
                }
                cursor.close()
            }
        }
    }

    private fun handleDownloadFailure(url: String) {
        val currentState = _uiState.value
        if (currentState is ArchiveUiState.Success) {
            _uiState.value = currentState.copy(
                downloadingUrls = currentState.downloadingUrls - url
            )
        }
    }

    fun getFileName(show: ArchiveShow): String {
        val safeTitle = show.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val safeDate = show.originalDate.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        return "${safeTitle}_${safeDate}.ogg"
    }

    fun getLocalFileIfDownloaded(show: ArchiveShow): File? {
        if (activeDownloadIds.containsKey(show.url)) return null
        
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "SPAZRadio"
        )
        val file = File(directory, getFileName(show))
        return if (file.exists()) file else null
    }
}
