package llm.slop.spazradio.data

import android.util.Log
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
    private val connectedFuture = CompletableFuture<Unit>()

    suspend fun fetchHistory(): List<ChatMessage> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://radio.spaz.org/djdash/chatlog")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ChatRepo", "History fetch failed: ${response.code}")
                    return@withContext emptyList()
                }
                val body = response.body?.string() ?: return@withContext emptyList()
                Log.d("ChatRepo", "History raw body: $body")
                val historyResponse = gson.fromJson(body, HistoryResponse::class.java)
                historyResponse.history
            }
        } catch (e: Exception) {
            Log.e("ChatRepo", "Failed to fetch history", e)
            emptyList()
        }
    }

    private fun getOrCreateMqttClient(): Mqtt3AsyncClient {
        mqttClient?.let { return it }

        Log.d("ChatRepo", "Creating Mqtt3Client for $clientId")
        val client = MqttClient.builder()
            .useMqttVersion3() 
            .identifier(clientId)
            .serverHost("radio.spaz.org")
            .serverPort(1885)
            .webSocketConfig()
                .serverPath("/mqtt")
                .subprotocol("mqttv3.1")
                .applyWebSocketConfig()
            .sslWithDefaultConfig()
            .buildAsync()

        mqttClient = client
        return client
    }

    fun connect(username: String) {
        val client = getOrCreateMqttClient()
        if (client.state.isConnected) return

        Log.d("ChatRepo", "Attempting connection for $username...")
        client.connectWith()
            .keepAlive(30)
            .cleanSession(true)
            .willPublish()
                .topic("presence/$clientId")
                .payload(ByteArray(0))
                .qos(MqttQos.EXACTLY_ONCE)
                .retain(true)
                .applyWillPublish()
            .send()
            .thenCompose { connAck ->
                Log.d("ChatRepo", "Connected! RC: ${connAck.returnCode}. Publishing presence...")
                client.publishWith()
                    .topic("presence/$clientId")
                    .payload(username.toByteArray())
                    .qos(MqttQos.EXACTLY_ONCE)
                    .retain(true)
                    .send()
            }
            .thenAccept {
                Log.d("ChatRepo", "Presence published successfully.")
                connectedFuture.complete(Unit)
            }
            .exceptionally { e ->
                Log.e("ChatRepo", "Connection error tree:", e)
                null
            }
    }

    fun observeMessages(): Flow<ChatMessage> = callbackFlow {
        val client = getOrCreateMqttClient()

        connectedFuture.thenAccept {
            Log.d("ChatRepo", "Subscribing to spazradio...")
            client.subscribeWith()
                .topicFilter("spazradio")
                .qos(MqttQos.EXACTLY_ONCE)
                .callback { publish ->
                    val messageStr = String(publish.payloadAsBytes)
                    try {
                        val message = gson.fromJson(messageStr, ChatMessage::class.java)
                        trySend(message)
                    } catch (e: Exception) {
                        Log.e("ChatRepo", "JSON Error: $messageStr", e)
                    }
                }
                .send()
        }

        awaitClose { }
    }

    fun observePresence(): Flow<Int> = callbackFlow {
        val client = getOrCreateMqttClient()

        connectedFuture.thenAccept {
            Log.d("ChatRepo", "Subscribing to presence/#...")
            client.subscribeWith()
                .topicFilter("presence/#")
                .qos(MqttQos.EXACTLY_ONCE)
                .callback { publish ->
                    val topic = publish.topic.toString()
                    val clientPath = topic.removePrefix("presence/")
                    val payload = String(publish.payloadAsBytes)

                    val currentUsers = _onlineUsers.value.toMutableMap()
                    if (payload.isEmpty()) {
                        currentUsers.remove(clientPath)
                    } else {
                        currentUsers[clientPath] = payload
                    }
                    _onlineUsers.value = currentUsers
                    
                    val uniqueCount = currentUsers.values.distinct().size
                    trySend(uniqueCount)
                }
                .send()
        }

        awaitClose { }
    }

    fun sendMessage(username: String, text: String) {
        val client = mqttClient ?: return
        if (!client.state.isConnected) return

        val message = ChatMessage(user = username, message = text, timeReceived = "")
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
    }
}
