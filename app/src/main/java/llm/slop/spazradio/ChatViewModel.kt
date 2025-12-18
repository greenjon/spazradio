package llm.slop.spazradio

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import llm.slop.spazradio.data.ChatMessage
import llm.slop.spazradio.data.ChatRepository
import okhttp3.OkHttpClient
import com.google.gson.Gson

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPrefs = application.getSharedPreferences("spaz_chat", Context.MODE_PRIVATE)
    private val repository = ChatRepository(OkHttpClient(), Gson())

    val messages = mutableStateListOf<ChatMessage>()
    
    private val _onlineCount = MutableStateFlow(0)
    val onlineCount: StateFlow<Int> = _onlineCount

    private val _username = mutableStateOf(sharedPrefs.getString("username", "") ?: "")
    val username: State<String> = _username

    init {
        viewModelScope.launch {
            val history = repository.fetchHistory()
            messages.addAll(history)
            
            launch {
                repository.observeMessages().collect { message ->
                    messages.add(message)
                }
            }
            
            launch {
                repository.observePresence().collect { count ->
                    _onlineCount.value = count
                }
            }

            if (_username.value.isNotEmpty()) {
                repository.connect(_username.value)
            }
        }
    }

    fun setUsername(name: String) {
        _username.value = name
        sharedPrefs.edit().putString("username", name).apply()
        repository.connect(name)
    }

    fun sendMessage(text: String) {
        if (_username.value.isNotEmpty()) {
            repository.sendMessage(_username.value, text)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }
}
