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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CompletableFuture
import kotlin.random.Random

class ChatRepository(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    private val clientId = "mut${Random.nextInt(1, 1000000)}"
    private var mqttClient: Mqtt3AsyncClient? = null

    private val _onlineUsers = MutableStateFlow<Map<String, String>>(emptyMap())
    private val connectedFuture = CompletableFuture<Unit>()

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
            Log.e("ChatRepo", "Failed to fetch history", e)
            emptyList()
        }
    }

    private fun getOrCreateMqttClient(): Mqtt3AsyncClient {
        mqttClient?.let { return it }

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
        if (client.state.isConnected) {
            // If already connected, just publish presence as Paho does in its loop
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
            .thenCompose { 
                subscribe()
                subscribePresence()
                publishPresence(username)
            }
            .thenAccept {
                connectedFuture.complete(Unit)
            }
            .exceptionally { e ->
                Log.e("ChatRepo", "MQTT Connect Failed", e)
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
                    _incomingMessages.value = message
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
        return mqttClient?.publishWith()
            ?.topic("presence/$clientId")
            ?.payload(username.toByteArray())
            ?.qos(MqttQos.EXACTLY_ONCE)
            ?.retain(true)
            ?.send() ?: CompletableFuture.completedFuture(null)
    }

    private val _incomingMessages = MutableStateFlow<ChatMessage?>(null)

    fun observeMessages(): Flow<ChatMessage> = callbackFlow {
        val job = kotlinx.coroutines.MainScope().launch(Dispatchers.Default) {
            _incomingMessages.collect { msg ->
                if (msg != null) trySend(msg)
            }
        }
        awaitClose { job.cancel() }
    }

    fun observePresence(): Flow<Int> = callbackFlow {
        val job = kotlinx.coroutines.MainScope().launch(Dispatchers.Default) {
            _onlineUsers.collect { users ->
                trySend(users.values.distinct().size)
            }
        }
        awaitClose { job.cancel() }
    }

    fun sendMessage(username: String, text: String) {
        val client = mqttClient ?: return
        if (!client.state.isConnected) return

        val payload = gson.toJson(mapOf("user" to username, "message" to text))
        client.publishWith()
            .topic("spazradio")
            .payload(payload.toByteArray())
            .qos(MqttQos.EXACTLY_ONCE)
            .send()
    }

    fun disconnect() {
        mqttClient?.disconnect()
        mqttClient = null
    }
}
