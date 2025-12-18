package llm.slop.spazradio.data

import com.google.gson.Gson
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CompletableFuture
import kotlin.random.Random

class ChatRepository(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    private val clientId = "mut${Random.nextInt(1000, 9999)}"
    private var mqttClient: Mqtt3AsyncClient? = null

    private val _onlineUsers = MutableStateFlow<Map<String, String>>(emptyMap())
    private var connectionFuture: CompletableFuture<*>? = null

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

    private fun getOrCreateMqttClient(): Mqtt3AsyncClient {
        mqttClient?.let { return it }

        val client = MqttClient.builder()
            .useMqttVersion3() // Force MQTT 3.1.1
            .identifier(clientId)
            .serverHost("radio.spaz.org")
            .serverPort(1885)
            .webSocketConfig()
                .serverPath("/mqtt")
                .subprotocol("mqttv3.1") // Set WebSocket Sub-Protocol
                .applyWebSocketConfig()
            .sslWithDefaultConfig()
            .buildAsync()

        mqttClient = client
        return client
    }

    fun connect(username: String): CompletableFuture<*> {
        val client = getOrCreateMqttClient()
        
        val future = client.connectWith()
            .keepAlive(30)
            .cleanSession(true) // Clean Session true
            .willPublish()
                .topic("presence/$clientId")
                .payload(ByteArray(0))
                .qos(MqttQos.EXACTLY_ONCE)
                .retain(true)
                .applyWillPublish()
            .send()
            .thenCompose { 
                // Publish presence raw string
                client.publishWith()
                    .topic("presence/$clientId")
                    .payload(username.toByteArray())
                    .qos(MqttQos.EXACTLY_ONCE)
                    .retain(true)
                    .send()
            }
        
        connectionFuture = future
        return future
    }

    fun observeMessages(): Flow<ChatMessage> = callbackFlow {
        val client = getOrCreateMqttClient()

        val setupSubscription = {
            client.subscribeWith()
                .topicFilter("spazradio")
                .qos(MqttQos.EXACTLY_ONCE)
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
        }

        if (client.state.isConnected) {
            setupSubscription()
        } else {
            connectionFuture?.thenAccept { setupSubscription() }
        }

        awaitClose { }
    }

    fun observePresence(): Flow<Int> = callbackFlow {
        val client = getOrCreateMqttClient()

        val setupPresenceSubscription = {
            client.subscribeWith()
                .topicFilter("presence/#")
                .qos(MqttQos.EXACTLY_ONCE)
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
        }

        if (client.state.isConnected) {
            setupPresenceSubscription()
        } else {
            connectionFuture?.thenAccept { setupPresenceSubscription() }
        }

        awaitClose { }
    }

    fun sendMessage(username: String, text: String) {
        val client = mqttClient ?: return
        if (!client.state.isConnected) return

        val message = ChatMessage(
            user = username,
            message = text,
            timeReceived = "" 
        )
        val json = gson.toJson(message)
        client.publishWith()
            .topic("spazradio")
            .payload(json.toByteArray())
            .qos(MqttQos.EXACTLY_ONCE)
            .send()
    }
    
    fun disconnect() {
        mqttClient?.disconnect()
        mqttClient = null
        connectionFuture = null
    }
}
