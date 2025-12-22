package llm.slop.spazradio.data

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory
import javax.net.ssl.SSLSocketFactory
import kotlin.random.Random

class ChatRepository(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    private val clientId = "mut${Random.nextInt(1, 1000000)}"
    private var mqttClient: MqttClient? = null

    private val _onlineUsers = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _incomingMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    
    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // DIRTY HACK: An SSLSocketFactory that forces IPv4 by filtering out IPv6 addresses.
    // For wss:// connections, Paho requires the factory to be an instance of SSLSocketFactory.
    private class ForceIpv4SSLSocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

        override fun createSocket(): Socket = delegate.createSocket()
        
        override fun createSocket(host: String?, port: Int): Socket {
            val ipv4Address = InetAddress.getAllByName(host)
                .firstOrNull { it is Inet4Address }
                ?: throw java.net.UnknownHostException("No IPv4 address found for $host")
            return delegate.createSocket(ipv4Address, port)
        }

        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
            return delegate.createSocket(host, port, localHost, localPort)
        }

        override fun createSocket(host: InetAddress?, port: Int): Socket = delegate.createSocket(host, port)

        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
            return delegate.createSocket(address, port, localAddress, localPort)
        }

        override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
            return delegate.createSocket(s, host, port, autoClose)
        }
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
                        val json = body.string()
                        val historyResponse = gson.fromJson(json, HistoryResponse::class.java)
                        
                        Log.d("ChatRepo", "History loaded and converted from ms on attempt $i")
                        return@withContext historyResponse.history.map { msg ->
                            msg.copy(timeReceived = msg.timeReceived / 1000L)
                        }
                    } else {
                        Log.e("ChatRepo", "History fetch failed (attempt $i): ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatRepo", "History fetch exception (attempt $i): ${e.message}")
                if (i < 3) delay(1000)
            }
        }
        emptyList()
    }

    private fun getOrCreateMqttClient(): MqttClient {
        mqttClient?.let { return it }

        Log.d("ChatRepo", "Creating Paho MqttClient for $clientId")
        val serverUri = "wss://radio.spaz.org:1885/mqtt"
        val client = MqttClient(serverUri, clientId, MemoryPersistence())
        
        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.d("ChatRepo", "MQTT Connect Complete. Reconnect: $reconnect")
                _connectionError.value = null
                subscribeAll()
            }

            override fun connectionLost(cause: Throwable?) {
                Log.w("ChatRepo", "MQTT Connection Lost: ${cause?.message}")
                _connectionError.value = cause?.message ?: "Connection lost"
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
                    _connectionError.value = null
                    Log.d("ChatRepo", "Connecting to MQTT (v3.1.1)...")
                    val options = MqttConnectOptions().apply {
                        isCleanSession = true
                        isAutomaticReconnect = true
                        connectionTimeout = 30
                        keepAliveInterval = 30
                        mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
                        setWill("presence/$clientId", ByteArray(0), 2, true)
                        
                        // Apply the IPv4 hack using a proper SSLSocketFactory subclass
                        socketFactory = ForceIpv4SSLSocketFactory(SSLSocketFactory.getDefault() as SSLSocketFactory)
                    }
                    
                    withContext(Dispatchers.IO) {
                        client.connect(options)
                    }
                    Log.d("ChatRepo", "MQTT Connected successfully")
                }
                publishPresence(username)
            } catch (e: Exception) {
                Log.e("ChatRepo", "MQTT Connect Failed", e)
                _connectionError.value = e.cause?.message ?: e.message ?: "Connect failed"
            }
        }
    }

    private fun subscribeAll() {
        try {
            mqttClient?.subscribe("spazradio", 2)
            mqttClient?.subscribe("presence/#", 2)
            Log.d("ChatRepo", "Subscribed to topics (QoS 2)")
            _connectionError.value = null
        } catch (e: Exception) {
            Log.e("ChatRepo", "Subscribe failed", e)
            _connectionError.value = "Subscribe failed: ${e.message}"
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
            _connectionError.value = "Not connected"
            return
        }

        repositoryScope.launch(Dispatchers.IO) {
            try {
                val payload = gson.toJson(mapOf("user" to username, "message" to text))
                client.publish("spazradio", payload.toByteArray(), 2, false)
                Log.d("ChatRepo", "Message sent: $text")
            } catch (e: Exception) {
                Log.e("ChatRepo", "Send failed", e)
                _connectionError.value = "Send failed: ${e.message}"
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
            _connectionError.value = null
        } catch (e: Exception) {
            Log.e("ChatRepo", "Error during disconnect", e)
        }
    }
}
