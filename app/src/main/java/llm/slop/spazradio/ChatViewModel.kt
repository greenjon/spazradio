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
import llm.slop.spazradio.data.ChatMessages
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

    private val _username = mutableStateOf(ChatMessages.cleanNick(sharedPrefs.getString("username", "") ?: ""))
    val username: State<String> = _username

    val connectionError: StateFlow<String?> = repository.connectionError

    init {
        Log.d("ChatViewModel", "Initializing ChatViewModel")
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        viewModelScope.launch {
            // History at start (also for viewers who haven't joined, who never connect)...
            loadHistory()

            launch {
                repository.observeMessages().collect { message ->
                    messages.add(message)
                    // A flood trims the oldest instead of growing the board without end.
                    if (messages.size > ChatMessages.MAX_MESSAGES) {
                        messages.removeRange(0, messages.size - ChatMessages.MAX_MESSAGES)
                    }
                }
            }

            // ...and again after every (re)connect, merged, so messages sent while the connection was down
            // (a tunnel, Wi-Fi to mobile data) appear instead of being lost.
            launch {
                repository.observeConnected().collect { loadHistory() }
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
                    // The repository ignores this while the app is in the background.
                    if (isOnline && _username.value.isNotEmpty()) repository.connect(_username.value)
                }
            }
        }
    }

    private suspend fun loadHistory() {
        try {
            val history = repository.fetchHistory()
            val merged = ChatMessages.merge(messages.toList(), history)
            messages.clear()
            messages.addAll(merged)
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error loading history", e)
        }
    }

    // The chat connection is open only while the app is in the foreground, whether or not the chat page is shown.
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        repository.setForeground(true)
        if (_username.value.isNotEmpty()) repository.connect(_username.value)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // Clears our presence and closes the connection, so a reconnect can't mark us online in the background.
        repository.setForeground(false)
        repository.disconnect()
    }

    fun setUsername(name: String) {
        val clean = ChatMessages.cleanNick(name)
        if (clean.isEmpty()) return
        _username.value = clean
        sharedPrefs.edit().putString("username", clean).apply()
        repository.connect(clean)
    }

    fun resetUsername() {
        _username.value = ""
        sharedPrefs.edit().remove("username").apply()
        repository.disconnect()
    }

    /** True when the message was sent; the caller keeps the draft otherwise. */
    suspend fun sendMessage(text: String): Boolean =
        _username.value.isNotEmpty() && repository.sendMessage(_username.value, text)

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        // viewModelScope is already cancelled here; the repository disconnects on its own scope, then ends it.
        repository.shutdown()
    }
}
