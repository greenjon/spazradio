package llm.slop.spazradio

import kotlinx.coroutines.delay
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import llm.slop.spazradio.ui.theme.SpazRadioTheme
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.os.Bundle
import androidx.core.graphics.createBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.graphics.Canvas as AndroidCanvas

/* ---------- Connection State ---------- */

enum class ConnectionState {
    CONNECTING,
    BUFFERING,
    RECONNECTING,
    PLAYING
}

/* ---------- Activity ---------- */

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb())
        )

        setContent {
            SpazRadioTheme {
                RadioApp()
            }
        }
    }
}

/* ---------- Colors ---------- */

val NeonGreen = Color(0xFF00FF00)
val DeepBlue = Color(0xFF120A8F)
val Magenta = Color(0xFFFF00FF)

/* ---------- App Root ---------- */

@Composable
fun RadioApp(
    scheduleViewModel: ScheduleViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("spaz_radio_prefs", Context.MODE_PRIVATE)
    }

    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var connectionState by remember { mutableStateOf(ConnectionState.CONNECTING) }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    val showSchedule = remember { mutableStateOf(prefs.getBoolean("show_schedule", true)) }
    val lissajousMode = remember { mutableStateOf(prefs.getBoolean("visuals_enabled", true)) }

    LaunchedEffect(showSchedule.value) {
        prefs.edit { putBoolean("show_schedule", showSchedule.value) }
    }

    LaunchedEffect(lissajousMode.value) {
        prefs.edit { putBoolean("visuals_enabled", lissajousMode.value) }
    }

    var trackTitle by remember { mutableStateOf("SPAZ.Radio") }
    var trackListeners by remember { mutableStateOf("") }

    val displayedSecondLine = when (connectionState) {
        ConnectionState.CONNECTING -> "Connecting…"
        ConnectionState.BUFFERING -> "Buffering…"
        ConnectionState.RECONNECTING -> "Reconnecting…"
        ConnectionState.PLAYING -> trackTitle
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /* ---------- Media Controller ---------- */

    LaunchedEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, RadioService::class.java))
        val future: ListenableFuture<MediaController> =
            MediaController.Builder(context, token).buildAsync()

        future.addListener({
            try {
                mediaController = future.get()

                mediaController?.addListener(object : Player.Listener {

                    override fun onPlaybackStateChanged(state: Int) {
                        connectionState = when (state) {
                            Player.STATE_IDLE -> ConnectionState.CONNECTING
                            Player.STATE_BUFFERING -> ConnectionState.BUFFERING
                            Player.STATE_READY ->
                                if (mediaController?.isPlaying == true)
                                    ConnectionState.PLAYING
                                else
                                    ConnectionState.BUFFERING
                            Player.STATE_ENDED -> ConnectionState.RECONNECTING
                            else -> connectionState
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        if (playing) {
                            connectionState = ConnectionState.PLAYING
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        connectionState = ConnectionState.RECONNECTING
                    }

                    override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                        trackTitle = metadata.title?.toString() ?: "SPAZ.Radio"
                        trackListeners = metadata.artist?.toString() ?: ""
                    }
                })

                isPlaying = mediaController?.isPlaying == true

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    /* ---------- UI ---------- */

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(DeepBlue, Magenta, DeepBlue))
                )
                .padding(innerPadding)
        ) {
            val waveform by RadioService.waveformFlow.collectAsState()

            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(1f)) {

                        PlayerHeader(
                            isPlaying = isPlaying,
                            onPlayPause = {
                                if (isPlaying) mediaController?.pause()
                                else mediaController?.play()
                            },
                            trackListeners = trackListeners,
                            onToggleSettings = { showSettings = !showSettings }
                        )

                        TrackTitle(displayedSecondLine)

                        Oscilloscope(
                            waveform = waveform,
                            isPlaying = isPlaying,
                            lissajousMode = lissajousMode.value,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(16.dp)
                        )
                    }

                    if (showSettings || showSchedule.value) {
                        InfoBox(
                            showSettings = showSettings,
                            onCloseSettings = { showSettings = false },
                            lissajousMode = lissajousMode,
                            showSchedule = showSchedule,
                            scheduleViewModel = scheduleViewModel,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(16.dp)
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {

                    PlayerHeader(
                        isPlaying = isPlaying,
                        onPlayPause = {
                            if (isPlaying) mediaController?.pause()
                            else mediaController?.play()
                        },
                        trackListeners = trackListeners,
                        onToggleSettings = { showSettings = !showSettings }
                    )

                    TrackTitle(displayedSecondLine)

                    val showInfoBox = showSettings || showSchedule.value

                    if (lissajousMode.value) {
                        Oscilloscope(
                            waveform = waveform,
                            isPlaying = isPlaying,
                            lissajousMode = lissajousMode.value,
                            modifier = if (showInfoBox) {
                                Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .padding(16.dp)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(16.dp)
                            }
                        )
                    }

                    if (showInfoBox) {
                        InfoBox(
                            showSettings = showSettings,
                            onCloseSettings = { showSettings = false },
                            lissajousMode = lissajousMode,
                            showSchedule = showSchedule,
                            scheduleViewModel = scheduleViewModel,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/* ---------- Existing Composables ---------- */
/* PlayerHeader, TrackTitle, InfoBox, SettingsScreen,
   ScheduleItemRow, Oscilloscope remain unchanged */

@Composable
fun PlayerHeader(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    trackListeners: String,
    onToggleSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Top Left Play/Pause Button
        IconButton(onClick = onPlayPause) {
            Icon(
                painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = NeonGreen,
                modifier = Modifier.size(48.dp)
            )
        }

        // Center: Listeners count
        Text(
            text = "SPAZ.Radio   -   $trackListeners", // Contains "N listening"
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFFFFFF00)
        )

        // Top Right Settings Button
        IconButton(onClick = onToggleSettings) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings),
                contentDescription = stringResource(R.string.settings_title),
                tint = NeonGreen,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
fun TrackTitle(trackTitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = trackTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = NeonGreen
        )
    }
}

@Composable
fun InfoBox(
    showSettings: Boolean,
    onCloseSettings: () -> Unit,
    lissajousMode: MutableState<Boolean>,
    showSchedule: MutableState<Boolean>,
    scheduleViewModel: ScheduleViewModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0x80000000), RoundedCornerShape(16.dp)) // 50% transparent black
            .border(3.dp, NeonGreen, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
    ) {
        if (showSettings) {
            SettingsScreen(
                onBack = onCloseSettings,
                lissajousMode = lissajousMode,
                showSchedule = showSchedule
            )
        } else {
            // Schedule Section (Bottom)
            val schedule by scheduleViewModel.schedule.collectAsState()
            val loading by scheduleViewModel.loading.collectAsState()
            val error by scheduleViewModel.error.collectAsState()

            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = NeonGreen
                )
            } else if (error != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Schedule unavailable", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { scheduleViewModel.loadSchedule() }) {
                        Text("Retry")
                    }
                }
            }
            else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.schedule_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = Color(0xFFFFFF00)
                        )
                    }
                    items(schedule) { item ->
                        ScheduleItemRow(item)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    lissajousMode: MutableState<Boolean>,
    showSchedule: MutableState<Boolean>
) {
    // Use a column but ensure it fits in the container
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Absorb clicks */ },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top // Align top to start listing settings
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFFFFFF00),
            textAlign = TextAlign.Left,
            //       fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Scrollable content if settings grow
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Lissajous Mode Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.show_visuals),
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonGreen
                )
                Checkbox(
                    checked = lissajousMode.value,
                    onCheckedChange = { lissajousMode.value = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = NeonGreen,
                        uncheckedColor = NeonGreen,
                        checkmarkColor = DeepBlue
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Show Schedule Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.show_schedule),
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonGreen
                )
                Checkbox(
                    checked = showSchedule.value,
                    onCheckedChange = { showSchedule.value = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = NeonGreen,
                        uncheckedColor = NeonGreen,
                        checkmarkColor = DeepBlue
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

        }

        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Close Settings",
                tint = NeonGreen,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
fun ScheduleItemRow(item: ScheduleItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "${item.datePart} • ${item.startTime} - ${item.endTime}",
            style = MaterialTheme.typography.bodyLarge,
            color = NeonGreen
        )
        Text(
            text = item.showName,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFFFFF00)

        )
    }
}

