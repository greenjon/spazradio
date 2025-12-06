package com.greenjon.spazradiotest

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.greenjon.spazradiotest.ui.theme.SpazradiotestTheme
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

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
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
fun Oscilloscope(waveform: ByteArray?) {
    if (waveform == null) return

    Canvas(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        val path = Path()
        path.moveTo(0f, centerY)

        val step = width / waveform.size
        for (i in waveform.indices) {
            val v = (waveform[i].toInt() and 0xFF) - 128
            
            val y = centerY + (v * (height / 2) / 128)
            val x = i * step
            path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = NeonGreen,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
