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
            filterShows("")
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
            
            val downloadedUrls = getDownloadedUrls(allShows)
            
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
                    val tokens = parseSearchQuery(query)
                    allShows.filter { show ->
                        tokens.all { token ->
                            evaluateToken(token, show, downloadedUrls)
                        }
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                if (filtered.isEmpty() && query.isNotBlank()) {
                    _uiState.value = ArchiveUiState.EmptySearch(query)
                } else {
                    _uiState.value = ArchiveUiState.Success(
                        filteredShows = filtered,
                        downloadedUrls = downloadedUrls,
                        downloadingUrls = downloadTracker.downloadingUrls.value
                    )
                }
            }
        }
    }

    // --- Search Engine DSL Logic ---

    private sealed class SearchToken {
        data class Text(val term: String) : SearchToken()
        data class Downloaded(val invert: Boolean) : SearchToken()
        data class DateRule(val operator: String, val year: Int) : SearchToken()
        data class DateRangeRule(val startYear: Int, val endYear: Int) : SearchToken()
        data class DurationRule(val operator: String, val seconds: Int) : SearchToken()
        data class DurationRangeRule(val startSeconds: Int, val endSeconds: Int) : SearchToken()
    }

    private fun parseSearchQuery(query: String): List<SearchToken> {
        val tokens = mutableListOf<SearchToken>()
        val parts = query.trim().split("\\s+".toRegex())
        
        for (part in parts) {
            when {
                // Downloaded rules
                part.contains("downloaded", ignoreCase = true) -> {
                    val invert = part.startsWith("!") || part.startsWith("not", ignoreCase = true) || 
                                part.contains("not", ignoreCase = true)
                    tokens.add(SearchToken.Downloaded(invert))
                }
                
                // Date/Year Range: year:2015-2020 or date:2015-2020
                (part.startsWith("date", ignoreCase = true) || part.startsWith("year", ignoreCase = true)) && 
                part.contains("-") -> {
                    val match = "(?:date|year)[:=](\\d{4})-(\\d{4})".toRegex(RegexOption.IGNORE_CASE).find(part)
                    match?.let {
                        val start = it.groupValues[1].toInt()
                        val end = it.groupValues[2].toInt()
                        tokens.add(SearchToken.DateRangeRule(minOf(start, end), maxOf(start, end)))
                    } ?: tokens.add(SearchToken.Text(part))
                }

                // Date/Year Single: year<2020, date:2017
                part.startsWith("date", ignoreCase = true) || part.startsWith("year", ignoreCase = true) -> {
                    val match = "(?:date|year)([<>]=?|[:=])(\\d{4})".toRegex(RegexOption.IGNORE_CASE).find(part)
                    match?.let {
                        val op = it.groupValues[1].replace(":", "=")
                        val year = it.groupValues[2].toInt()
                        tokens.add(SearchToken.DateRule(op, year))
                    } ?: tokens.add(SearchToken.Text(part))
                }
                
                // Reverse year rules: 2017<=
                "(\\d{4})([<>]=?)".toRegex().matches(part) -> {
                    val match = "(\\d{4})([<>]=?)".toRegex().find(part)
                    match?.let {
                        val year = it.groupValues[1].toInt()
                        val op = it.groupValues[2]
                        tokens.add(SearchToken.DateRule(op, year))
                    } ?: tokens.add(SearchToken.Text(part))
                }

                // Duration Range: duration:1h-3h or duration:1-3h
                part.startsWith("duration", ignoreCase = true) && part.contains("-") -> {
                    val match = "duration[:=](\\d+)([hm])?-(\\d+)([hm])?".toRegex(RegexOption.IGNORE_CASE).find(part)
                    match?.let {
                        val val1 = it.groupValues[1].toInt()
                        val unit1 = it.groupValues[2].lowercase()
                        val val2 = it.groupValues[3].toInt()
                        val unit2 = it.groupValues[4].lowercase().ifEmpty { unit1 }
                        
                        val startSec = convertToSeconds(val1, unit1.ifEmpty { unit2 }.ifEmpty { "m" })
                        val endSec = convertToSeconds(val2, unit2.ifEmpty { "m" })
                        tokens.add(SearchToken.DurationRangeRule(minOf(startSec, endSec), maxOf(startSec, endSec)))
                    } ?: tokens.add(SearchToken.Text(part))
                }

                // Duration Single: duration<2h
                part.startsWith("duration", ignoreCase = true) -> {
                    val match = "duration([<>]=?|[:=])(\\d+)([hm])?".toRegex(RegexOption.IGNORE_CASE).find(part)
                    match?.let {
                        val op = it.groupValues[1].replace(":", "=")
                        val amount = it.groupValues[2].toInt()
                        val unit = it.groupValues[3].lowercase().ifEmpty { "m" }
                        tokens.add(SearchToken.DurationRule(op, convertToSeconds(amount, unit)))
                    } ?: tokens.add(SearchToken.Text(part))
                }

                else -> tokens.add(SearchToken.Text(part))
            }
        }
        return tokens
    }

    private fun convertToSeconds(amount: Int, unit: String): Int = when (unit) {
        "h" -> amount * 3600
        "m" -> amount * 60
        else -> amount * 60
    }

    private fun evaluateToken(token: SearchToken, show: ArchiveShow, downloadedUrls: Set<String>): Boolean {
        return when (token) {
            is SearchToken.Text -> {
                show.title.contains(token.term, ignoreCase = true) || 
                show.date.contains(token.term, ignoreCase = true)
            }
            is SearchToken.Downloaded -> {
                val isDownloaded = downloadedUrls.contains(show.url)
                if (token.invert) !isDownloaded else isDownloaded
            }
            is SearchToken.DateRule -> {
                val showYear = if (show.date.length >= 4) show.date.takeLast(4).toIntOrNull() ?: 0 else 0
                compareInts(showYear, token.year, token.operator)
            }
            is SearchToken.DateRangeRule -> {
                val showYear = if (show.date.length >= 4) show.date.takeLast(4).toIntOrNull() ?: 0 else 0
                showYear in token.startYear..token.endYear
            }
            is SearchToken.DurationRule -> {
                val showSeconds = parseDurationToSeconds(show.duration ?: "")
                compareInts(showSeconds, token.seconds, token.operator)
            }
            is SearchToken.DurationRangeRule -> {
                val showSeconds = parseDurationToSeconds(show.duration ?: "")
                showSeconds in token.startSeconds..token.endSeconds
            }
        }
    }

    private fun compareInts(actual: Int, target: Int, op: String): Boolean {
        return when (op) {
            "<" -> actual < target
            ">" -> actual > target
            "<=" -> actual <= target
            ">=" -> actual >= target
            "=" -> actual == target
            else -> true
        }
    }

    private fun parseDurationToSeconds(duration: String): Int {
        val parts = duration.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> (parts[0] * 3600) + (parts[1] * 60) + parts[2]
            2 -> (parts[0] * 60) + parts[1]
            1 -> parts[0]
            else -> 0
        }
    }

    // --- End Search Engine DSL Logic ---

    private fun refreshDownloadStatus() {
        val currentState = _uiState.value
        if (currentState is ArchiveUiState.Success) {
            viewModelScope.launch {
                val downloaded = getDownloadedUrls(allShows)
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
