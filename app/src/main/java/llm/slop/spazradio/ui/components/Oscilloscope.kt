package llm.slop.spazradio.ui.components

import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.delay
import android.graphics.Canvas as AndroidCanvas
import llm.slop.spazradio.ui.theme.NeonGreen

@Composable
fun Oscilloscope(
    waveform: ByteArray?,
    isPlaying: Boolean,
    lissajousMode: Boolean,
    modifier: Modifier = Modifier
) {
    /* ---------- Frame Clock ---------- */

    val frameClock = remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16L * 2) // ~30 FPS
            frameClock.longValue++
        }
    }

    /* ---------- Persistent Drawing State ---------- */

    val bitmapRef = remember { mutableStateOf<Bitmap?>(null) }
    val canvasRef = remember { mutableStateOf<AndroidCanvas?>(null) }

    var loudnessEnv by remember { mutableFloatStateOf(12f) }

    /* ---------- Paints ---------- */

    val fadePaint = remember {
        android.graphics.Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            color = android.graphics.Color.argb(28, 0, 0, 0)
        }
    }

    val linePaint = remember {
        android.graphics.Paint().apply {
            color = NeonGreen.toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f
            isAntiAlias = true
            setShadowLayer(6f, 0f, 0f, NeonGreen.toArgb())
        }
    }

    val path = remember { android.graphics.Path() }

    /* ---------- Canvas ---------- */

    Canvas(modifier = modifier) {
        frameClock.longValue // trigger redraw

        val width = size.width.toInt()
        val height = size.height.toInt()

        if (bitmapRef.value == null ||
            bitmapRef.value!!.width != width ||
            bitmapRef.value!!.height != height
        ) {
            bitmapRef.value = createBitmap(width, height)
            canvasRef.value = AndroidCanvas(bitmapRef.value!!)
        }

        val bmp = bitmapRef.value!!
        val cvs = canvasRef.value!!

        /* ---------- Fade Previous Frame ---------- */

        cvs.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)

        if (isPlaying && waveform != null && waveform.size > 8) {

            /* ---------- RMS + Peak ---------- */

            var sumSq = 0f
            var peak = 0

            for (b in waveform) {
                val v = (b.toInt() and 0xFF) - 128
                val a = kotlin.math.abs(v)
                if (a > peak) peak = a
                sumSq += v * v
            }

            val rms = kotlin.math.sqrt(sumSq / waveform.size)

            /* ---------- Loudness Envelope ---------- */

            val attack = 0.22f
            val release = 0.06f

            loudnessEnv += if (rms > loudnessEnv)
                (rms - loudnessEnv) * attack
            else
                (rms - loudnessEnv) * release

            /* ---------- Visual AGC (Signal Gain) ---------- */

            val targetRms = 34f
            val gain = (targetRms / (loudnessEnv + 1f))
                .coerceIn(1.3f, 6.5f)

            /* ---------- Dynamic Trail ---------- */

            fadePaint.color = android.graphics.Color.argb(
                when {
                    peak < 10 -> 18
                    peak < 30 -> 28
                    peak < 60 -> 40
                    else -> 55
                },
                0, 0, 0
            )

            /* ---------- Geometry ---------- */

            val cx = width / 2f
            val cy = height / 2f
            val xScale = width * 0.48f
            val yScale = height * 0.48f

            path.reset()

            if (lissajousMode) {

                val count = waveform.size
                val phaseShift = (count * 0.37f).toInt()

                var lastA = 0f
                var lastB = 0f
                var first = true

                val step = (count / 256).coerceAtLeast(1)
                for (i in 0 until count step step) {

                    val rawA =
                        ((waveform[i].toInt() and 0xFF) - 128) / 128f
                    val j = (i + phaseShift) % count
                    val rawB =
                        ((waveform[j].toInt() and 0xFF) - 128) / 128f

                    val a =
                        (0.85f * rawA + 0.15f * (rawA - lastA)) * gain
                    val b =
                        (0.85f * rawB + 0.15f * (rawB - lastB)) * gain

                    lastA = rawA
                    lastB = rawB

                    val ax = a.coerceIn(-1.2f, 1.2f)
                    val by = b.coerceIn(-1.2f, 1.2f)

                    val x = cx + ax * xScale
                    val y = cy + by * yScale

                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                }

                path.close()
                cvs.drawPath(path, linePaint)
            }
        }

        drawImage(bmp.asImageBitmap())
    }
}
