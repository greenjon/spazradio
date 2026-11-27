package llm.slop.spazradio

import android.app.Application
import android.content.Context
import android.text.format.DateFormat
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class ScheduleItem(
    val datePart: String,
    val startTime: String,
    val endTime: String,
    val showName: String
)

data class RawShow(
    val start_timestamp: Long = 0,
    val end_timestamp: Long = 0,
    // Gson fills missing fields with null whatever Kotlin says, so these are honestly nullable.
    val name: String? = null,
    val url: String? = null
)

/** Parses the schedule feed, dropping bad rows one by one (the web TV does the same). */
object ScheduleParser {
    const val FALLBACK_NAME = "SPAZ Radio"
    private val gson = Gson()

    /** Throws IllegalArgumentException when the body is not a JSON array. */
    fun parse(json: String): List<RawShow> {
        val root = JsonParser.parseString(json)
        require(root.isJsonArray) { "Schedule is not a JSON array" }
        return root.asJsonArray.mapNotNull { el ->
            try {
                val show = gson.fromJson(el, RawShow::class.java) ?: return@mapNotNull null
                if (show.end_timestamp <= show.start_timestamp) return@mapNotNull null
                if (show.name.isNullOrBlank()) show.copy(name = FALLBACK_NAME) else show
            } catch (e: Exception) {
                null
            }
        }
    }
}

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val cacheFile = File(context.cacheDir, "schedule_cache.json")

    // Rows are kept raw and formatted when read, so a time zone or locale change and
    // shows that have ended are picked up by refreshView() without another fetch.
    private var rawShows: List<RawShow> = emptyList()

    private val _schedule = MutableStateFlow<List<ScheduleItem>>(emptyList())
    val schedule: StateFlow<List<ScheduleItem>> = _schedule

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private var lastFetch = 0L
    private val fetching = AtomicBoolean(false)

    init {
        loadFromCache()
    }

    private fun loadFromCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (cacheFile.exists()) {
                    val cached = ScheduleParser.parse(cacheFile.readText())
                    // Never let a slow cache read replace fresher network data.
                    if (rawShows.isEmpty() && lastFetch == 0L) {
                        rawShows = cached
                        refreshView()
                        if (_schedule.value.isNotEmpty()) _loading.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("ScheduleViewModel", "Error loading cache", e)
            }
        }
    }

    /** Re-filters and re-formats the rows for the current time, locale and time zone. */
    fun refreshView() {
        val now = System.currentTimeMillis()
        _schedule.value = rawShows
            .filter { it.end_timestamp > now }
            .mapNotNull { runCatching { formatShowItem(it) }.getOrNull() }
    }

    /** Called while the schedule panel is on screen: refresh the view, and the feed if it is over an hour old. */
    suspend fun keepFresh() {
        while (true) {
            refreshView()
            if (System.currentTimeMillis() - lastFetch > TimeUnit.HOURS.toMillis(1)) {
                loadScheduleSync()
            }
            delay(TimeUnit.MINUTES.toMillis(5))
        }
    }

    private fun saveToCache(shows: List<RawShow>) {
        try {
            cacheFile.writeText(Gson().toJson(shows))
        } catch (e: Exception) {
            Log.e("ScheduleViewModel", "Error saving cache", e)
        }
    }

    // Manual refresh (the Retry button)
    fun loadSchedule() {
        viewModelScope.launch(Dispatchers.IO) {
            loadScheduleSync()
        }
    }

    // keepFresh() runs on the main thread (a LaunchedEffect), so the fetch moves itself to IO.
    private suspend fun loadScheduleSync(): Unit = withContext(Dispatchers.IO) { fetchSchedule() }

    private suspend fun fetchSchedule() {
        if (!fetching.compareAndSet(false, true)) return
        try {
            if (_schedule.value.isEmpty()) {
                _loading.value = true
            }
            _error.value = false

            var attempt = 0
            val maxAttempts = 2

            while (attempt < maxAttempts) {
                try {
                    val request = Request.Builder()
                        .url("https://radio.spaz.org/djdash/droid")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("Unexpected code $response")
                        }

                        val body = response.body ?: throw IOException("Empty body")
                        val shows = ScheduleParser.parse(body.string())

                        rawShows = shows
                        lastFetch = System.currentTimeMillis()
                        refreshView()
                        saveToCache(shows)
                    }
                    break
                } catch (e: Exception) {
                    attempt++
                    if (attempt >= maxAttempts) {
                        Log.e("ScheduleViewModel", "Error fetching schedule", e)
                        if (_schedule.value.isEmpty()) {
                            _error.value = true
                        }
                    } else {
                        delay(2_000)
                    }
                }
            }
            _loading.value = false
        } finally {
            fetching.set(false)
        }
    }

    private fun formatShowItem(show: RawShow): ScheduleItem {
        val start = Date(show.start_timestamp)
        val end = Date(show.end_timestamp)

        val weekdayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val monthDayPattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMdd")
        val monthDayFormat = SimpleDateFormat(monthDayPattern, Locale.getDefault())

        val timeFormat = java.text.DateFormat.getTimeInstance(
            java.text.DateFormat.SHORT,
            Locale.getDefault()
        )

        val cleanName = android.text.Html
            .fromHtml(show.name ?: ScheduleParser.FALLBACK_NAME, android.text.Html.FROM_HTML_MODE_LEGACY)
            .toString()

        return ScheduleItem(
            datePart = "${weekdayFormat.format(start)} ${monthDayFormat.format(start)}",
            startTime = timeFormat.format(start),
            endTime = timeFormat.format(end),
            showName = cleanName
        )
    }
}
