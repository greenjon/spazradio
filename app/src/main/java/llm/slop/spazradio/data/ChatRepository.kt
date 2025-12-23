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
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
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
    
    private val connectionMutex = Mutex()
    private var isConnecting = false

    // Topics for HiveMQ Cloud testing
    private val CHAT_TOPIC = "spazradio/chat"
    private val PRESENCE_TOPIC_PREFIX = "spazradio/presence/"

    private class ForceCompatibilitySSLSocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
        override fun createSocket(): Socket = delegate.createSocket()
        override fun createSocket(host: String?, port: Int): Socket {
            val ipv4Address = InetAddress.getAllByName(host)
                .firstOrNull { it is Inet4Address }
                ?: throw java.net.UnknownHostException("No IPv4 address found for $host")
            return delegate.createSocket(ipv4Address, port)
        }
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = delegate.createSocket(host, port, localHost, localPort)
        override fun createSocket(host: InetAddress?, port: Int): Socket = delegate.createSocket(host, port)
        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = delegate.createSocket(address, port, localAddress, localPort)
        override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket = delegate.createSocket(s, host, port, autoClose)
    }

    suspend fun fetchHistory(): List<ChatMessage> = withContext(Dispatchers.IO) {
        // Old history endpoint is incompatible with HiveMQ broker. 
        // Returning empty list for testing phase.
        emptyList<ChatMessage>()
    }

    private fun getOrCreateMqttClient(): MqttClient {
        mqttClient?.let { return it }

        val serverUri = "wss://e127037ebb0d4595bf76f7aedcdc16d0.s1.eu.hivemq.cloud:8884/mqtt"
        val client = MqttClient(serverUri, clientId, MemoryPersistence())
        
        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.d("ChatRepo", "MQTT Connect Complete. Reconnect: $reconnect")
                repositoryScope.launch {
                    connectionMutex.withLock { isConnecting = false }
                }
                _connectionError.value = null
                subscribeAll()
            }

            override fun connectionLost(cause: Throwable?) {
                Log.w("ChatRepo", "MQTT Connection Lost: ${cause?.message}")
                _connectionError.value = cause?.message ?: "Connection lost"
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.payload?.let { String(it) } ?: return
                if (topic == CHAT_TOPIC) {
                    try {
                        val rawMsg = gson.fromJson(payload, ChatMessage::class.java)
                        _incomingMessages.tryEmit(rawMsg.copy(timeReceived = rawMsg.timeReceived / 1000L))
                    } catch (e: Exception) {
                        Log.e("ChatRepo", "JSON parse error")
                    }
                } else if (topic?.startsWith(PRESENCE_TOPIC_PREFIX) == true) {
                    val id = topic.substringAfter(PRESENCE_TOPIC_PREFIX)
                    val name = payload
                    val current = _onlineUsers.value.toMutableMap()
                    if (name.isEmpty()) current.remove(id) else current[id] = name
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
            connectionMutex.withLock {
                if (client.isConnected || isConnecting) {
                    Log.d("ChatRepo", "Connect attempt blocked: Busy or already connected")
                    if (client.isConnected) publishPresence(username)
                    return@launch
                }
                isConnecting = true
            }

            try {
                _connectionError.value = null
                
                val sslContext = SSLContext.getInstance("TLSv1.2")
                sslContext.init(null, null, null)
                val compatibilityFactory = ForceCompatibilitySSLSocketFactory(sslContext.socketFactory)

                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    isAutomaticReconnect = true
                    connectionTimeout = 30
                    keepAliveInterval = 30
                    mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
                    userName = "spazradio"
                    password = "SPAZRadio23".toCharArray()
                    setWill(PRESENCE_TOPIC_PREFIX + clientId, ByteArray(0), 2, true)
                    socketFactory = compatibilityFactory
                }
                
                Log.d("ChatRepo", "Connecting to HiveMQ Cloud...")
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
            mqttClient?.subscribe(CHAT_TOPIC, 2)
            mqttClient?.subscribe(PRESENCE_TOPIC_PREFIX + "#", 2)
            _connectionError.value = null
        } catch (e: Exception) {
            Log.e("ChatRepo", "Subscribe failed")
        }
    }

    private fun publishPresence(username: String) {
        val client = mqttClient ?: return
        if (client.isConnected) {
            try {
                client.publish(PRESENCE_TOPIC_PREFIX + clientId, username.toByteArray(), 2, true)
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
                client.publish(CHAT_TOPIC, payload.toByteArray(), 2, false)
            } catch (e: Exception) {
                _connectionError.value = "Send failed: ${e.message}"
            }
        }
    }

    fun disconnect() {
        try {
            repositoryScope.launch {
                connectionMutex.withLock { isConnecting = false }
            }
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()
            }
            mqttClient = null
            _connectionError.value = null
        } catch (e: Exception) {
            Log.e("ChatRepo", "Error during disconnect")
        }
    }
}
