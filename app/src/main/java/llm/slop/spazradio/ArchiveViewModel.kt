package llm.slop.spazradio

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
    object Loading : ArchiveUiState()
    data class Success(val shows: List<ArchiveShow>, val downloadedUrls: Set<String> = emptySet()) : ArchiveUiState()
    data class Error(val message: String) : ArchiveUiState()
}

class ArchiveViewModel(application: Application, private val repository: ArchiveRepository) : AndroidViewModel(application) {

    // Simple constructor for manual DI or a factory
    constructor(application: Application) : this(application, ArchiveRepository(OkHttpClient()))

    private val _uiState = MutableStateFlow<ArchiveUiState>(ArchiveUiState.Loading)
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    private var hasFetched = false

    fun fetchArchivesIfNeeded() {
        if (hasFetched) {
            refreshDownloadStatus()
            return
        }
        fetchArchives()
    }

    fun fetchArchives() {
        viewModelScope.launch {
            _uiState.value = ArchiveUiState.Loading
            try {
                val shows = repository.fetchArchiveFeed()
                if (shows.isNotEmpty()) {
                    val downloaded = getDownloadedUrls(shows)
                    _uiState.value = ArchiveUiState.Success(shows, downloaded)
                    hasFetched = true
                } else {
                    _uiState.value = ArchiveUiState.Error("No archives found or network error.")
                }
            } catch (e: Exception) {
                _uiState.value = ArchiveUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun refreshDownloadStatus() {
        val currentState = _uiState.value
        if (currentState is ArchiveUiState.Success) {
            viewModelScope.launch {
                val downloaded = getDownloadedUrls(currentState.shows)
                _uiState.value = currentState.copy(downloadedUrls = downloaded)
            }
        }
    }

    private suspend fun getDownloadedUrls(shows: List<ArchiveShow>): Set<String> = withContext(Dispatchers.IO) {
        val downloaded = mutableSetOf<String>()
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "SPAZRadio"
        )
        if (directory.exists() && directory.isDirectory) {
            shows.forEach { show ->
                val file = File(directory, getFileName(show))
                if (file.exists()) {
                    downloaded.add(show.url)
                }
            }
        }
        downloaded
    }

    fun downloadArchive(show: ArchiveShow) {
        val context = getApplication<Application>()
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        val fileName = getFileName(show)
        val request = DownloadManager.Request(Uri.parse(show.url))
            .setTitle(show.title)
            .setDescription("Downloading Spaz Radio Archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "SPAZRadio/$fileName")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        try {
            downloadManager.enqueue(request)
            Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
            // Optimistically update UI or refresh after a delay
            refreshDownloadStatus()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun getFileName(show: ArchiveShow): String {
        // Sanitize title for filename
        val safeTitle = show.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val safeDate = show.originalDate.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        return "${safeTitle}_${safeDate}.ogg"
    }

    fun getLocalFileIfDownloaded(show: ArchiveShow): File? {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "SPAZRadio"
        )
        val file = File(directory, getFileName(show))
        return if (file.exists()) file else null
    }
}
