package llm.slop.spazradio.ui.components

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import llm.slop.spazradio.visuals.PcmTap
import llm.slop.spazradio.visuals.VisualQuality
import llm.slop.spazradio.visuals.VisualsSettings
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "VisualizerView"
private const val HOST = "appassets.androidplatform.net"
private const val PAGE = "https://$HOST/visuals/index.html?embed"
private const val STARTUP_TIMEOUT_MS = 20_000L
private const val MAX_RESTARTS = 2
private const val FIRST_LOOK_GRACE_MS = 6_000L // the first look needs a moment to load before a freeze can show it

/**
 * The Liquid LSD web TV, picture only, bundled in assets/visuals (scripts/sync_visuals.sh) and served to a
 * WebView from a made-up https origin, so it works offline. The radio plays in RadioService; its decoded audio
 * reaches the page through [PcmTap] -> `LSD.feed()`. The view takes no touches and the page loads nothing from
 * the network. [onFailed] fires if the picture can't start (no WebView, no WebGL2, repeated crashes), so the
 * caller can show the oscilloscope instead.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VisualizerView(
    settings: VisualsSettings,
    active: Boolean,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handler = remember { Handler(Looper.getMainLooper()) }
    val feedHandler = remember { Handler(Looper.getMainLooper()) } // the audio feed's own queue, cleared on its own
    var restarts by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val powerSave = rememberPowerSave(context)
    val currentSettings by rememberUpdatedState(settings)
    val currentOnFailed by rememberUpdatedState(onFailed)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) visible = true
            if (event == Lifecycle.Event.ON_STOP) visible = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // A page that never says it is up (module failed to load, shader compile hung) counts as failed.
    LaunchedEffect(restarts, visible) {
        if (!visible) return@LaunchedEffect // a hidden page draws no frames, so it can't say it is up
        delay(STARTUP_TIMEOUT_MS)
        if (!ready) currentOnFailed()
    }

    // Options: the app's settings, with Auto quality dropping to Low under the system's battery saver.
    LaunchedEffect(webView, ready, settings, powerSave) {
        val web = webView
        if (web == null || !ready) return@LaunchedEffect
        val quality = if (settings.quality == VisualQuality.AUTO && powerSave) VisualQuality.LOW else settings.quality
        web.evaluateJavascript(
            "(function(){var L=window.LSD;if(!L||!L.setOption)return;" +
                "L.setOption('quality','${quality.js}');" +
                "L.setOption('reactivity',${settings.reactivity});" +
                "L.setOption('reducedMotion','${settings.reducedMotion.js}');" +
                "L.setOption('live',${settings.live});})()",
            null,
        )
    }

    // Drawing and analysis run only while the app is on screen and the radio is playing.
    var warmedUp by remember { mutableStateOf(false) }
    LaunchedEffect(webView, ready, active, visible) {
        val web = webView
        if (web == null || !ready) return@LaunchedEffect
        if (active && visible) {
            warmedUp = true
            web.onResume()
            web.evaluateJavascript("window.LSD&&LSD.resume()", null)
        } else {
            // Just started with the radio not playing: keep drawing until the first look is on screen, then freeze.
            if (!warmedUp && visible) {
                delay(FIRST_LOOK_GRACE_MS)
                warmedUp = true
            }
            web.evaluateJavascript("window.LSD&&LSD.pause()", null)
            web.onPause()
        }
    }

    // The audio feed: while the picture runs, decoded audio goes to the page, held back by the sync setting.
    DisposableEffect(webView, ready, active, visible) {
        val web = webView
        if (web != null && ready && active && visible) {
            val inFlight = AtomicInteger(0)
            PcmTap.listener = { chunk, rate ->
                val delayMs = currentSettings.syncMs.toLong()
                // About 25 chunks a second; more than the sync delay plus a second's worth waiting means the UI thread is stuck.
                if (inFlight.get() < 25 + delayMs / 40) {
                    inFlight.incrementAndGet()
                    val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
                    feedHandler.postDelayed({
                        inFlight.decrementAndGet()
                        web.evaluateJavascript("window.LSD&&LSD.feed('$b64',$rate)", null)
                    }, delayMs)
                }
            }
        }
        onDispose {
            PcmTap.listener = null
            feedHandler.removeCallbacksAndMessages(null)
        }
    }

    key(restarts) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val bridge = LsdHostBridge(
                    handler,
                    onReady = { ready = true },
                    onFailed = { reason -> Log.w(TAG, "visuals failed: $reason"); currentOnFailed() },
                )
                try {
                    createWebView(ctx, bridge) {
                        handler.post {
                            ready = false
                            if (restarts < MAX_RESTARTS) restarts++ else currentOnFailed()
                        }
                    }.also { w -> handler.post { webView = w } } // state is not written during composition
                } catch (e: Exception) { // no WebView installed or it is being updated
                    Log.w(TAG, "WebView unavailable", e)
                    handler.post { currentOnFailed() }
                    View(ctx)
                }
            },
            onRelease = { view ->
                (view as? WebView)?.let { it.stopLoading(); it.destroy() }
                handler.post { if (webView === view) webView = null; ready = false }
            },
        )
    }
}

/** The page's callbacks (`window.LSDHost`); they arrive on a WebView thread, so they hop to the UI thread. */
private class LsdHostBridge(
    private val handler: Handler,
    private val onReady: () -> Unit,
    private val onFailed: (String) -> Unit,
) {
    @JavascriptInterface
    fun ready() { handler.post(onReady) }

    @JavascriptInterface
    fun failed(reason: String?) { handler.post { onFailed(reason.orEmpty()) } }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
private fun createWebView(ctx: Context, bridge: LsdHostBridge, onRenderGone: () -> Unit): WebView =
    WebView(ctx).apply {
        setBackgroundColor(Color.BLACK)
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        isLongClickable = false
        isHapticFeedbackEnabled = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false // the page's AudioContext starts by itself
            setSupportZoom(false)
        }
        addJavascriptInterface(bridge, "LSDHost")
        webViewClient = AssetClient(ctx.assets, onRenderGone)
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                Log.d(TAG, "js ${m.messageLevel()}: ${m.message()} (${m.sourceId().substringAfterLast('/')}:${m.lineNumber()})")
                return true
            }
        }
        loadUrl(PAGE)
    }

/** Serves assets/visuals/ at https://appassets.androidplatform.net/visuals/ and refuses everything else. */
private class AssetClient(private val assets: AssetManager, private val onRenderGone: () -> Unit) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        val path = url.path.orEmpty().removePrefix("/")
        if (url.scheme != "https" || url.host != HOST) return reply(403, "Forbidden")
        if (!path.startsWith("visuals/") || path.contains("..")) return reply(404, "Not Found")
        val stream = try { assets.open(path) } catch (_: Exception) { return reply(404, "Not Found") }
        val mime = mimeFor(path)
        val text = mime.startsWith("text/") || mime == "application/json"
        return WebResourceResponse(mime, if (text) "utf-8" else null, 200, "OK", mapOf("Cache-Control" to "no-store"), stream)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Log.w(TAG, "WebView renderer gone (crashed=${detail.didCrash()})")
        onRenderGone()
        return true
    }

    private fun reply(status: Int, reason: String) =
        WebResourceResponse("text/plain", "utf-8", status, reason, emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun mimeFor(path: String) = when (path.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "frag", "vert", "lsd", "lsdplay", "txt" -> "text/plain"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }
}

@Composable
private fun rememberPowerSave(context: Context): Boolean {
    val pm = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var saving by remember { mutableStateOf(pm.isPowerSaveMode) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { saving = pm.isPowerSaveMode }
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return saving
}
