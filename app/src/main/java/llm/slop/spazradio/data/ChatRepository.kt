package llm.slop.spazradio.data

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import kotlin.random.Random

class ChatRepository(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    private val clientId = "mut${Random.nextInt(1, 1000000)}"
    private var mqttClient: MqttClient? = null

    private val _onlineUsers = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _incomingMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun fetchHistory(): List<ChatMessage> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://radio.spaz.org/djdash/chatlog")
            .header("User-Agent", "Mozilla/5.0 (Android)")
            .build()

        var result: List<ChatMessage>? = null
        for (i in 1..3) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val historyResponse = gson.fromJson(body, HistoryResponse::class.java)
                        
                        result = historyResponse.history.map { msg ->
                            msg.copy(timeReceived = msg.timeReceived / 1000L)
                        }
                        
                        Log.d("ChatRepo", "History loaded and converted from ms on attempt $i")
                        break
                    } else {
                        Log.e("ChatRepo", "History fetch failed (attempt $i): ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatRepo", "History fetch exception (attempt $i): ${e.message}")
                if (i < 3) delay(1000)
            }
        }
        result ?: emptyList()
    }

    private fun getOrCreateMqttClient(): MqttClient {
        mqttClient?.let { return it }

        Log.d("ChatRepo", "Creating Paho MqttClient for $clientId")
        val serverUri = "wss://radio.spaz.org:1885/mqtt"
        val client = MqttClient(serverUri, clientId, MemoryPersistence())
        
        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.d("ChatRepo", "MQTT Connect Complete. Reconnect: $reconnect")
                subscribeAll()
            }

            override fun connectionLost(cause: Throwable?) {
                Log.w("ChatRepo", "MQTT Connection Lost: ${cause?.message}")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.payload?.let { String(it) } ?: return
                if (topic == "spazradio") {
                    try {
                        val rawMsg = gson.fromJson(payload, ChatMessage::class.java)
                        _incomingMessages.tryEmit(rawMsg.copy(timeReceived = rawMsg.timeReceived / 1000L))
                    } catch (e: Exception) {
                        Log.e("ChatRepo", "JSON parse error: $payload", e)
                    }
                } else if (topic?.startsWith("presence/") == true) {
                    val id = topic.substringAfter("presence/")
                    val name = payload
                    val current = _onlineUsers.value.toMutableMap()
                    if (name.isEmpty()) {
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
        val client = getOrCreateMqttClient()
        
        repositoryScope.launch {
            try {
                if (!client.isConnected) {
                    Log.d("ChatRepo", "Connecting to MQTT (v3.1.1)...")
                    val options = MqttConnectOptions().apply {
                        isCleanSession = true
                        isAutomaticReconnect = true
                        connectionTimeout = 30
                        keepAliveInterval = 30
                        mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
                        setWill("presence/$clientId", ByteArray(0), 2, true)
                    }
                    
                    withContext(Dispatchers.IO) {
                        client.connect(options)
                    }
                    Log.d("ChatRepo", "MQTT Connected successfully")
                }
                publishPresence(username)
            } catch (e: Exception) {
                Log.e("ChatRepo", "MQTT Connect Failed", e)
            }
        }
    }

    private fun subscribeAll() {
        try {
            mqttClient?.subscribe("spazradio", 2)
            mqttClient?.subscribe("presence/#", 2)
            Log.d("ChatRepo", "Subscribed to topics (QoS 2)")
        } catch (e: Exception) {
            Log.e("ChatRepo", "Subscribe failed", e)
        }
    }

    private fun publishPresence(username: String) {
        val client = mqttClient ?: return
        if (client.isConnected) {
            try {
                client.publish("presence/$clientId", username.toByteArray(), 2, true)
                Log.d("ChatRepo", "Published presence: $username")
            } catch (e: Exception) {
                Log.e("ChatRepo", "Publish presence failed", e)
            }
        }
    }

    fun observeMessages(): Flow<ChatMessage> = _incomingMessages.asSharedFlow()

    fun observePresenceCount(): Flow<Int> = _onlineUsers.map { it.values.distinct().size }
    
    fun observeOnlineNames(): Flow<List<String>> = _onlineUsers.map { it.values.distinct().sorted() }

    fun sendMessage(username: String, text: String) {
        val client = mqttClient ?: return
        if (!client.isConnected) {
            Log.w("ChatRepo", "Cannot send: Not connected")
            return
        }

        repositoryScope.launch(Dispatchers.IO) {
            try {
                val payload = gson.toJson(mapOf("user" to username, "message" to text))
                client.publish("spazradio", payload.toByteArray(), 2, false)
                Log.d("ChatRepo", "Message sent: $text")
            } catch (e: Exception) {
                Log.e("ChatRepo", "Send failed", e)
            }
        }
    }

    fun disconnect() {
        Log.d("ChatRepo", "Disconnecting MQTT...")
        try {
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()
            }
            mqttClient = null
        } catch (e: Exception) {
            Log.e("ChatRepo", "Error during disconnect", e)
        }
    }
}
