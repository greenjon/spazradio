package llm.slop.spazradio.utils

import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Singleton providing a shared OkHttpClient instance for the entire app.
 */
object SpazNetwork {

    val client: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .socketFactory(ForceIpv4SocketFactory())
            .build()
    }

    /**
     * Reuses our IPv4 logic for all HTTP traffic to ensure consistency.
     */
    private class ForceIpv4SocketFactory : SocketFactory() {
        private val delegate = getDefault()
        
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
    }
}
