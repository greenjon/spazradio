package llm.slop.spazradio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import llm.slop.spazradio.ui.theme.DeepBlue
import llm.slop.spazradio.ui.theme.NeonMagenta
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
            val radioViewModel: RadioViewModel = viewModel()
            val appTheme by radioViewModel.appTheme.collectAsState()
            
            SpazRadioTheme(appTheme = appTheme) {
                RadioApp(radioViewModel = radioViewModel)
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
    val trackSubtitle by radioViewModel.trackSubtitle.collectAsState()
    val isPlaying by radioViewModel.isPlaying.collectAsState()
    val isArchivePlaying by radioViewModel.isArchivePlaying.collectAsState()
    val playbackPosition by radioViewModel.playbackPosition.collectAsState()
    val playbackDuration by radioViewModel.playbackDuration.collectAsState()
    val lissajousMode by radioViewModel.lissajousMode.collectAsState()
    val appMode by radioViewModel.appMode.collectAsState()
    val activeUtility by radioViewModel.activeUtility.collectAsState()
    val listenerCount by radioViewModel.trackListeners.collectAsState()
    val archiveCount by archiveViewModel.cachedArchiveCount.collectAsState()
    val appTheme by radioViewModel.appTheme.collectAsState()

    // 100 * 3 pages to simulate infinite loop. Start at index 150 (INFO)
    val pageCount = 300
    val pagerState = rememberPagerState(
        initialPage = 150 + when (activeUtility) {
            ActiveUtility.CHAT -> 1
            ActiveUtility.SETTINGS -> 2
            else -> 0
        },
        pageCount = { pageCount }
    )

    // Sync activeUtility -> pagerState (Only trigger when NOT swiping)
    LaunchedEffect(activeUtility) {
        if (activeUtility == ActiveUtility.NONE || pagerState.isScrollInProgress) return@LaunchedEffect
        
        val baseIndex = (pagerState.currentPage / 3) * 3
        val targetPage = baseIndex + when (activeUtility) {
            ActiveUtility.INFO -> 0
            ActiveUtility.CHAT -> 1
            ActiveUtility.SETTINGS -> 2
            else -> 0
        }
        
        if (pagerState.currentPage != targetPage) {
            // Snap to target page instead of animating to avoid race conditions with swiping
            pagerState.animateScrollToPage(targetPage, animationSpec = tween(durationMillis = 150))
        }
    }

    // Sync pagerState -> activeUtility (Only trigger when user IS swiping)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (activeUtility == ActiveUtility.NONE || !pagerState.isScrollInProgress) return@collect
            
            val normalizedPage = page % 3
            val targetUtility = when (normalizedPage) {
                0 -> ActiveUtility.INFO
                1 -> ActiveUtility.CHAT
                2 -> ActiveUtility.SETTINGS
                else -> ActiveUtility.INFO
            }
            if (activeUtility != targetUtility) {
                radioViewModel.setActiveUtility(targetUtility)
            }
        }
    }

    MainLayout(
        showOscilloscope = lissajousMode,
        showInfoBox = activeUtility != ActiveUtility.NONE,
        appTheme = appTheme,
        header = {
            PlayerHeader(
                trackStatus = trackSubtitle,
                listenerCount = listenerCount,
                archiveCount = archiveCount,
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
        oscilloscope = { modifier ->
            val waveform by RadioService.waveformFlow.collectAsState()
            Oscilloscope(
                waveform = waveform,
                isPlaying = isPlaying,
                lissajousMode = true,
                modifier = modifier
            )
        },
        infoBox = { modifier ->
            if (activeUtility != ActiveUtility.NONE) {
                val title by remember(pagerState, appMode) {
                    derivedStateOf {
                        when (pagerState.currentPage % 3) {
                            0 -> if (appMode == AppMode.RADIO) "Schedule" else "Archives"
                            1 -> "Chat"
                            2 -> "Settings"
                            else -> ""
                        }
                    }
                }

                InfoBox(
                    title = title,
                    onClose = { radioViewModel.closeInfoBox() },
                    modifier = modifier
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        flingBehavior = PagerDefaults.flingBehavior(state = pagerState)
                    ) { page ->
                        when (page % 3) {
                            0 -> {
                                if (appMode == AppMode.RADIO) {
                                    ScheduleContent(scheduleViewModel)
                                } else {
                                    ArchiveContent(
                                        archiveViewModel = archiveViewModel,
                                        radioViewModel = radioViewModel
                                    )
                                }
                            }
                            1 -> ChatContent(chatViewModel)
                            2 -> SettingsContent(radioViewModel, chatViewModel)
                        }
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
    appTheme: AppTheme,
    header: @Composable () -> Unit,
    oscilloscope: @Composable (Modifier) -> Unit,
    infoBox: @Composable (Modifier) -> Unit,
    footer: @Composable () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = footer
    ) { innerPadding ->
        val backgroundModifier = if (appTheme == AppTheme.NEON) {
            Modifier.background(Brush.verticalGradient(listOf(DeepBlue, NeonMagenta, DeepBlue)))
        } else {
            Modifier.background(MaterialTheme.colorScheme.background)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(backgroundModifier)
        ) {
            if (showOscilloscope) {
                oscilloscope(Modifier.fillMaxSize())
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    header()
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
