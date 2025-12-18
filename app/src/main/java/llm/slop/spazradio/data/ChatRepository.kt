package llm.slop.spazradio.data

import android.util.Log
import com.google.gson.Gson
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

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
                        val body = response.body.string()
                        val historyResponse = gson.fromJson(body, HistoryResponse::class.java)
                        result = historyResponse.history
                        Log.d("ChatRepo", "History loaded on attempt $i")
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
                .subprotocol("mqttv3.1") // Force legacy subprotocol
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

        Log.d("ChatRepo", "Sending MQTT CONNECT request...")
        val connectFuture = client.connectWith()
            .keepAlive(30)
            .cleanSession(true)
            .willPublish()
                .topic("presence/$clientId")
                .payload(ByteArray(0))
                .qos(MqttQos.EXACTLY_ONCE)
                .retain(true)
                .applyWillPublish()
            .send()

        // Handle timeout manually for API < 31
        val timeoutTask = scheduler.schedule({
            if (!connectFuture.isDone) {
                connectFuture.cancel(true)
                Log.e("ChatRepo", "MQTT Connect Timed Out after 60s")
            }
        }, 60, TimeUnit.SECONDS)

        connectFuture.whenComplete { connAck, throwable ->
            timeoutTask.cancel(false)
            if (throwable != null) {
                Log.e("ChatRepo", "MQTT Connect Failed: ${throwable.message}")
                logErrorTree(throwable)
            } else if (connAck != null) {
                Log.d("ChatRepo", "MQTT Connected! RC: ${connAck.returnCode}")
                
                subscribe()
                subscribePresence()
                publishPresence(username)
                
                connectedFuture.complete(Unit)
            }
        }
    }

    private fun subscribe(): CompletableFuture<*> {
        Log.d("ChatRepo", "Subscribing to spazradio...")
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
        Log.d("ChatRepo", "Subscribing to presence/#...")
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
        Log.d("ChatRepo", "Publishing presence for $username...")
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
        Log.d("ChatRepo", "Sending message: $payload")
        client.publishWith()
            .topic("spazradio")
            .payload(payload.toByteArray())
            .qos(MqttQos.EXACTLY_ONCE)
            .send()
            .exceptionally { e ->
                Log.e("ChatRepo", "Send failed: ${e.message}")
                logErrorTree(e)
                null
            }
    }

    fun disconnect() {
        Log.d("ChatRepo", "Disconnecting...")
        mqttClient?.disconnect()
        mqttClient = null
        connectedFuture = CompletableFuture()
    }

    private fun logErrorTree(e: Throwable?) {
        var current = e
        var depth = 0
        while (current != null && depth < 10) {
            Log.e("ChatRepo", "  Cause $depth: ${current.javaClass.simpleName}: ${current.message}")
            current = current.cause
            depth++
        }
    }
}
