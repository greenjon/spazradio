package llm.slop.spazradio

import android.text.format.DateFormat
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
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



class ScheduleViewModel : ViewModel() {

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
        loadSchedule()
    }

    fun loadSchedule() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
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

                        val responseData = response.body?.string()
                        val type = object : TypeToken<List<RawShow>>() {}.type
                        val rawShows: List<RawShow> = gson.fromJson(responseData, type)

                        val now = System.currentTimeMillis()
                        _schedule.value = rawShows
                            .filter { it.end_timestamp > now }
                            .map { formatShowItem(it) }
                    }

                    // Success → exit retry loop
                    break

                } catch (e: IOException) {
                    attempt++
                    if (attempt >= maxAttempts) {
                        Log.e("ScheduleViewModel", "Error fetching schedule", e)
                        _error.value = "Schedule unavailable"
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
