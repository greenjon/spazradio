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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import llm.slop.spazradio.data.ArchiveShow
import java.io.File
import java.net.URI

class DownloadTracker(private val application: Application) {

    private val downloadManager = application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _downloadingUrls = MutableStateFlow<Set<String>>(emptySet())
    val downloadingUrls: StateFlow<Set<String>> = _downloadingUrls.asStateFlow()

    /** File names in Music/SPAZRadio; see [getFileName]. */
    private val _downloadedFiles = MutableStateFlow<Set<String>>(emptySet())
    val downloadedFiles: StateFlow<Set<String>> = _downloadedFiles.asStateFlow()

    private val _failures = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** One-shot events: the title of a show whose download could not start or failed later. */
    val failures: SharedFlow<String> = _failures.asSharedFlow()

    init {
        refreshDownloadedFiles()
    }

    fun startDownload(show: ArchiveShow) {
        val fileName = getFileName(show)
        if (_downloadingUrls.value.contains(show.url) || _downloadedFiles.value.contains(fileName)) return

        val url = secureUrl(show.url)
        if (url == null) {
            Log.w("DownloadTracker", "Refusing download from ${show.url}")
            _failures.tryEmit(show.title)
            return
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(show.title)
            .setDescription("Downloading Spaz Radio Archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "SPAZRadio/$fileName")

        try {
            val id = downloadManager.enqueue(request)
            _downloadingUrls.update { it + show.url }

            startPolling(show, id)
        } catch (e: Exception) {
            // On Android 7-9 this is a SecurityException when WRITE_EXTERNAL_STORAGE was not granted.
            Log.e("DownloadTracker", "Download failed for ${show.url}", e)
            _failures.tryEmit(show.title)
        }
    }

    private fun startPolling(show: ArchiveShow, downloadId: Long) {
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
                            onDownloadComplete(show.url)
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            isFinished = true
                            onDownloadFailed(show)
                        }
                    }
                } else {
                    isFinished = true
                    onDownloadComplete(show.url) // Assume finished if gone
                }
                cursor.close()
            }
        }
    }

    private fun onDownloadComplete(url: String) {
        _downloadingUrls.update { it - url }
        refreshDownloadedFiles()
    }

    private fun onDownloadFailed(show: ArchiveShow) {
        _downloadingUrls.update { it - show.url }
        _failures.tryEmit(show.title)
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

    companion object {
        /**
         * The URL to hand to DownloadManager (a system process that ignores the app's cleartext policy):
         * https on a spaz.org host only. An http URL on such a host is upgraded; anything else is null.
         */
        fun secureUrl(url: String): String? {
            val uri = try { URI(url) } catch (_: Exception) { return null }
            val host = uri.host?.lowercase() ?: return null
            if (host != "spaz.org" && !host.endsWith(".spaz.org")) return null
            return when (uri.scheme?.lowercase()) {
                "https" -> url
                "http" -> "https" + url.substring(4)
                else -> null
            }
        }
    }
}
