package llm.slop.spazradio

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import llm.slop.spazradio.ui.components.FooterToolbar
import llm.slop.spazradio.ui.components.InfoBox
import llm.slop.spazradio.ui.components.Oscilloscope
import llm.slop.spazradio.ui.theme.DeepBlue
import llm.slop.spazradio.ui.theme.Magenta
import llm.slop.spazradio.ui.theme.NeonGreen
import llm.slop.spazradio.ui.theme.SpazRadioTheme

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

/* ---------- App Root (RadioApp) ---------- */
@Composable
fun RadioApp(
    radioViewModel: RadioViewModel = viewModel(),
    scheduleViewModel: ScheduleViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("spaz_radio_prefs", Context.MODE_PRIVATE) }

    val playbackUiState by radioViewModel.playbackUiState.collectAsState()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    val showSchedule = remember { mutableStateOf(prefs.getBoolean("show_schedule", true)) }
    val lissajousMode = remember { mutableStateOf(prefs.getBoolean("visuals_enabled", true)) }

    LaunchedEffect(showSchedule.value) { prefs.edit { putBoolean("show_schedule", showSchedule.value) } }
    LaunchedEffect(lissajousMode.value) { prefs.edit { putBoolean("visuals_enabled", lissajousMode.value) } }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val waveform by RadioService.waveformFlow.collectAsState()
    val isPlaying = playbackUiState is PlaybackUiState.Playing
    val showInfoBox = showSettings || showSchedule.value

    // Toggle Settings lambda
    val onToggleSettings: () -> Unit = {
        showSettings = !showSettings
    }

    // Displayed track line
    val displayedSecondLine = when (playbackUiState) {
        PlaybackUiState.Connecting -> "Connecting…"
        PlaybackUiState.Buffering -> "Buffering…"
        PlaybackUiState.Reconnecting -> "Reconnecting…"
        is PlaybackUiState.Playing, is PlaybackUiState.Paused -> playbackUiState.trackTitle()
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DeepBlue, Magenta, DeepBlue)))
                .padding(innerPadding)
        ) {
            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(1f)) {
                        PlayerHeader(
                            playbackUiState = playbackUiState,
                            onPlayPause = { radioViewModel.togglePlayPause() },
                            onToggleSettings = onToggleSettings
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
                        FooterToolbar(
                            onRadioClick = { /* No-op, we're on Radio */ },
                            onArchivesClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://spaz.org/radio/archive"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.padding(16.dp)
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
                                .fillMaxHeight()
                                .padding(16.dp)
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    PlayerHeader(
                        playbackUiState = playbackUiState,
                        onPlayPause = { radioViewModel.togglePlayPause() },
                        onToggleSettings = onToggleSettings
                    )
                    TrackTitle(displayedSecondLine)
                    
                    // Main content area that takes up available space
                    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        if (lissajousMode.value) {
                            Oscilloscope(
                                waveform = waveform,
                                isPlaying = isPlaying,
                                lissajousMode = lissajousMode.value,
                                modifier = if (showInfoBox) {
                                    Modifier.fillMaxWidth().height(300.dp).padding(16.dp)
                                } else {
                                    Modifier.fillMaxWidth().weight(1f).padding(16.dp)
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
                                modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp)
                            )
                        }
                    }

                    FooterToolbar(
                        onRadioClick = { /* No-op */ },
                        onArchivesClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://spaz.org/radio/archive"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerHeader(
    playbackUiState: PlaybackUiState,
    onPlayPause: () -> Unit,
    onToggleSettings: () -> Unit
) {
    val isPlaying = playbackUiState is PlaybackUiState.Playing
    val headerText = if (playbackUiState.trackListeners().isNotBlank())
        "SPAZ.Radio   -   ${playbackUiState.trackListeners()}"
    else "SPAZ.Radio"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPlayPause) {
            Icon(
                painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = NeonGreen,
                modifier = Modifier.size(48.dp)
            )
        }
        Text(
            text = headerText,
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFFFFFF00),
            textAlign = TextAlign.Center
        )
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