@Composable
fun Oscilloscope(
    waveform: ByteArray?,
    isPlaying: Boolean,
    lissajousMode: Boolean,
    modifier: Modifier = Modifier
) {
    /* ---------- Frame Clock ---------- */

    val frameClock = remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16L * 2) // ~30 FPS
            frameClock.longValue++
        }
    }

    /* ---------- Persistent Drawing State ---------- */

    val bitmapRef = remember { mutableStateOf<Bitmap?>(null) }
    val canvasRef = remember { mutableStateOf<AndroidCanvas?>(null) }

    var loudnessEnv by remember { mutableFloatStateOf(12f) }

    /* ---------- Paints ---------- */

    val fadePaint = remember {
        android.graphics.Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            color = android.graphics.Color.argb(28, 0, 0, 0)
        }
    }

    val linePaint = remember {
        android.graphics.Paint().apply {
            color = NeonGreen.toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f
            isAntiAlias = true
            setShadowLayer(6f, 0f, 0f, NeonGreen.toArgb())
        }
    }

    val path = remember { android.graphics.Path() }

    /* ---------- Canvas ---------- */

    androidx.compose.foundation.Canvas(modifier = modifier) {
        frameClock.longValue // trigger redraw

        val width = size.width.toInt()
        val height = size.height.toInt()

        if (bitmapRef.value == null ||
            bitmapRef.value!!.width != width ||
            bitmapRef.value!!.height != height
        ) {
            bitmapRef.value = createBitmap(width, height)
            canvasRef.value = AndroidCanvas(bitmapRef.value!!)
        }

        val bmp = bitmapRef.value!!
        val cvs = canvasRef.value!!

        /* ---------- Fade Previous Frame ---------- */

        cvs.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)

        if (isPlaying && waveform != null && waveform.size > 8) {

            /* ---------- RMS + Peak ---------- */

            var sumSq = 0f
            var peak = 0

            for (b in waveform) {
                val v = (b.toInt() and 0xFF) - 128
                val a = kotlin.math.abs(v)
                if (a > peak) peak = a
                sumSq += v * v
            }

            val rms = kotlin.math.sqrt(sumSq / waveform.size)

            /* ---------- Loudness Envelope ---------- */

            val attack = 0.22f
            val release = 0.06f

            loudnessEnv += if (rms > loudnessEnv)
                (rms - loudnessEnv) * attack
            else
                (rms - loudnessEnv) * release

            /* ---------- Visual AGC (Signal Gain) ---------- */

            val targetRms = 34f
            val gain = (targetRms / (loudnessEnv + 1f))
                .coerceIn(1.3f, 6.5f)

            /* ---------- Dynamic Trail ---------- */

            fadePaint.color = android.graphics.Color.argb(
                when {
                    peak < 10 -> 18
                    peak < 30 -> 28
                    peak < 60 -> 40
                    else -> 55
                },
                0, 0, 0
            )

            /* ---------- Geometry ---------- */

            val cx = width / 2f
            val cy = height / 2f
            val xScale = width * 0.48f
            val yScale = height * 0.48f

            path.reset()

            if (lissajousMode) {

                val count = waveform.size
                val phaseShift = (count * 0.37f).toInt()

                var lastA = 0f
                var lastB = 0f
                var first = true

                val step = (count / 256).coerceAtLeast(1)
                for (i in 0 until count step step) {

                    val rawA =
                        ((waveform[i].toInt() and 0xFF) - 128) / 128f
                    val j = (i + phaseShift) % count
                    val rawB =
                        ((waveform[j].toInt() and 0xFF) - 128) / 128f

                    val a =
                        (0.85f * rawA + 0.15f * (rawA - lastA)) * gain
                    val b =
                        (0.85f * rawB + 0.15f * (rawB - lastB)) * gain

                    lastA = rawA
                    lastB = rawB

                    val ax = a.coerceIn(-1.2f, 1.2f)
                    val by = b.coerceIn(-1.2f, 1.2f)

                    val x = cx + ax * xScale
                    val y = cy + by * yScale

                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                }

                path.close()
                cvs.drawPath(path, linePaint)
            }
        }

        drawImage(bmp.asImageBitmap())
    }
}
