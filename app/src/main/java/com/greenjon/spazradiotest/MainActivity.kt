package com.greenjon.spazradiotest

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.greenjon.spazradiotest.ui.theme.SpazradiotestTheme
import android.graphics.Canvas as AndroidCanvas

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { _: Boolean ->
            // Handle permission result if needed
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            SpazradiotestTheme {
                RadioApp()
            }
        }
    }
}

val NeonGreen = Color(0xFF00FF00)
val DeepBlue = Color(0xFF120A8F)
val Magenta = Color(0xFFFF00FF)

@Composable
fun RadioApp(
    scheduleViewModel: ScheduleViewModel = viewModel()
) {
    val context = LocalContext.current
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    val showSchedule = rememberSaveable { mutableStateOf(true) }

    // Settings State
    val lissajousMode = rememberSaveable { mutableStateOf(true) }
 //   val tension = remember { mutableFloatStateOf(0.55f) }
//    val gainRange = remember { mutableStateOf(0.5f..1.8f) }

    var trackTitle by remember { mutableStateOf("Connecting...") }
    var trackListeners by remember { mutableStateOf("") }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, RadioService::class.java))
        val controllerFuture: ListenableFuture<MediaController> =
            MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        trackTitle = mediaMetadata.title?.toString() ?: "Radio Spaz"
                        trackListeners = mediaMetadata.artist?.toString() ?: ""
                    }
                })
                isPlaying = mediaController?.isPlaying == true
                trackTitle = mediaController?.mediaMetadata?.title?.toString() ?: "Radio Spaz"
                trackListeners = mediaController?.mediaMetadata?.artist?.toString() ?: ""

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(DeepBlue, Magenta, DeepBlue)
                    )
                )
                .padding(innerPadding)
        ) {
            val waveform by RadioService.waveformFlow.collectAsState()
            
            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Column (Controls + Visualizer)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        PlayerHeader(
                            isPlaying = isPlaying,
                            onPlayPause = {
                                if (isPlaying) mediaController?.pause() else mediaController?.play()
                            },
                            trackListeners = trackListeners,
                            onToggleSettings = { showSettings = !showSettings }
                        )

                        TrackTitle(trackTitle = trackTitle)

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

                    // Right Column (InfoBox)
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
                // Portrait (Original)
                Column(modifier = Modifier.fillMaxSize()) {
                    PlayerHeader(
                        isPlaying = isPlaying,
                        onPlayPause = {
                            if (isPlaying) mediaController?.pause() else mediaController?.play()
                        },
                        trackListeners = trackListeners,
                        onToggleSettings = { showSettings = !showSettings }
                    )

                    TrackTitle(trackTitle = trackTitle)

                    if (lissajousMode.value) {
                        Oscilloscope(
                            waveform = waveform,
                            isPlaying = isPlaying,
                            lissajousMode = lissajousMode.value,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
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
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

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
            .padding(16.dp),
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
            text = trackListeners, // Contains "N listening"
            style = MaterialTheme.typography.labelMedium,
            color = NeonGreen
        )

        // Top Right Settings Button
        IconButton(onClick = onToggleSettings) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings),
                contentDescription = "Settings",
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = trackTitle,
            style = MaterialTheme.typography.titleMedium,
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
                //        tension = tension,
                //gainRange = gainRange
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
                Text(
                    text = "Error: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    item {
                        Text(
                            text = "Schedule",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = NeonGreen
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
//    tension: MutableFloatState,
    //gainRange: MutableState<ClosedFloatingPointRange<Float>>
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
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = NeonGreen,
            fontWeight = FontWeight.Bold,
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
                    text = "Visuals",
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
                    text = "Show Schedule",
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

            // Bézier Tension Control
//            Column(modifier = Modifier
//                .fillMaxWidth()
//                .padding(bottom = 16.dp)) {
//                Text(
//                    text = "Bézier Tension: ${String.format(Locale.US, "%.2f", tension.floatValue)}",
//                    style = MaterialTheme.typography.bodyLarge,
//                    color = NeonGreen
//                )
//                Slider(
//                    value = tension.floatValue,
//                    onValueChange = { tension.floatValue = it },
//                    valueRange = 0f..1f,
//                    colors = SliderDefaults.colors(
//                        thumbColor = NeonGreen,
//                        activeTrackColor = NeonGreen,
//                        inactiveTrackColor = DeepBlue.copy(alpha = 0.5f)
//                    )
//                )
//            }

            // Auto-gain Range Control
//            Column(modifier = Modifier
//                .fillMaxWidth()
//                .padding(bottom = 16.dp)) {
//                Text(
//                    text = "Auto-gain Range: ${String.format(Locale.US, "%.1f", gainRange.value.start)} - ${
//                        String.format(
//                            Locale.US,
//                            "%.1f",
//                            gainRange.value.endInclusive
//                        )
//                    }",
//                    style = MaterialTheme.typography.bodyLarge,
//                    color = NeonGreen
//                )
//                RangeSlider(
//                    value = gainRange.value,
//                    onValueChange = { gainRange.value = it },
//                    valueRange = 0f..3f,
//                    colors = SliderDefaults.colors(
//                        thumbColor = NeonGreen,
//                        activeTrackColor = NeonGreen,
//                        inactiveTrackColor = DeepBlue.copy(alpha = 0.5f)
//                    )
//                )
//            }
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
            style = MaterialTheme.typography.labelMedium,
            color = NeonGreen
        )
        Text(
            text = item.showName,
            style = MaterialTheme.typography.bodyLarge,
            color = NeonGreen
        )
    }
}

@Composable
fun Oscilloscope(
    waveform: ByteArray?,
    isPlaying: Boolean,
    lissajousMode: Boolean,
 //   tension: Float,
//    minGain: Float,
//    maxGain: Float,
    modifier: Modifier = Modifier
) {
    val frameClock = remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { time ->
                frameClock.longValue = time
            }
        }
    }

    val bitmapRef = remember { mutableStateOf<Bitmap?>(null) }
    val canvasRef = remember { mutableStateOf<AndroidCanvas?>(null) }
    var loudnessEnv by remember { mutableFloatStateOf(0f) }

    val fadePaint = remember {
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            color = android.graphics.Color.argb(35, 0, 0, 0)
        }
    }

    val linePaint = remember {
        Paint().apply {
            color = NeonGreen.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = 5f
            isAntiAlias = true
            setShadowLayer(10f, 0f, 0f, NeonGreen.toArgb())
        }
    }

    val path = remember { Path() }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        frameClock.longValue // Trigger redraw on frame by reading the state

        val width = size.width.toInt()
        val height = size.height.toInt()

        if (bitmapRef.value == null || bitmapRef.value!!.width != width || bitmapRef.value!!.height != height) {
            val newBitmap = createBitmap(width, height)
            bitmapRef.value = newBitmap
            canvasRef.value = AndroidCanvas(newBitmap)
        }

        val bmp = bitmapRef.value!!
        val cvs = canvasRef.value!!

        cvs.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)

        if (isPlaying && waveform != null) {
            // -------------------------------------------
            // 1) Compute RMS of this audio frame
            // -------------------------------------------
            var sumSquares = 0f
            var maxAmplitude = 0

            for (b in waveform) {
                val v = (b.toInt() and 0xFF) - 128
                val absV = kotlin.math.abs(v)
                if (absV > maxAmplitude) maxAmplitude = absV
                sumSquares += v * v
            }

            val rms = if (waveform.isNotEmpty())
                kotlin.math.sqrt(sumSquares / waveform.size)
            else 0f

            // -------------------------------------------
            // 2) Smooth loudness envelope (volume follower)
            // -------------------------------------------
            val attack = 0.25f     // reacts fast to loud parts
            val release = 0.05f    // falls back slowly on quiet parts

            loudnessEnv += if (rms > loudnessEnv)
                (rms - loudnessEnv) * attack
            else
                (rms - loudnessEnv) * release

            // -------------------------------------------
            // 3) Convert loudness into explosion scale
            // -------------------------------------------
            // loudnessEnv is 0–128 range, normalize it
            // tweak divisor (32f) to control overall sensitivity
            val explosionScale = ((loudnessEnv / 64f))
                .coerceIn(0.3f, 4.5f)   // min size vs max explosion

            // 4) Dynamic Trail Decay
            val dynamicAlpha = when {
                maxAmplitude < 10 -> 18
                maxAmplitude < 30 -> 28
                maxAmplitude < 60 -> 40
                else -> 55
            }

            fadePaint.color = android.graphics.Color.argb(dynamicAlpha, 0, 0, 0)

            // 5) Geometry Setup
            val centerY = height / 2f
            val centerX = width / 2f
            val xScale = width * 0.42f
            val yScale = height * 0.42f

            path.reset()
            path.moveTo(0f, centerY)

            if (lissajousMode) {
                path.reset()

                if (maxAmplitude > 2 && waveform.size > 8) {
                    var firstPoint = true
                    val count = waveform.size
                    val phaseShift = waveform.size / 4

                    for (i in 0 until count) {
                        val a = ((waveform[i].toInt() and 0xFF) - 128) / 128f
                        val bIndex = (i + phaseShift) % count
                        val b = ((waveform[bIndex].toInt() and 0xFF) - 128) / 128f

                        val x = centerX + a * xScale * explosionScale
                        val y = centerY + b * yScale * explosionScale

                        if (firstPoint) {
                            path.moveTo(x, y)
                            firstPoint = false
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    path.close()
                } else {
                    path.addCircle(centerX, centerY, 10f, Path.Direction.CW)
                }

                // Only draw the path if we are in Lissajous mode
                cvs.drawPath(path, linePaint)
            }
        }
        drawImage(image = bmp.asImageBitmap())
    }
}