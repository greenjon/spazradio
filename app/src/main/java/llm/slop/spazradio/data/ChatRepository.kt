package llm.slop.spazradio.data

import android.util.Log
import com.google.gson.Gson
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.random.Random

class ChatRepository(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    private val clientId = "mut${Random.nextInt(1, 1000000)}"
    private var mqttClient: Mqtt3AsyncClient? = null

    private val _onlineUsers = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _incomingMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    private var connectedFuture = CompletableFuture<Unit>()

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
                Log.d("ChatRepo", "History raw body received")
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
        Log.d("ChatRepo", "connect() requested for $username. State: ${client.state}")

        if (client.state.isConnected) {
            publishPresence(username)
            return
        }

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
                Log.d("ChatRepo", "MQTT Connected (RC: ${connAck.returnCode})")
                subscribe()
            }
            .thenCompose { 
                Log.d("ChatRepo", "Subscribed to spazradio")
                subscribePresence()
            }
            .thenCompose { 
                Log.d("ChatRepo", "Subscribed to presence/#")
                publishPresence(username)
            }
            .thenAccept {
                Log.d("ChatRepo", "Presence published. Ready.")
                connectedFuture.complete(Unit)
            }
            .exceptionally { e ->
                Log.e("ChatRepo", "Connection/Handshake failed", e)
                null
            }
    }

    private fun subscribe(): CompletableFuture<*> {
        return mqttClient?.subscribeWith()
            ?.topicFilter("spazradio")
            ?.qos(MqttQos.EXACTLY_ONCE)
            ?.callback { publish ->
                val payload = String(publish.payloadAsBytes)
                try {
                    val message = gson.fromJson(payload, ChatMessage::class.java)
                    _incomingMessages.tryEmit(message)
                } catch (e: Exception) {
                    Log.e("ChatRepo", "JSON parse error: $payload", e)
                }
            }
            ?.send() ?: CompletableFuture.completedFuture(null)
    }

    private fun subscribePresence(): CompletableFuture<*> {
        return mqttClient?.subscribeWith()
            ?.topicFilter("presence/#")
            ?.qos(MqttQos.EXACTLY_ONCE)
            ?.callback { publish ->
                val topic = publish.topic.toString()
                val id = topic.substringAfter("presence/")
                val name = String(publish.payloadAsBytes)
                
                Log.d("ChatRepo", "Presence update: $id -> $name")
                val current = _onlineUsers.value.toMutableMap()
                if (name.isEmpty()) {
                    current.remove(id)
                } else {
                    current[id] = name
                }
                _onlineUsers.value = current
            }
            ?.send() ?: CompletableFuture.completedFuture(null)
    }

    private fun publishPresence(username: String): CompletableFuture<*> {
        Log.d("ChatRepo", "Publishing presence: $username")
        return mqttClient?.publishWith()
            ?.topic("presence/$clientId")
            ?.payload(username.toByteArray())
            ?.qos(MqttQos.EXACTLY_ONCE)
            ?.retain(true)
            ?.send() ?: CompletableFuture.completedFuture(null)
    }

    fun observeMessages(): Flow<ChatMessage> = _incomingMessages.asSharedFlow()

    fun observePresence(): Flow<Int> = _onlineUsers.map { it.values.distinct().size }

    fun sendMessage(username: String, text: String) {
        val client = mqttClient ?: return
        if (!client.state.isConnected) {
            Log.w("ChatRepo", "Cannot send: Not connected")
            return
        }

        val payload = gson.toJson(mapOf("user" to username, "message" to text))
        Log.d("ChatRepo", "Sending: $payload")
        client.publishWith()
            .topic("spazradio")
            .payload(payload.toByteArray())
            .qos(MqttQos.EXACTLY_ONCE)
            .send()
            .exceptionally { e ->
                Log.e("ChatRepo", "Send failed", e)
                null
            }
    }

    fun disconnect() {
        Log.d("ChatRepo", "Disconnecting...")
        mqttClient?.disconnect()
        mqttClient = null
        connectedFuture = CompletableFuture()
    }
}
