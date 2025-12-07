package com.greenjon.spazradiotest

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.greenjon.spazradiotest.ui.theme.SpazradiotestTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
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
    var isVisEnabled by remember { mutableStateOf(false) }

    var trackTitle by remember { mutableStateOf("Connecting...") }
    var trackListeners by remember { mutableStateOf("") }

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
                        colors = listOf(Magenta, DeepBlue)
                    )
                )
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                // Header: Play/Pause - Listeners - VIS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Top Left Play/Pause Button
                    Button(
                        onClick = {
                            if (isPlaying) {
                                mediaController?.pause()
                            } else {
                                mediaController?.play()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        modifier = Modifier.border(1.dp, NeonGreen, RoundedCornerShape(4.dp))
                    ) {
                        Text(if (isPlaying) "Pause" else "Play", color = NeonGreen)
                    }

                    // Center: Listeners count
                    Text(
                        text = trackListeners, // Contains "N listening"
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonGreen
                    )

                    // Top Right VIS Button
                    Button(
                        onClick = { isVisEnabled = !isVisEnabled },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        modifier = Modifier.border(1.dp, NeonGreen, RoundedCornerShape(4.dp))
                    ) {
                        Text(
                            text = if (isVisEnabled) "VIS: ON" else "VIS: OFF",
                            color = NeonGreen
                        )
                    }
                }

                // Track Title Section (Moved below buttons)
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

                // Visualizer (Conditional)
                if (isVisEnabled) {
                    val waveform by RadioService.waveformFlow.collectAsState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Oscilloscope(waveform = waveform)
                    }
                }

                // Schedule Section (Bottom)
                val schedule by scheduleViewModel.schedule.collectAsState()
                val loading by scheduleViewModel.loading.collectAsState()
                val error by scheduleViewModel.error.collectAsState()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(Color(0x80000000), RoundedCornerShape(16.dp)) // 50% transparent black
                        .border(3.dp, NeonGreen, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = NeonGreen
                        )
                    } else if (error != null) {
                        Text(
                            text = "Error: $error",
                            color = MaterialTheme.colorScheme.error, // Keeping error color red/standard for visibility or could be NeonGreen too
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)) {
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
    }
}

@Composable
fun ScheduleItemRow(item: ScheduleItem) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)) {
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
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(300.dp)
) {
    // 1. Setup the Frame Clock
    val frameClock = remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            // This is the function that was missing
            withFrameNanos { time ->
                frameClock.value = time
            }
        }
    }

    if (waveform == null) return

    val bitmapRef = remember { mutableStateOf<Bitmap?>(null) }
    // Note the explicit type here to avoid confusion
    val canvasRef = remember { mutableStateOf<AndroidCanvas?>(null) }
    val smoothedGain = remember { mutableStateOf(1f) }


    // Reuse Paint objects
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
        val tick = frameClock.value // Triggers redraw

        val width = size.width.toInt()
        val height = size.height.toInt()

        if (bitmapRef.value == null || bitmapRef.value!!.width != width || bitmapRef.value!!.height != height) {
            val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmapRef.value = newBitmap
            canvasRef.value = AndroidCanvas(newBitmap)
        }

        val bmp = bitmapRef.value!!
        val cvs = canvasRef.value!!

        cvs.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)

        // -----------------------------
// 1) RMS + Peak Analysis
// -----------------------------
        var maxAmplitude = 0
        var sumSquares = 0f

        for (b in waveform) {
            val v = (b.toInt() and 0xFF) - 128
            val absV = kotlin.math.abs(v)
            if (absV > maxAmplitude) maxAmplitude = absV
            sumSquares += v * v
        }

        val rms = if (waveform.isNotEmpty()) {
            kotlin.math.sqrt(sumSquares / waveform.size)
        } else {
            0f
        }
// -----------------------------
// 2) Dynamic Trail Decay
// -----------------------------
        val dynamicAlpha = when {
            maxAmplitude < 10 -> 18   // long persistence in silence
            maxAmplitude < 30 -> 28
            maxAmplitude < 60 -> 40
            else -> 55   // fast decay when loud
        }

        fadePaint.color = android.graphics.Color.argb(dynamicAlpha, 0, 0, 0)

// -----------------------------
// 3) RMS Auto-Gain (Soft Compressed + Smoothed)
// -----------------------------
        val minGain = 0.5f // Lowered floor
        val maxGain = 1.8f // Lowered ceiling slightly

        val normalizedRms = (rms / 64f).coerceIn(0.08f, 1.2f)
        val targetGain = (1f / normalizedRms).coerceIn(minGain, maxGain)

        smoothedGain.value += (targetGain - smoothedGain.value) * 0.12f
        val autoGain = smoothedGain.value

// -----------------------------
// 4) Geometry Setup
// -----------------------------
        val centerY = height / 2f
        val centerX = height / 2f

        // This is the "ideal" height if the wave wasn't huge.
        // We multiply by autoGain to boost quiet parts.
        val baseScale = (height * 0.45f) * autoGain

        path.reset()
        path.moveTo(0f, centerY)

// -----------------------------
// 5) Bézier Tension Control
// -----------------------------
        val tension = 0.55f   // 🎛 Adjust freely: 0.3 = liquid, 0.8 = sharp

// -----------------------------
// 6) Draw Logic — Lissajous Mode (Mono → Fake Stereo)
// -----------------------------
        if (maxAmplitude > 2 && waveform.size > 8) {

            val count = waveform.size
            val phaseOffset = count / 4   // 90° shift
            val radius = baseScale        // reuse your auto-gain scale

            path.reset()

            // --- First point ---
            val v0 = waveform[0].toInt() and 0xFF
            val v0n = (v0 / 128f) - 1f
            val v0p = waveform[phaseOffset % count].toInt() and 0xFF
            val v0pn = (v0p / 128f) - 1f

            val x0 = centerX + v0n * radius
            val y0 = centerY + v0pn * radius
            path.moveTo(x0, y0)

            // --- Vector Trace ---
            for (i in 1 until count - phaseOffset) {

                val a = waveform[i].toInt() and 0xFF
                val b = waveform[(i + phaseOffset) % count].toInt() and 0xFF

                val an = (a / 128f) - 1f
                val bn = (b / 128f) - 1f

                val rawX = centerX + an * radius
                val rawY = centerY + bn * radius

                // Soft limiter still applies
                val limit = height / 2f - 12f

                val lx = centerX + limit * kotlin.math.tanh((rawX - centerX) / limit)
                val ly = centerY + limit * kotlin.math.tanh((rawY - centerY) / limit)

                path.lineTo(lx.toFloat(), ly.toFloat())
            }

        } else {
            // Silence fallback
            path.reset()
            path.addCircle(centerX, centerY, 10f, Path.Direction.CW)
        }
        cvs.drawPath(path, linePaint)
        drawImage(image = bmp.asImageBitmap())
    }
}
