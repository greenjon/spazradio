package llm.slop.spazradio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

        enableEdgeToEdge()

        setContent {
            val radioViewModel: RadioViewModel = viewModel()
            val appTheme by radioViewModel.appTheme.collectAsState()
            
            val isDarkTheme = appTheme == AppTheme.DARK || appTheme == AppTheme.NEON
            LaunchedEffect(appTheme) {
                if (isDarkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    )
                }
            }

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

    val coroutineScope = rememberCoroutineScope()

    // BACKGROUND DATA LOADING
    // Pre-populate archives after a delay to avoid interfering with initial stream buffering
    LaunchedEffect(Unit) {
        delay(5000) // Wait 5 seconds after launch
        archiveViewModel.fetchArchivesIfNeeded()
    }

    val pageCount = 300
    val pagerState = rememberPagerState(
        initialPage = 150 + when (activeUtility) {
            ActiveUtility.CHAT -> 1
            ActiveUtility.SETTINGS -> 2
            else -> 0
        },
        pageCount = { pageCount }
    )

    val highlightedUtility by remember(pagerState, activeUtility) {
        derivedStateOf {
            if (activeUtility == ActiveUtility.NONE) ActiveUtility.NONE
            else when (pagerState.currentPage % 3) {
                0 -> ActiveUtility.INFO
                1 -> ActiveUtility.CHAT
                2 -> ActiveUtility.SETTINGS
                else -> ActiveUtility.INFO
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (activeUtility != ActiveUtility.NONE) {
                val settledUtility = when (page % 3) {
                    0 -> ActiveUtility.INFO
                    1 -> ActiveUtility.CHAT
                    2 -> ActiveUtility.SETTINGS
                    else -> ActiveUtility.INFO
                }
                if (activeUtility != settledUtility) {
                    radioViewModel.setActiveUtility(settledUtility)
                }
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
                activeUtility = highlightedUtility,
                visualsEnabled = lissajousMode,
                onUtilityClick = { utility ->
                    if (activeUtility == utility) {
                        radioViewModel.setActiveUtility(ActiveUtility.NONE)
                    } else {
                        val wasClosed = activeUtility == ActiveUtility.NONE
                        if (wasClosed) {
                            radioViewModel.setActiveUtility(utility)
                        }
                        
                        coroutineScope.launch {
                            val baseIndex = (pagerState.currentPage / 3) * 3
                            val targetPage = baseIndex + when (utility) {
                                ActiveUtility.INFO -> 0
                                ActiveUtility.CHAT -> 1
                                ActiveUtility.SETTINGS -> 2
                                else -> 0
                            }
                            pagerState.scrollToPage(targetPage)
                        }
                    }
                },
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            ) {
                header()
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .imePadding()
                ) {
                    if (showInfoBox) {
                        infoBox(
                            Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.navigationBarsPadding().height(80.dp))
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                footer()
            }
        }
    }
}
