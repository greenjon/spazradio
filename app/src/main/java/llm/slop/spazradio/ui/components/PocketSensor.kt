package llm.slop.spazradio.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

/** How long the screen must stay covered before it counts: a hand passing over the phone doesn't. */
private const val POCKET_AFTER_MS = 3000L
/** Proximity sensors report a distance in cm, many only "near" (0) or "far" (their maximum range). */
private const val NEAR_CM = 5f
/** Gravity along the screen's axis, in m/s², below which the screen faces the floor (within about 25°). */
private const val FACE_DOWN_Z = -8.8f
/** A safety net only: the screen timeout puts the phone to sleep (and the app lets go) long before this. */
private const val LOCK_TIMEOUT_MS = 60 * 60 * 1000L

/**
 * True once the screen has been covered for a few seconds while [enabled]: the phone went into a pocket or a
 * bag, or lies face down (which covers the proximity sensor too). The sensors are only listened to while
 * [enabled] and the app is on screen. A device without a proximity sensor (most tablets) falls back to
 * gravity: face down only.
 *
 * While pocketed it also holds the proximity wake lock phone calls use, so the display goes dark at once
 * (and ignores touches) instead of waiting out the screen timeout, which releasing keep-awake restarts;
 * the timeout still runs, so the phone locks as usual. Uncovered before then, the display comes back.
 */
@Composable
fun rememberPocketed(enabled: Boolean): Boolean {
    val context = LocalContext.current
    val sensors = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager? }
    val power = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager? }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) started = true
            if (event == Lifecycle.Event.ON_STOP) started = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val listening = enabled && started
    var covered by remember { mutableStateOf(false) }
    var pocketed by remember { mutableStateOf(false) }

    DisposableEffect(listening, sensors) {
        val proximity = sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val gravity = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensor = proximity ?: gravity
        if (!listening || sensors == null || sensor == null) {
            covered = false
            return@DisposableEffect onDispose { }
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                covered = if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
                    event.values[0] < minOf(NEAR_CM, event.sensor.maximumRange)
                } else {
                    event.values[2] < FACE_DOWN_Z
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose {
            sensors.unregisterListener(listener)
            covered = false
        }
    }

    LaunchedEffect(covered) {
        if (covered) {
            delay(POCKET_AFTER_MS)
            pocketed = true
        } else {
            pocketed = false
        }
    }

    DisposableEffect(pocketed, power) {
        val screenOff = PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK
        if (!pocketed || power == null || !power.isWakeLockLevelSupported(screenOff)) {
            return@DisposableEffect onDispose { }
        }
        val lock = power.newWakeLock(screenOff, "spazradio:pocket").apply { setReferenceCounted(false); acquire(LOCK_TIMEOUT_MS) }
        // Let go because the music stopped (not because the phone came out): stay dark until it is uncovered.
        onDispose { if (lock.isHeld) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }
    }
    return pocketed
}
