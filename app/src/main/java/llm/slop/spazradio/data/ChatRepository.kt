package llm.slop.spazradio.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import javax.net.ssl.SSLContext

class ChatRepository(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    private val clientId = newClientId()
    @Volatile private var mqttClient: MqttClient? = null
    @Volatile private var currentUsername: String? = null
    /** The connection exists only while the app is in the foreground (set by the ViewModel from the process lifecycle). */
    @Volatile private var foreground = false

    private val _onlineUsers = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _incomingMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    // One event per successful (re)connect, after the subscriptions: the chat then fetches the history again to
    // fill in whatever was sent while the connection was down.
    private val _connected = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    // One at a time, in the order launched: connect, disconnect and presence changes never overlap. Cancelled
    // only by [shutdown], after its own disconnect has run.
    private val serial = Dispatchers.IO.limitedParallelism(1)
    private val repositoryScope = CoroutineScope(serial + SupervisorJob())
    private var lastSend = 0L

    private fun newClientId(): String {
        val rnd = java.security.SecureRandom()
        return "mut" + String(CharArray(6) { Character.forDigit(rnd.nextInt(36), 36) })
    }

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
                        if (body != null) {
                            ChatMessages.parseHistory(body.string(), System.currentTimeMillis())
                                ?.let { return@withContext it }
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
                // Announce only in the foreground: a reconnect in the background must not mark us online again.
                if (foreground) currentUsername?.let { publishPresence(it, true) }
                _connectionError.value = null
                subscribeAll()
                _connected.tryEmit(Unit)
            }

            override fun connectionLost(cause: Throwable?) {
                Log.w("ChatRepo", "MQTT Connection Lost: ${cause?.message}")
                _connectionError.value = cause?.message ?: "Connection lost"
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.payload?.let { String(it) } ?: ""
                if (topic == "spazradio") {
                    // Live messages carry no time (only the server's log stamps one): use the moment it arrived.
                    val json = try { JsonParser.parseString(payload) } catch (_: Exception) { null }
                    val msg = ChatMessages.parse(json, System.currentTimeMillis(), trustTime = false)
                    if (msg != null) _incomingMessages.tryEmit(msg) else Log.w("ChatRepo", "Dropped a malformed chat message")
                } else if (topic?.startsWith("presence/") == true) {
                    val id = topic.substringAfter("presence/")
                    val name = ChatMessages.presenceName(payload)
                    // An empty payload means offline (graceful or LWT)
                    _onlineUsers.update { ChatMessages.updateRoster(it, id, name) }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        mqttClient = client
        return client
    }

    /** Called from the process lifecycle: ON_START true (then [connect]), ON_STOP false (then [disconnect]). */
    fun setForeground(value: Boolean) { foreground = value }

    fun connect(username: String) {
        currentUsername = username
        if (!foreground) return
        repositoryScope.launch {
            if (!foreground) return@launch
            val client = getOrCreateMqttClient()
            if (client.isConnected) {
                publishPresence(username, true)
                return@launch
            }
            try {
                _connectionError.value = null

                val sslContext = SSLContext.getInstance("TLSv1.2")
                sslContext.init(null, null, null)

                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    isAutomaticReconnect = true
                    connectionTimeout = 30
                    keepAliveInterval = 60 // Broker checks every 60s
                    mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1

                    // LAST WILL: If we crash, the broker publishes an empty string to our presence topic
                    setWill("presence/$clientId", "".toByteArray(), 1, true)

                    socketFactory = sslContext.socketFactory
                }

                Log.d("ChatRepo", "Starting MQTT connection sequence (TLS 1.2 forced)...")
                client.connect(options)
            } catch (e: Exception) {
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

    fun publishPresence(username: String, isOnline: Boolean) {
        repositoryScope.launch {
            val client = mqttClient ?: return@launch
            if (!client.isConnected) return@launch
            publishPresenceNow(client, if (isOnline) username else "")
        }
    }

    private fun publishPresenceNow(client: MqttClient, payload: String) {
        try {
            val message = MqttMessage(payload.toByteArray()).apply {
                qos = 1
                isRetained = true // New joiners see current status
            }
            client.publish("presence/$clientId", message)
        } catch (e: Exception) {
            Log.e("ChatRepo", "Publish presence failed: ${e.message}")
        }
    }

    fun observeMessages(): Flow<ChatMessage> = _incomingMessages.asSharedFlow()
    fun observeConnected(): Flow<Unit> = _connected.asSharedFlow()
    fun observePresenceCount(): Flow<Int> = _onlineUsers.map { ChatMessages.rosterNames(it).size }
    fun observeOnlineNames(): Flow<List<String>> = _onlineUsers.map { ChatMessages.rosterNames(it) }

    /** True when the message went out; false (and nothing sent) when empty, too soon after the last one, or offline. */
    suspend fun sendMessage(username: String, text: String): Boolean = withContext(serial) {
        val body = ChatMessages.outgoingText(text)
        val name = ChatMessages.cleanNick(username)
        val client = mqttClient
        val now = System.currentTimeMillis()
        if (body.isEmpty() || name.isEmpty() || client == null || !client.isConnected || now - lastSend < SEND_GAP_MS) {
            return@withContext false
        }
        try {
            val payload = gson.toJson(mapOf("user" to name, "message" to body))
            client.publish("spazradio", payload.toByteArray(), 2, false)
            lastSend = now
            true
        } catch (e: Exception) {
            Log.w("ChatRepo", "Send failed: ${e.message}")
            false
        }
    }

    /** Clears our presence, then closes the connection for good (also when the link is down). Returns at once. */
    fun disconnect() {
        currentUsername = null
        repositoryScope.launch { closeClient() }
    }

    /** [disconnect], then ends the repository's own scope: for the ViewModel's onCleared. */
    fun shutdown() {
        currentUsername = null
        repositoryScope.launch { closeClient() }.invokeOnCompletion { repositoryScope.cancel() }
    }

    private fun closeClient() {
        val client = mqttClient
        mqttClient = null
        if (client != null) {
            // The clear goes out before the DISCONNECT, in the same call (a graceful DISCONNECT skips the will).
            if (client.isConnected) publishPresenceNow(client, "")
            try { client.disconnect(2000) } catch (_: Exception) {
                try { client.disconnectForcibly(0, 1000) } catch (_: Exception) {}
            }
            try { client.close() } catch (_: Exception) {}
        }
        _connectionError.value = null
        _onlineUsers.value = emptyMap()
    }

    private companion object {
        const val SEND_GAP_MS = 1000L
    }
}
