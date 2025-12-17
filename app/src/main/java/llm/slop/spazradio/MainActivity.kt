package llm.slop.spazradio

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import llm.slop.spazradio.ui.components.ArchiveContent
import llm.slop.spazradio.ui.components.FooterToolbar
import llm.slop.spazradio.ui.components.InfoBox
import llm.slop.spazradio.ui.components.Oscilloscope
import llm.slop.spazradio.ui.components.PlayerHeader
import llm.slop.spazradio.ui.components.ScheduleContent
import llm.slop.spazradio.ui.components.SettingsContent
import llm.slop.spazradio.ui.components.TrackTitle
import llm.slop.spazradio.ui.theme.DeepBlue
import llm.slop.spazradio.ui.theme.Magenta
import llm.slop.spazradio.ui.theme.SpazRadioTheme

/* ---------- Activity ---------- */

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
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
    scheduleViewModel: ScheduleViewModel = viewModel(),
    archiveViewModel: ArchiveViewModel = viewModel()
) {
    val headerTitle by radioViewModel.headerTitle.collectAsState()
    val trackSubtitle by radioViewModel.trackSubtitle.collectAsState()
    val isPlaying by radioViewModel.isPlaying.collectAsState()
    val isArchivePlaying by radioViewModel.isArchivePlaying.collectAsState()
    val playbackPosition by radioViewModel.playbackPosition.collectAsState()
    val playbackDuration by radioViewModel.playbackDuration.collectAsState()
    val lissajousMode by radioViewModel.lissajousMode.collectAsState()
    val currentInfoDisplay by radioViewModel.currentInfoDisplay.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val waveform by RadioService.waveformFlow.collectAsState()

    AdaptiveLayout(
        isLandscape = isLandscape,
        showOscilloscope = lissajousMode,
        showInfoBox = currentInfoDisplay != InfoDisplay.NONE,
        header = {
            PlayerHeader(
                title = headerTitle,
                isPlaying = isPlaying,
                isArchivePlaying = isArchivePlaying,
                playbackPosition = playbackPosition,
                playbackDuration = playbackDuration,
                onPlayPause = { radioViewModel.togglePlayPause() },
                onToggleSettings = { radioViewModel.toggleSettings() },
                onSeek = { radioViewModel.seekTo(it) }
            )
        },
        trackTitle = { TrackTitle(trackSubtitle) },
        oscilloscope = { modifier ->
            Oscilloscope(
                waveform = waveform,
                isPlaying = isPlaying,
                lissajousMode = true,
                modifier = modifier
            )
        },
        infoBox = { modifier ->
            val title = when (currentInfoDisplay) {
                InfoDisplay.SETTINGS -> "Settings"
                InfoDisplay.SCHEDULE -> "Schedule"
                InfoDisplay.ARCHIVES -> "Archives"
                else -> ""
            }
            InfoBox(
                title = title,
                onClose = { radioViewModel.closeInfoBox() },
                modifier = modifier
            ) {
                when (currentInfoDisplay) {
                    InfoDisplay.SETTINGS -> SettingsContent(radioViewModel)
                    InfoDisplay.SCHEDULE -> ScheduleContent(scheduleViewModel)
                    InfoDisplay.ARCHIVES -> {
                        ArchiveContent(
                            archiveViewModel = archiveViewModel,
                            radioViewModel = radioViewModel
                        )
                    }
                    else -> {}
                }
            }
        },
        footer = {
            FooterToolbar(
                onRadioClick = { radioViewModel.showLiveStream() },
                onArchivesClick = { radioViewModel.showArchives() },
                modifier = Modifier.padding(16.dp)
            )
        }
    )
}

@Composable
fun AdaptiveLayout(
    isLandscape: Boolean,
    showOscilloscope: Boolean,
    showInfoBox: Boolean,
    header: @Composable () -> Unit,
    trackTitle: @Composable () -> Unit,
    oscilloscope: @Composable (Modifier) -> Unit,
    infoBox: @Composable (Modifier) -> Unit,
    footer: @Composable () -> Unit
) {
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
                        header()
                        trackTitle()
                        // Ensure space is filled even if oscilloscope is hidden
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (showOscilloscope) {
                                oscilloscope(Modifier.fillMaxSize().padding(16.dp))
                            }
                        }
                        footer()
                    }
                    if (showInfoBox) {
                        infoBox(Modifier.weight(1f).fillMaxHeight().padding(16.dp))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    header()
                    trackTitle()
                    // Middle section
                    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        if (showInfoBox) {
                            if (showOscilloscope) {
                                oscilloscope(Modifier.fillMaxWidth().height(300.dp).padding(16.dp))
                            }
                            infoBox(Modifier.fillMaxWidth().weight(1f).padding(16.dp))
                        } else {
                            // If no info box, let oscilloscope take all space if it exists
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (showOscilloscope) {
                                    oscilloscope(Modifier.fillMaxSize().padding(16.dp))
                                }
                            }
                        }
                    }
                    footer()
                }
            }
        }
    }
}
