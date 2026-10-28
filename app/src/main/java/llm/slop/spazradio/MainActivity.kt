package llm.slop.spazradio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import llm.slop.spazradio.ui.components.ArchiveContent
import llm.slop.spazradio.ui.components.CONTROLS_FADE_MS
import llm.slop.spazradio.ui.components.ChatContent
import llm.slop.spazradio.ui.components.ControlsAutoHide
import llm.slop.spazradio.ui.components.FooterToolbar
import llm.slop.spazradio.ui.components.InfoBox
import llm.slop.spazradio.ui.components.Oscilloscope
import llm.slop.spazradio.ui.components.VisualizerView
import llm.slop.spazradio.ui.components.rememberControlsAutoHide
import llm.slop.spazradio.ui.components.restartsAutoHide
import llm.slop.spazradio.ui.components.PlayerHeader
import llm.slop.spazradio.ui.components.RevealControlsLayer
import llm.slop.spazradio.ui.components.ScheduleContent
import llm.slop.spazradio.ui.components.SettingsContent
import llm.slop.spazradio.ui.theme.DeepBlue
import llm.slop.spazradio.ui.theme.NeonMagenta
import llm.slop.spazradio.ui.theme.SpazRadioTheme

/* ---------- Activity ---------- */

// Changed from ComponentActivity to AppCompatActivity to support AppCompatDelegate locale changes
class MainActivity : AppCompatActivity() {

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        requestNotificationPermission()

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
    val visuals by radioViewModel.visuals.collectAsState()
    // The web visuals couldn't start on this device (no WebView or WebGL2): the oscilloscope takes over.
    val webVisualsFailed by radioViewModel.webVisualsFailed.collectAsState()
    val autoHideControls by radioViewModel.autoHideControls.collectAsState()

    // Only worth hiding when there is a picture to watch and nothing open to read.
    val controls = rememberControlsAutoHide(
        armed = autoHideControls && lissajousMode && isPlaying && activeUtility == ActiveUtility.NONE
    )

    val coroutineScope = rememberCoroutineScope()

    // BACKGROUND DATA LOADING
    LaunchedEffect(Unit) {
        delay(5000)
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

    // Sync activeUtility (ViewModel) -> Pager
    LaunchedEffect(activeUtility) {
        if (activeUtility != ActiveUtility.NONE) {
            val targetModulo = when (activeUtility) {
                ActiveUtility.INFO -> 0
                ActiveUtility.CHAT -> 1
                ActiveUtility.SETTINGS -> 2
                else -> 0
            }
            if (pagerState.currentPage % 3 != targetModulo) {
                val baseIndex = (pagerState.currentPage / 3) * 3
                pagerState.scrollToPage(baseIndex + targetModulo)
            }
        }
    }

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

    // Sync Pager -> activeUtility (ViewModel)
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
        controls = controls,
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
            if (webVisualsFailed) {
                val waveform by RadioService.waveformFlow.collectAsState()
                Oscilloscope(
                    waveform = waveform,
                    isPlaying = isPlaying,
                    lissajousMode = true,
                    modifier = modifier
                )
            } else {
                VisualizerView(
                    settings = visuals,
                    active = isPlaying,
                    onFailed = { radioViewModel.webVisualsFailed.value = true },
                    modifier = modifier
                )
            }
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
    controls: ControlsAutoHide,
    header: @Composable () -> Unit,
    oscilloscope: @Composable (Modifier) -> Unit,
    infoBox: @Composable (Modifier) -> Unit,
    footer: @Composable () -> Unit
) {
    val isKeyboardOpen = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    // Fade, don't remove: the layout stays put and the reveal layer takes the touches while they're hidden.
    val controlsAlpha by animateFloatAsState(
        targetValue = if (controls.visible) 1f else 0f,
        animationSpec = tween(CONTROLS_FADE_MS),
        label = "controlsAlpha"
    )

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
                .restartsAutoHide(controls)
        ) {
            if (showOscilloscope) {
                oscilloscope(Modifier.fillMaxSize())
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding() 
            ) {
                Box(Modifier.graphicsLayer { alpha = controlsAlpha }) {
                    header()
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (showInfoBox) {
                        infoBox(
                            Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                }
                
                if (!isKeyboardOpen) {
                    Spacer(modifier = Modifier.navigationBarsPadding().height(80.dp))
                }
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = controlsAlpha }
            ) {
                footer()
            }

            RevealControlsLayer(controls)
        }
    }
}
