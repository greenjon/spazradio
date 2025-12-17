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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import llm.slop.spazradio.ui.components.FooterToolbar
import llm.slop.spazradio.ui.components.InfoBox
import llm.slop.spazradio.ui.components.Oscilloscope
import llm.slop.spazradio.ui.components.PlayerHeader
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

enum class InfoDisplay {
    NONE, SETTINGS, SCHEDULE
}

/* ---------- App Root (RadioApp) ---------- */
@Composable
fun RadioApp(
    radioViewModel: RadioViewModel = viewModel(),
    scheduleViewModel: ScheduleViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("spaz_radio_prefs", Context.MODE_PRIVATE) }

    val headerTitle by radioViewModel.headerTitle.collectAsState()
    val trackSubtitle by radioViewModel.trackSubtitle.collectAsState()
    val isPlaying by radioViewModel.isPlaying.collectAsState()

    var infoDisplay by rememberSaveable { mutableStateOf(InfoDisplay.NONE) }
    
    val showSchedulePref = remember { mutableStateOf(prefs.getBoolean("show_schedule", true)) }
    val lissajousMode = remember { mutableStateOf(prefs.getBoolean("visuals_enabled", true)) }

    LaunchedEffect(showSchedulePref.value) { prefs.edit { putBoolean("show_schedule", showSchedulePref.value) } }
    LaunchedEffect(lissajousMode.value) { prefs.edit { putBoolean("visuals_enabled", lissajousMode.value) } }

    // If schedule is preferred and we aren't in settings, show schedule
    val currentInfoDisplay = when {
        infoDisplay == InfoDisplay.SETTINGS -> InfoDisplay.SETTINGS
        showSchedulePref.value -> InfoDisplay.SCHEDULE
        else -> InfoDisplay.NONE
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val waveform by RadioService.waveformFlow.collectAsState()
    val showInfoBox = currentInfoDisplay != InfoDisplay.NONE

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
                            title = headerTitle,
                            isPlaying = isPlaying,
                            onPlayPause = { radioViewModel.togglePlayPause() },
                            onToggleSettings = { 
                                infoDisplay = if (infoDisplay == InfoDisplay.SETTINGS) InfoDisplay.NONE else InfoDisplay.SETTINGS 
                            }
                        )
                        TrackTitle(trackSubtitle)
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
                            onRadioClick = { /* No-op */ },
                            onArchivesClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://spaz.org/radio/archive"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    if (showInfoBox) {
                        InfoBox(
                            showSettings = (currentInfoDisplay == InfoDisplay.SETTINGS),
                            onCloseSettings = { infoDisplay = InfoDisplay.NONE },
                            lissajousMode = lissajousMode,
                            showSchedule = showSchedulePref,
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
                        title = headerTitle,
                        isPlaying = isPlaying,
                        onPlayPause = { radioViewModel.togglePlayPause() },
                        onToggleSettings = { 
                            infoDisplay = if (infoDisplay == InfoDisplay.SETTINGS) InfoDisplay.NONE else InfoDisplay.SETTINGS 
                        }
                    )
                    TrackTitle(trackSubtitle)
                    
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
                                showSettings = (currentInfoDisplay == InfoDisplay.SETTINGS),
                                onCloseSettings = { infoDisplay = InfoDisplay.NONE },
                                lissajousMode = lissajousMode,
                                showSchedule = showSchedulePref,
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
