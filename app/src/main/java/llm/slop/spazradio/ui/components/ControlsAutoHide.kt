package llm.slop.spazradio.ui.components

import android.app.Activity
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

private const val HIDE_AFTER_MS = 4000L
/** Matches the controls' fade, so the header doesn't jump while the status bar slides away. */
const val CONTROLS_FADE_MS = 300

/** Whether the radio's controls are showing over the visuals, and the touches that keep them there. */
@Stable
class ControlsAutoHide {
    var visible by mutableStateOf(true)
        internal set
    internal var touching by mutableStateOf(false)
    internal var lastRelease by mutableIntStateOf(0)

    fun reveal() {
        visible = true
    }
}

/**
 * Fades the controls out after a few seconds without touches while [armed] (visuals on, music playing, no
 * panel open). While they are hidden the system bars hide too, the screen stays awake and Back brings the
 * controls back. Never arms under TalkBack: controls that vanish under a screen reader can't be found.
 */
@Composable
fun rememberControlsAutoHide(armed: Boolean): ControlsAutoHide {
    val state = remember { ControlsAutoHide() }
    val touchExploration = rememberTouchExploration()
    val active = armed && !touchExploration

    LaunchedEffect(active, state.visible, state.touching, state.lastRelease) {
        if (!active) {
            state.visible = true
        } else if (state.visible && !state.touching) {
            delay(HIDE_AFTER_MS)
            state.visible = false
        }
    }

    val view = LocalView.current
    val hidden = !state.visible
    LaunchedEffect(hidden) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) {
            delay(CONTROLS_FADE_MS.toLong())
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = hidden) { state.reveal() }

    return state
}

/** Watches every touch on the screen (without taking it) so the hide countdown restarts after each one. */
fun Modifier.restartsAutoHide(state: ControlsAutoHide): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.any { it.pressed }
            if (down != state.touching) {
                state.touching = down
                if (!down) state.lastRelease++
            }
        }
    }
}

/** Covers the screen while the controls are hidden: the first touch only brings them back. */
@Composable
fun RevealControlsLayer(state: ControlsAutoHide) {
    if (state.visible) return
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    state.reveal()
                }
            }
    )
}

@Composable
private fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var enabled by remember { mutableStateOf(manager.isTouchExplorationEnabled) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
