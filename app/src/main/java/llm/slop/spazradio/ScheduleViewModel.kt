package llm.slop.spazradio

import android.app.Application
import android.content.Context
import android.text.format.DateFormat
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ScheduleItem(
    val datePart: String,
    val startTime: String,
    val endTime: String,
    val showName: String
)

data class RawShow(
    val start_timestamp: Long,
    val end_timestamp: Long,
    val name: String,
    val url: String
)

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val cacheFile = File(context.cacheDir, "schedule_cache.json")
    
    private val _schedule = MutableStateFlow<List<ScheduleItem>>(emptyList())
    val schedule: StateFlow<List<ScheduleItem>> = _schedule

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val gson = Gson()

    init {
        loadFromCache()
        loadSchedule()
    }

    private fun loadFromCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (cacheFile.exists()) {
                    val json = cacheFile.readText()
                    val type = object : TypeToken<List<RawShow>>() {}.type
                    val rawShows: List<RawShow> = gson.fromJson(json, type)
                    
                    val now = System.currentTimeMillis()
                    val validShows = rawShows
                        .filter { it.end_timestamp > now }
                        .map { formatShowItem(it) }
                    
                    if (validShows.isNotEmpty()) {
                        _schedule.value = validShows
                        // If we have valid cached data, we can stop the initial loading spinner
                        _loading.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("ScheduleViewModel", "Error loading cache", e)
            }
        }
    }

    private fun saveToCache(rawShows: List<RawShow>) {
        try {
            val json = gson.toJson(rawShows)
            cacheFile.writeText(json)
        } catch (e: Exception) {
            Log.e("ScheduleViewModel", "Error saving cache", e)
        }
    }

    fun loadSchedule() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_schedule.value.isEmpty()) {
                _loading.value = true
            }
            _error.value = null

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
                        val responseData = body.string()
                        val type = object : TypeToken<List<RawShow>>() {}.type
                        val rawShows: List<RawShow> = gson.fromJson(responseData, type)

                        val now = System.currentTimeMillis()
                        
                        // Prune and format
                        val validShows = rawShows
                            .filter { it.end_timestamp > now }
                        
                        _schedule.value = validShows.map { formatShowItem(it) }
                        
                        // Persist the full raw list for future launches
                        saveToCache(rawShows)
                    }

                    // Success → exit retry loop
                    break

                } catch (e: IOException) {
                    attempt++
                    if (attempt >= maxAttempts) {
                        Log.e("ScheduleViewModel", "Error fetching schedule", e)
                        if (_schedule.value.isEmpty()) {
                            _error.value = "Schedule unavailable"
                        }
                    } else {
                        kotlinx.coroutines.delay(1_000)
                    }
                }
            }

            _loading.value = false
        }
    }

    private fun formatShowItem(show: RawShow): ScheduleItem {
        val start = Date(show.start_timestamp)
        val end = Date(show.end_timestamp)

        val weekdayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val monthDayPattern =
            DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMdd")
        val monthDayFormat = SimpleDateFormat(monthDayPattern, Locale.getDefault())

        val timeFormat =
            java.text.DateFormat.getTimeInstance(
                java.text.DateFormat.SHORT,
                Locale.getDefault()
            )

        val cleanName = android.text.Html
            .fromHtml(show.name, android.text.Html.FROM_HTML_MODE_LEGACY)
            .toString()

        return ScheduleItem(
            datePart = "${weekdayFormat.format(start)} ${monthDayFormat.format(start)}",
            startTime = timeFormat.format(start),
            endTime = timeFormat.format(end),
            showName = cleanName
        )
    }
}
