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

    // Configure OkHttpClient with timeouts to be more robust
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .hostnameVerifier { _, _ -> true }
        .build()
        
    private val gson = Gson()

    init {
        loadSchedule()
    }

    fun loadSchedule() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                val request = Request.Builder()
                    .url("https://radio.spaz.org/djdash/droid")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")

                    val responseData = response.body?.string()
                    val type = object : TypeToken<List<RawShow>>() {}.type
                    val rawShows: List<RawShow> = gson.fromJson(responseData, type)

                    val now = System.currentTimeMillis()
                    val formatted = rawShows
                        .filter { it.end_timestamp > now } // Filter out past shows
                        .map { formatShowItem(it) }
                    
                    _schedule.value = formatted
                    _loading.value = false
                }
            } catch (e: Exception) {
                Log.e("ScheduleViewModel", "Error fetching schedule", e)
                _error.value = e.message
                _loading.value = false
            }
        }
    }

    private fun formatShowItem(show: RawShow): ScheduleItem {
        val start = Date(show.start_timestamp)
        val end = Date(show.end_timestamp)

        val weekdayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        // Use getBestDateTimePattern to respect user's locale for month/day order and separator
        val monthDayPattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMdd")
        val monthDayFormat = SimpleDateFormat(monthDayPattern, Locale.getDefault())
        
        val timeFormat = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Locale.getDefault())
        
        val startTime = timeFormat.format(start)
        val endTime = timeFormat.format(end)

        // HTML decoding (simple version for this use case)
        val cleanName = android.text.Html.fromHtml(show.name, android.text.Html.FROM_HTML_MODE_LEGACY).toString()

        return ScheduleItem(
            datePart = "${weekdayFormat.format(start)} ${monthDayFormat.format(start)}",
            startTime = startTime,
            endTime = endTime,
            showName = cleanName
        )
    }
}
