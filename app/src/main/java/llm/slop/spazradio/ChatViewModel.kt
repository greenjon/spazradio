package llm.slop.spazradio

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import llm.slop.spazradio.data.ChatMessage
import llm.slop.spazradio.data.ChatRepository
import llm.slop.spazradio.utils.NetworkMonitor
import llm.slop.spazradio.utils.SpazNetwork

class ChatViewModel(application: Application) : AndroidViewModel(application), DefaultLifecycleObserver {
    private val sharedPrefs = application.getSharedPreferences("spaz_chat", Context.MODE_PRIVATE)
    
    private val repository = ChatRepository(SpazNetwork.client, Gson())
    private val networkMonitor = NetworkMonitor(application)

    val messages = mutableStateListOf<ChatMessage>()
    
    private val _onlineCount = MutableStateFlow(0)
    val onlineCount: StateFlow<Int> = _onlineCount
    
    private val _onlineNames = MutableStateFlow<List<String>>(emptyList())
    val onlineNames: StateFlow<List<String>> = _onlineNames

    private val _username = mutableStateOf(sharedPrefs.getString("username", "") ?: "")
    val username: State<String> = _username

    val connectionError: StateFlow<String?> = repository.connectionError

    init {
        Log.d("ChatViewModel", "Initializing ChatViewModel")
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        viewModelScope.launch {
            // Load history once on start
            try {
                val history = repository.fetchHistory()
                messages.clear()
                messages.addAll(history)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading history", e)
            }
            
            launch {
                repository.observeMessages().collect { message ->
                    messages.add(message)
                }
            }
            
            launch {
                repository.observePresenceCount().collect { count ->
                    _onlineCount.value = count
                }
            }
            
            launch {
                repository.observeOnlineNames().collect { names ->
                    _onlineNames.value = names
                }
            }

            // Centralized connection trigger: Network Online
            launch {
                networkMonitor.isOnline.collectLatest { isOnline ->
                    if (isOnline && _username.value.isNotEmpty()) {
                        launch(Dispatchers.IO) {
                            repository.connect(_username.value)
                        }
                    }
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        // App came to foreground
        if (_username.value.isNotEmpty()) {
            Log.d("ChatViewModel", "App foregrounded - Publishing online status")
            repository.publishPresence(_username.value, true)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // App went to background
        if (_username.value.isNotEmpty()) {
            Log.d("ChatViewModel", "App backgrounded - Publishing offline status")
            repository.publishPresence(_username.value, false)
        }
    }

    fun setUsername(name: String) {
        _username.value = name
        sharedPrefs.edit().putString("username", name).apply()
        viewModelScope.launch(Dispatchers.IO) {
            repository.connect(name)
        }
    }

    fun resetUsername() {
        _username.value = ""
        sharedPrefs.edit().remove("username").apply()
        viewModelScope.launch(Dispatchers.IO) {
            repository.disconnect()
        }
    }

    fun sendMessage(text: String) {
        if (_username.value.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.sendMessage(_username.value, text)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        viewModelScope.launch(Dispatchers.IO) {
            repository.disconnect()
        }
    }
}
