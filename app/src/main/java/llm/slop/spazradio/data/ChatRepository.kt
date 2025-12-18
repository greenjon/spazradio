package llm.slop.spazradio.data

import com.google.gson.Gson
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.random.Random

class ChatRepository(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    private val clientId = "mut${Random.nextInt(1000, 9999)}"
    private var mqttClient: Mqtt5AsyncClient? = null

    private val _onlineUsers = MutableStateFlow<Map<String, String>>(emptyMap())

    suspend fun fetchHistory(): List<ChatMessage> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://radio.spaz.org/djdash/chatlog")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val historyResponse = gson.fromJson(body, HistoryResponse::class.java)
                historyResponse.history
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun observeMessages(): Flow<ChatMessage> = callbackFlow {
        val mqttClient = getOrCreateMqttClient()

        mqttClient.subscribeWith()
            .topicFilter("spazradio")
            .callback { publish ->
                val payload = publish.payloadAsBytes
                val messageStr = String(payload)
                try {
                    val message = gson.fromJson(messageStr, ChatMessage::class.java)
                    trySend(message)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .send()

        awaitClose { }
    }

    fun observePresence(): Flow<Int> = callbackFlow {
        val mqttClient = getOrCreateMqttClient()

        mqttClient.subscribeWith()
            .topicFilter("presence/#")
            .callback { publish ->
                val topic = publish.topic.toString()
                val clientPath = topic.removePrefix("presence/")
                val username = String(publish.payloadAsBytes)

                val currentUsers = _onlineUsers.value.toMutableMap()
                if (username.isEmpty()) {
                    currentUsers.remove(clientPath)
                } else {
                    currentUsers[clientPath] = username
                }
                _onlineUsers.value = currentUsers
                trySend(currentUsers.size)
            }
            .send()

        awaitClose { }
    }

    private fun getOrCreateMqttClient(): Mqtt5AsyncClient {
        mqttClient?.let { return it }

        val client = MqttClient.builder()
            .useMqttVersion5()
            .identifier(clientId)
            .serverHost("radio.spaz.org")
            .serverPort(1885) // Updated port
            .webSocketConfig()
                .serverPath("mqtt")
                .applyWebSocketConfig()
            .sslWithDefaultConfig()
            .buildAsync()

        mqttClient = client
        return client
    }

    fun connect(username: String) {
        val client = getOrCreateMqttClient()
        
        client.connectWith()
            .keepAlive(30) // Set keepAlive
            .willPublish()
                .topic("presence/$clientId")
                .payload(ByteArray(0))
                .retain(true)
                .applyWillPublish()
            .send()
            .thenAccept { 
                // Publish presence
                client.publishWith()
                    .topic("presence/$clientId")
                    .payload(username.toByteArray())
                    .retain(true)
                    .send()
            }
    }

    fun sendMessage(username: String, text: String) {
        val client = mqttClient ?: return
        val message = ChatMessage(
            user = username,
            message = text,
            timeReceived = "" 
        )
        val json = gson.toJson(message)
        client.publishWith()
            .topic("spazradio")
            .payload(json.toByteArray())
            .send()
    }
    
    fun disconnect() {
        mqttClient?.disconnect()
        mqttClient = null
    }
}
