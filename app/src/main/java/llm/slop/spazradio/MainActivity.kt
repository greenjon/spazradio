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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import llm.slop.spazradio.ui.components.CinemaStrip
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
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import llm.slop.spazradio.data.ArchiveShow
import llm.slop.spazradio.ui.components.ArchiveContent
import llm.slop.spazradio.ui.components.CONTROLS_FADE_MS
import llm.slop.spazradio.ui.components.ChatContent
import llm.slop.spazradio.ui.components.ControlsAutoHide
import llm.slop.spazradio.ui.components.FooterToolbar
import llm.slop.spazradio.ui.components.HeaderDownload
import llm.slop.spazradio.ui.components.rememberShowDownloader
import llm.slop.spazradio.ui.components.InfoBox
import llm.slop.spazradio.ui.components.Oscilloscope
import lsd.liquid.visuals.LsdVisuals
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
            
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (appTheme) {
                AppTheme.DARK, AppTheme.NEON -> true
                AppTheme.LIGHT -> false
                AppTheme.AUTO -> systemDark
            }
            LaunchedEffect(isDarkTheme) {
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

    // The jukebox show on the stream, offered for download beside the title (never during a live show).
    val jukeboxFile by radioViewModel.jukeboxFile.collectAsState()
    val downloadingUrls by archiveViewModel.downloadingUrls.collectAsState()
    val downloadedFiles by archiveViewModel.downloadedFiles.collectAsState()
    // Looked up again when the show changes or the archive list grows; null hides the button.
    val jukeboxShow by produceState<ArchiveShow?>(null, jukeboxFile, archiveCount) {
        val file = jukeboxFile
        // A new show: drop the last one's button at once rather than while its file is being looked for.
        if (file == null || value?.let { findInArchive(file, listOf(it)) } == null) value = null
        if (file != null) value = archiveViewModel.jukeboxShow(file)
    }
    val headerDownload = jukeboxShow?.let { show ->
        when {
            show.url in downloadingUrls -> HeaderDownload.DOWNLOADING
            archiveViewModel.fileNameFor(show) in downloadedFiles -> HeaderDownload.DOWNLOADED
            else -> HeaderDownload.AVAILABLE
        }
    }
    val downloadShow = rememberShowDownloader(archiveViewModel, radioViewModel)
    // Collected once here (not per downloader) so a failure from the header or the archive list shows one toast.
    val toastContext = LocalContext.current
    LaunchedEffect(archiveViewModel) {
        archiveViewModel.downloadFailures.collect { title ->
            Toast.makeText(toastContext, toastContext.getString(R.string.download_failed, title), Toast.LENGTH_LONG).show()
        }
    }

    val screenLayout = rememberScreenLayout()
    val wide = screenLayout == ScreenLayout.WIDE

    // Wide screens keep the list on the left and chat or settings on the right, each opened on its own.
    var wideInfoOpen by rememberSaveable { mutableStateOf(false) }
    var wideSide by rememberSaveable { mutableStateOf(ActiveUtility.NONE) }
    // Settings borrows chat's slot; chat comes back when settings closes.
    var chatUnderSettings by rememberSaveable { mutableStateOf(false) }
    val toggleWide: (ActiveUtility) -> Unit = { utility ->
        when (utility) {
            ActiveUtility.INFO -> wideInfoOpen = !wideInfoOpen
            ActiveUtility.CHAT -> {
                chatUnderSettings = false
                wideSide = if (wideSide == ActiveUtility.CHAT) ActiveUtility.NONE else ActiveUtility.CHAT
            }
            ActiveUtility.SETTINGS -> if (wideSide == ActiveUtility.SETTINGS) {
                wideSide = if (chatUnderSettings) ActiveUtility.CHAT else ActiveUtility.NONE
                chatUnderSettings = false
            } else {
                chatUnderSettings = wideSide == ActiveUtility.CHAT
                wideSide = ActiveUtility.SETTINGS
            }
            ActiveUtility.NONE -> {}
        }
    }
    // On a wide screen the view model's one open panel is only a request (start-up, a mode change, a
    // rotation from portrait): it opens that side and is cleared again.
    LaunchedEffect(wide, activeUtility) {
        if (!wide || activeUtility == ActiveUtility.NONE) return@LaunchedEffect
        if (activeUtility == ActiveUtility.INFO) wideInfoOpen = true
        else { chatUnderSettings = false; wideSide = activeUtility }
        radioViewModel.closeInfoBox()
    }
    // Turning back to a single panel keeps the one most recently in use.
    var wasWide by remember { mutableStateOf(wide) }
    LaunchedEffect(wide) {
        if (wasWide && !wide) {
            val keep = when {
                wideSide != ActiveUtility.NONE -> wideSide
                wideInfoOpen -> ActiveUtility.INFO
                else -> ActiveUtility.NONE
            }
            if (keep != ActiveUtility.NONE) radioViewModel.setActiveUtility(keep)
        }
        wasWide = wide
    }
    val anyPanelOpen = activeUtility != ActiveUtility.NONE ||
        (wide && (wideInfoOpen || wideSide != ActiveUtility.NONE))

    // Only worth hiding when there is a picture to watch and nothing open to read.
    val controls = rememberControlsAutoHide(
        armed = autoHideControls && lissajousMode && isPlaying && !anyPanelOpen
    )

    val coroutineScope = rememberCoroutineScope()

    // BACKGROUND DATA LOADING
    LaunchedEffect(Unit) {
        delay(5000)
        archiveViewModel.fetchArchivesIfNeeded()
    }

    val pageCount = 300
    val pagerState = rememberPagerState(
        initialPage = 150 + activeUtility.pageOffset(),
        pageCount = { pageCount }
    )

    // Sync activeUtility (ViewModel) -> Pager
    LaunchedEffect(activeUtility) {
        if (activeUtility != ActiveUtility.NONE) {
            val targetModulo = activeUtility.pageOffset()
            if (pagerState.currentPage % 3 != targetModulo) {
                val baseIndex = (pagerState.currentPage / 3) * 3
                pagerState.scrollToPage(baseIndex + targetModulo)
            }
        }
    }

    val highlightedUtility by remember(pagerState, activeUtility) {
        derivedStateOf {
            if (activeUtility == ActiveUtility.NONE) ActiveUtility.NONE
            else utilityForPage(pagerState.currentPage)
        }
    }

    // Sync Pager -> activeUtility (ViewModel)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (activeUtility != ActiveUtility.NONE) {
                val settledUtility = utilityForPage(page)
                if (activeUtility != settledUtility) {
                    radioViewModel.setActiveUtility(settledUtility)
                }
            }
        }
    }

    // Back closes an open panel before it leaves the app; on a wide screen the right-hand one goes first.
    BackHandler(enabled = activeUtility != ActiveUtility.NONE) { radioViewModel.closeInfoBox() }
    BackHandler(enabled = wide && (wideSide != ActiveUtility.NONE || wideInfoOpen)) {
        if (wideSide != ActiveUtility.NONE) toggleWide(wideSide) else wideInfoOpen = false
    }

    val radioMode = appMode == AppMode.RADIO
    val panelTitle: @Composable (ActiveUtility) -> String = { utility ->
        stringResource(
            when (utility) {
                ActiveUtility.INFO -> if (radioMode) R.string.schedule_title else R.string.label_archives
                ActiveUtility.CHAT -> R.string.label_chat
                ActiveUtility.SETTINGS -> R.string.settings_title
                ActiveUtility.NONE -> R.string.app_name
            }
        )
    }
    val panelBody: @Composable (ActiveUtility) -> Unit = { utility ->
        when (utility) {
            ActiveUtility.INFO ->
                if (radioMode) ScheduleContent(scheduleViewModel)
                else ArchiveContent(archiveViewModel = archiveViewModel, radioViewModel = radioViewModel)
            ActiveUtility.CHAT -> ChatContent(chatViewModel)
            ActiveUtility.SETTINGS -> SettingsContent(radioViewModel, chatViewModel)
            ActiveUtility.NONE -> {}
        }
    }

    val onUtilityClick: (ActiveUtility) -> Unit = { utility ->
        if (wide) {
            toggleWide(utility)
        } else if (activeUtility == utility) {
            radioViewModel.setActiveUtility(ActiveUtility.NONE)
        } else {
            val wasClosed = activeUtility == ActiveUtility.NONE
            if (wasClosed) {
                radioViewModel.setActiveUtility(utility)
            }

            coroutineScope.launch {
                val baseIndex = (pagerState.currentPage / 3) * 3
                pagerState.scrollToPage(baseIndex + utility.pageOffset())
            }
        }
    }

    MainLayout(
        screenLayout = screenLayout,
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
                onModeChange = { radioViewModel.setAppMode(it) },
                download = headerDownload,
                // Taken when tapped, so the next title poll can't swap the show mid-download.
                onDownload = { jukeboxShow?.let(downloadShow) }
            )
        },
        cinemaStrip = {
            CinemaStrip(
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
                onModeChange = { radioViewModel.setAppMode(it) },
                download = headerDownload,
                onDownload = { jukeboxShow?.let(downloadShow) },
                activeUtility = highlightedUtility,
                visualsEnabled = lissajousMode,
                onUtilityClick = onUtilityClick,
                onToggleVisuals = { radioViewModel.setLissajousMode(!lissajousMode) }
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
                LsdVisuals(
                    settings = visuals,
                    active = isPlaying && !controls.pocketed, // nobody watches a pocketed screen
                    onFailed = { radioViewModel.webVisualsFailed.value = true },
                    modifier = modifier
                )
            }
        },
        infoBox = { modifier, compact ->
            if (activeUtility != ActiveUtility.NONE) {
                val titleUtility by remember(pagerState) {
                    derivedStateOf { utilityForPage(pagerState.currentPage) }
                }

                InfoBox(
                    title = panelTitle(titleUtility),
                    onClose = { radioViewModel.closeInfoBox() },
                    modifier = modifier,
                    compact = compact
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        flingBehavior = PagerDefaults.flingBehavior(state = pagerState)
                    ) { page ->
                        panelBody(utilityForPage(page))
                    }
                }
            }
        },
        widePanels = { leftModifier, rightModifier ->
            if (wideInfoOpen) {
                InfoBox(
                    title = panelTitle(ActiveUtility.INFO),
                    onClose = { wideInfoOpen = false },
                    modifier = leftModifier
                ) { panelBody(ActiveUtility.INFO) }
            }
            if (wideSide != ActiveUtility.NONE) {
                InfoBox(
                    title = panelTitle(wideSide),
                    onClose = { toggleWide(wideSide) },
                    modifier = rightModifier
                ) { panelBody(wideSide) }
            }
        },
        footer = {
            FooterToolbar(
                appMode = appMode,
                isSelected = { utility ->
                    if (wide) (utility == ActiveUtility.INFO && wideInfoOpen) || utility == wideSide
                    else utility == highlightedUtility
                },
                visualsEnabled = lissajousMode,
                onUtilityClick = onUtilityClick,
                onToggleVisuals = { radioViewModel.setLissajousMode(!lissajousMode) },
                modifier = if (wide) Modifier.widthIn(max = 600.dp) else Modifier,
                containerColor = if (wide) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)
            )
        }
    )
}

