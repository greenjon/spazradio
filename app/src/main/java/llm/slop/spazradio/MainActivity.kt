package llm.slop.spazradio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import llm.slop.spazradio.ui.components.ArchiveContent
import llm.slop.spazradio.ui.components.ChatContent
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
    archiveViewModel: ArchiveViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel()
) {
    val headerTitle by radioViewModel.headerTitle.collectAsState()
    val trackSubtitle by radioViewModel.trackSubtitle.collectAsState()
    val isPlaying by radioViewModel.isPlaying.collectAsState()
    val isArchivePlaying by radioViewModel.isArchivePlaying.collectAsState()
    val playbackPosition by radioViewModel.playbackPosition.collectAsState()
    val playbackDuration by radioViewModel.playbackDuration.collectAsState()
    val lissajousMode by radioViewModel.lissajousMode.collectAsState()
    val appMode by radioViewModel.appMode.collectAsState()
    val activeUtility by radioViewModel.activeUtility.collectAsState()

    MainLayout(
        showOscilloscope = lissajousMode,
        showInfoBox = activeUtility != ActiveUtility.NONE,
        header = {
            PlayerHeader(
                title = headerTitle,
                isPlaying = isPlaying,
                isArchivePlaying = isArchivePlaying,
                playbackPosition = playbackPosition,
                playbackDuration = playbackDuration,
                appMode = appMode,
                onPlayPause = { radioViewModel.togglePlayPause() },
                onSeek = { radioViewModel.seekTo(it) },
                onModeChange = { radioViewModel.setAppMode(it) }
            )
        },
        trackTitle = { TrackTitle(trackSubtitle) },
        oscilloscope = { modifier ->
            // Collect waveform ONLY here to avoid recomposing the whole RadioApp
            val waveform by RadioService.waveformFlow.collectAsState()
            Oscilloscope(
                waveform = waveform,
                isPlaying = isPlaying,
                lissajousMode = true,
                modifier = modifier
            )
        },
        infoBox = { modifier ->
            val title = when (activeUtility) {
                ActiveUtility.SETTINGS -> "Settings"
                ActiveUtility.INFO -> if (appMode == AppMode.RADIO) "Schedule" else "Archives"
                ActiveUtility.CHAT -> "Chat"
                else -> ""
            }
            if (activeUtility != ActiveUtility.NONE) {
                InfoBox(
                    title = title,
                    onClose = { radioViewModel.closeInfoBox() },
                    modifier = modifier
                ) {
                    when (activeUtility) {
                        ActiveUtility.SETTINGS -> SettingsContent(radioViewModel, chatViewModel)
                        ActiveUtility.INFO -> {
                            if (appMode == AppMode.RADIO) {
                                ScheduleContent(scheduleViewModel)
                            } else {
                                ArchiveContent(
                                    archiveViewModel = archiveViewModel,
                                    radioViewModel = radioViewModel
                                )
                            }
                        }
                        ActiveUtility.CHAT -> ChatContent(chatViewModel)
                        else -> {}
                    }
                }
            }
        },
        footer = {
            FooterToolbar(
                appMode = appMode,
                activeUtility = activeUtility,
                visualsEnabled = lissajousMode,
                onUtilityClick = { radioViewModel.setActiveUtility(it) },
                onToggleVisuals = { radioViewModel.setLissajousMode(!lissajousMode) }
            )
        }
    )
}

@Composable
fun MainLayout(
    showOscilloscope: Boolean,
    showInfoBox: Boolean,
    header: @Composable () -> Unit,
    trackTitle: @Composable () -> Unit,
    oscilloscope: @Composable (Modifier) -> Unit,
    infoBox: @Composable (Modifier) -> Unit,
    footer: @Composable () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = footer
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DeepBlue, Magenta, DeepBlue)))
        ) {
            // Edge-to-edge Visuals Layer
            if (showOscilloscope) {
                oscilloscope(Modifier.fillMaxSize())
            }

            // Foreground Content Layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    header()
                    trackTitle()
                    // Middle section: HUD style overlay area
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        if (showInfoBox) {
                            infoBox(
                                Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
