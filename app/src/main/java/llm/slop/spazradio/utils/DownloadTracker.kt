package llm.slop.spazradio.utils

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import llm.slop.spazradio.data.ArchiveShow
import java.io.File

class DownloadTracker(private val application: Application) {

    private val downloadManager = application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _downloadingUrls = MutableStateFlow<Set<String>>(emptySet())
    val downloadingUrls: StateFlow<Set<String>> = _downloadingUrls.asStateFlow()

    /** File names in Music/SPAZRadio; see [getFileName]. */
    private val _downloadedFiles = MutableStateFlow<Set<String>>(emptySet())
    val downloadedFiles: StateFlow<Set<String>> = _downloadedFiles.asStateFlow()

    private val activeDownloadIds = mutableMapOf<String, Long>()

    init {
        refreshDownloadedFiles()
    }

    fun startDownload(show: ArchiveShow) {
        val fileName = getFileName(show)
        if (_downloadingUrls.value.contains(show.url) || _downloadedFiles.value.contains(fileName)) return

        val request = DownloadManager.Request(Uri.parse(show.url))
            .setTitle(show.title)
            .setDescription("Downloading Spaz Radio Archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "SPAZRadio/$fileName")

        try {
            val id = downloadManager.enqueue(request)
            activeDownloadIds[show.url] = id
            _downloadingUrls.value += show.url
            
            startPolling(show.url, id)
        } catch (e: Exception) {
            Log.e("DownloadTracker", "Download failed for ${show.url}", e)
        }
    }

    private fun startPolling(url: String, downloadId: Long) {
        scope.launch {
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
                            onDownloadComplete(url)
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            isFinished = true
                            onDownloadFailed(url)
                        }
                    }
                } else {
                    isFinished = true
                    onDownloadComplete(url) // Assume finished if gone
                }
                cursor.close()
            }
        }
    }

    private fun onDownloadComplete(url: String) {
        activeDownloadIds.remove(url)
        _downloadingUrls.value -= url
        refreshDownloadedFiles()
    }

    private fun onDownloadFailed(url: String) {
        activeDownloadIds.remove(url)
        _downloadingUrls.value -= url
    }

    fun refreshDownloadedFiles() {
        scope.launch {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "SPAZRadio"
            )
            _downloadedFiles.value = directory.list()?.toSet() ?: emptySet()
        }
    }

    fun getFileName(show: ArchiveShow): String {
        val safeTitle = show.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val safeDate = show.originalDate.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        // A jukebox show that isn't in the archive feed has no date.
        return if (safeDate.isEmpty()) "${safeTitle}.ogg" else "${safeTitle}_${safeDate}.ogg"
    }
}