/** How the screen is laid out: the shape of the window decides, and rotating switches it on the fly. */
enum class ScreenLayout {
    /** Taller than wide: header on top, one panel at a time, toolbar along the bottom. */
    PORTRAIT,
    /** A phone on its side: the visuals fill the screen, one strip of controls, panels slide over on the right. */
    CINEMA,
    /** A tablet or unfolded phone on its side: the list and chat (or settings) open side by side. */
    WIDE
}

/** Wide needs Material's "expanded" width and more than a phone's height: a phone on its side is ~900 x 400 dp. */
private const val WIDE_MIN_WIDTH_DP = 840
private const val WIDE_MIN_HEIGHT_DP = 480

@Composable
private fun rememberScreenLayout(): ScreenLayout {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    return when {
        width <= height -> ScreenLayout.PORTRAIT
        width >= WIDE_MIN_WIDTH_DP && height >= WIDE_MIN_HEIGHT_DP -> ScreenLayout.WIDE
        else -> ScreenLayout.CINEMA
    }
}

/** The panel pager repeats Schedule/List, Chat, Settings, so it can be swiped round in either direction. */
private fun utilityForPage(page: Int): ActiveUtility = when (page % 3) {
    1 -> ActiveUtility.CHAT
    2 -> ActiveUtility.SETTINGS
    else -> ActiveUtility.INFO
}

