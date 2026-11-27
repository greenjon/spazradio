package llm.slop.spazradio.utils

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Singleton providing a shared OkHttpClient instance for the app's own requests.
 * It uses the platform's default trust manager and hostname verifier.
 */
object SpazNetwork {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
