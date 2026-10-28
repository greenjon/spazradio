package llm.slop.spazradio.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** How long the screen must stay covered before it counts: a hand passing over the phone doesn't. */
private const val POCKET_AFTER_MS = 3000L
/** Proximity sensors report a distance in cm, many only "near" (0) or "far" (their maximum range). */
private const val NEAR_CM = 5f
/** Gravity along the screen's axis, in m/s², below which the screen faces the floor (within about 25°). */
private const val FACE_DOWN_Z = -8.8f

/**
 * True once the screen has been covered for a few seconds while [enabled]: the phone went into a pocket or a
 * bag, or lies face down (which covers the proximity sensor too). The sensors are only listened to while
 * [enabled]. A device without a proximity sensor (most tablets) falls back to gravity: face down only.
 */
@Composable
fun rememberPocketed(enabled: Boolean): Boolean {
    val context = LocalContext.current
    val sensors = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager? }
    var covered by remember { mutableStateOf(false) }
    var pocketed by remember { mutableStateOf(false) }

    DisposableEffect(enabled, sensors) {
        val proximity = sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val gravity = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensor = proximity ?: gravity
        if (!enabled || sensors == null || sensor == null) {
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
    return pocketed
}