private fun ActiveUtility.pageOffset(): Int = when (this) {
    ActiveUtility.CHAT -> 1
    ActiveUtility.SETTINGS -> 2
    else -> 0
}

@Composable
fun MainLayout(
    screenLayout: ScreenLayout,
    showOscilloscope: Boolean,
    showInfoBox: Boolean,
    appTheme: AppTheme,
    controls: ControlsAutoHide,
    header: @Composable () -> Unit,
    cinemaStrip: @Composable () -> Unit,
    oscilloscope: @Composable (Modifier) -> Unit,
    infoBox: @Composable (Modifier, compact: Boolean) -> Unit,
    widePanels: @Composable (left: Modifier, right: Modifier) -> Unit,
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

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(backgroundModifier)
                .restartsAutoHide(controls)
        ) {
            val screenWidth = maxWidth
            // The visuals stay at this one spot whatever the layout, so rotating doesn't restart them.
            if (showOscilloscope) {
                oscilloscope(Modifier.fillMaxSize())
            }

            when (screenLayout) {
                ScreenLayout.PORTRAIT -> {
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
                                        .padding(16.dp),
                                    false
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
                }

                ScreenLayout.CINEMA -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .then(if (isKeyboardOpen) Modifier else Modifier.statusBarsPadding())
                                // A side navigation bar or a camera cutout can sit on either long edge.
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        ) {
                            if (showInfoBox) {
                                infoBox(
                                    Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                        .width((screenWidth * 0.45f).coerceIn(320.dp, 460.dp))
                                        .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                                    // A landscape keyboard leaves ~150 dp: the panel gives it all to the chat.
                                    isKeyboardOpen
                                )
                            }
                        }
                        // Typing in chat needs every bit of height, so the strip steps aside for the keyboard.
                        if (!isKeyboardOpen) {
                            Box(Modifier.graphicsLayer { alpha = controlsAlpha }) {
                                cinemaStrip()
                            }
                        }
                    }
                }

                ScreenLayout.WIDE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                    ) {
                        Box(Modifier.graphicsLayer { alpha = controlsAlpha }) {
                            header()
                        }
                        val panelWidth = (screenWidth * 0.32f).coerceIn(320.dp, 440.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                .padding(16.dp)
                        ) {
                            widePanels(
                                Modifier.align(Alignment.CenterStart).fillMaxHeight().width(panelWidth),
                                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(panelWidth)
                            )
                        }
                        if (!isKeyboardOpen) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.25f))
                                    .graphicsLayer { alpha = controlsAlpha },
                                contentAlignment = Alignment.Center
                            ) {
                                footer()
                            }
                        }
                    }
                }
            }

            RevealControlsLayer(controls)
        }
    }
}
