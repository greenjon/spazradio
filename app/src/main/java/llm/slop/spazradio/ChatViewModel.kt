package llm.slop.spazradio

import android.app.Application
import android.content.Context
import android.util.Log
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
        Log.d("ChatViewModel", "Initializing ChatViewModel")
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Fetching history...")
                val history = repository.fetchHistory()
                messages.addAll(history)
                Log.d("ChatViewModel", "History loaded: ${history.size} messages")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading history", e)
            }
            
            launch {
                Log.d("ChatViewModel", "Starting message observation...")
                repository.observeMessages().collect { message ->
                    Log.d("ChatViewModel", "New message received: ${message.user}")
                    messages.add(message)
                }
            }
            
            launch {
                Log.d("ChatViewModel", "Starting presence observation...")
                repository.observePresence().collect { count ->
                    Log.d("ChatViewModel", "Online count updated: $count")
                    _onlineCount.value = count
                }
            }

            if (_username.value.isNotEmpty()) {
                Log.d("ChatViewModel", "Connecting with existing username: ${_username.value}")
                repository.connect(_username.value)
            }
        }
    }

    fun setUsername(name: String) {
        Log.d("ChatViewModel", "Setting username: $name")
        _username.value = name
        sharedPrefs.edit().putString("username", name).apply()
        repository.connect(name)
    }

    fun sendMessage(text: String) {
        if (_username.value.isNotEmpty()) {
            Log.d("ChatViewModel", "Sending message: $text")
            repository.sendMessage(_username.value, text)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ChatViewModel", "ChatViewModel cleared, disconnecting repository")
        repository.disconnect()
    }
}
