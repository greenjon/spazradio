package llm.slop.spazradio.data

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import javax.net.ssl.SSLContext
import kotlin.random.Random

class ChatRepository(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    private val clientId = "mut${Random.nextInt(1, 1000000)}"
    private var mqttClient: MqttClient? = null
    private var currentUsername: String? = null

    private val _onlineUsers = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _incomingMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    
    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val connectionMutex = Mutex()
    private var isConnecting = false

    suspend fun fetchHistory(): List<ChatMessage> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://radio.spaz.org/djdash/chatlog")
            .header("User-Agent", "Mozilla/5.0 (Android)")
            .build()

        for (i in 1..3) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body
                        val json = body.string()
                        val historyResponse = gson.fromJson(json, HistoryResponse::class.java)
                        return@withContext historyResponse.history.map { msg ->
                            msg.copy(timeReceived = msg.timeReceived / 1000L)
                        }
                    }
                }
            } catch (e: Exception) {
                if (i < 3) delay(1000)
            }
        }
        emptyList()
    }

    private fun getOrCreateMqttClient(): MqttClient {
        mqttClient?.let { return it }

        val serverUri = "wss://radio.spaz.org:1885/mqtt"
        val client = MqttClient(serverUri, clientId, MemoryPersistence())
        
        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.d("ChatRepo", "MQTT Connect Complete. Reconnect: $reconnect")
                repositoryScope.launch {
                    connectionMutex.withLock { isConnecting = false }
                    currentUsername?.let { publishPresence(it, true) }
                }
                _connectionError.value = null
                subscribeAll()
            }

            override fun connectionLost(cause: Throwable?) {
                Log.w("ChatRepo", "MQTT Connection Lost: ${cause?.message}")
                _connectionError.value = cause?.message ?: "Connection lost"
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.payload?.let { String(it) } ?: ""
                if (topic == "spazradio") {
                    try {
                        val rawMsg = gson.fromJson(payload, ChatMessage::class.java)
                        _incomingMessages.tryEmit(rawMsg.copy(timeReceived = rawMsg.timeReceived / 1000L))
                    } catch (e: Exception) {
                        Log.e("ChatRepo", "JSON parse error")
                    }
                } else if (topic?.startsWith("presence/") == true) {
                    val id = topic.substringAfter("presence/")
                    val name = payload
                    val current = _onlineUsers.value.toMutableMap()
                    if (name.isEmpty() || name == "offline") {
                        current.remove(id)
                    } else {
                        current[id] = name
                    }
                    _onlineUsers.value = current
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        mqttClient = client
        return client
    }

    fun connect(username: String) {
        currentUsername = username
        val client = getOrCreateMqttClient()
        
        repositoryScope.launch {
            connectionMutex.withLock {
                if (client.isConnected || isConnecting) {
                    Log.d("ChatRepo", "Connect attempt blocked: Busy or already connected")
                    if (client.isConnected) publishPresence(username, true)
                    return@launch
                }
                isConnecting = true
            }

            try {
                _connectionError.value = null
                
                val sslContext = SSLContext.getInstance("TLSv1.2")
                sslContext.init(null, null, null)

                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    isAutomaticReconnect = true
                    connectionTimeout = 30
                    keepAliveInterval = 30
                    mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
                    // Reverting to the original "presence/" topic structure
                    setWill("presence/$clientId", "".toByteArray(), 1, true)
                    socketFactory = sslContext.socketFactory
                }
                
                Log.d("ChatRepo", "Starting MQTT connection sequence (TLS 1.2 forced)...")
                withContext(Dispatchers.IO) {
                    client.connect(options)
                }
            } catch (e: Exception) {
                connectionMutex.withLock { isConnecting = false }
                Log.e("ChatRepo", "MQTT Connect Failed: ${e.message}")
                _connectionError.value = e.cause?.message ?: e.message ?: "Connect failed"
            }
        }
    }

    private fun subscribeAll() {
        try {
            mqttClient?.subscribe("spazradio", 2)
            mqttClient?.subscribe("presence/#", 1)
            _connectionError.value = null
        } catch (e: Exception) {
            Log.e("ChatRepo", "Subscribe failed")
        }
    }

    private fun publishPresence(username: String, isOnline: Boolean) {
        val client = mqttClient ?: return
        if (client.isConnected) {
            try {
                val topic = "presence/$clientId"
                val payload = if (isOnline) username else ""
                val message = MqttMessage(payload.toByteArray()).apply {
                    qos = 1
                    isRetained = true
                }
                client.publish(topic, message)
            } catch (e: Exception) {
                Log.e("ChatRepo", "Publish presence failed")
            }
        }
    }

    fun observeMessages(): Flow<ChatMessage> = _incomingMessages.asSharedFlow()
    fun observePresenceCount(): Flow<Int> = _onlineUsers.map { it.values.distinct().size }
    fun observeOnlineNames(): Flow<List<String>> = _onlineUsers.map { it.values.distinct().sorted() }

    fun sendMessage(username: String, text: String) {
        val client = mqttClient ?: return
        if (!client.isConnected) {
            _connectionError.value = "Not connected"
            return
        }

        repositoryScope.launch(Dispatchers.IO) {
            try {
                val payload = gson.toJson(mapOf("user" to username, "message" to text))
                client.publish("spazradio", payload.toByteArray(), 2, false)
            } catch (e: Exception) {
                _connectionError.value = "Send failed: ${e.message}"
            }
        }
    }

    fun disconnect() {
        try {
            repositoryScope.launch {
                connectionMutex.withLock { isConnecting = false }
                
                currentUsername?.let { 
                    publishPresence(it, false) 
                }

                if (mqttClient?.isConnected == true) {
                    mqttClient?.disconnect()
                }
                mqttClient = null
                currentUsername = null
                _connectionError.value = null
                _onlineUsers.value = emptyMap()
            }
        } catch (e: Exception) {
            Log.e("ChatRepo", "Error during disconnect")
        }
    }
}
