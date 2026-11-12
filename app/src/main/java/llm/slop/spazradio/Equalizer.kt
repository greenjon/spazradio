package llm.slop.spazradio

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * OFF: flat. BUILT_IN: the app's own three-band EQ. SYSTEM: the app announces its audio session so the phone's
 * equalizer (Samsung SoundAlive, Wavelet, ...) can attach, and stays flat itself so the two never stack.
 */
enum class EqMode { OFF, BUILT_IN, SYSTEM }

/** Gains in whole dB, each within [MIN_DB]..[MAX_DB]. */
data class EqBands(val bassDb: Int = 0, val midDb: Int = 0, val trebleDb: Int = 0) {
    val isFlat get() = bassDb == 0 && midDb == 0 && trebleDb == 0

    companion object {
        const val MIN_DB = -12
        const val MAX_DB = 12
    }
}

object EqPrefs {
    const val MODE = "eq_mode"
    const val BASS = "eq_bass_db"
    const val MID = "eq_mid_db"
    const val TREBLE = "eq_treble_db"
    val KEYS = setOf(MODE, BASS, MID, TREBLE)

    fun mode(prefs: SharedPreferences): EqMode =
        EqMode.entries.firstOrNull { it.name == prefs.getString(MODE, null) } ?: EqMode.OFF

    fun bands(prefs: SharedPreferences): EqBands = EqBands(
        prefs.getInt(BASS, 0).coerceIn(EqBands.MIN_DB, EqBands.MAX_DB),
        prefs.getInt(MID, 0).coerceIn(EqBands.MIN_DB, EqBands.MAX_DB),
        prefs.getInt(TREBLE, 0).coerceIn(EqBands.MIN_DB, EqBands.MAX_DB),
    )

    /** A saved SYSTEM choice whose equalizer app has since gone away plays flat. */
    fun effectiveMode(context: Context, prefs: SharedPreferences): EqMode {
        val saved = mode(prefs)
        return if (saved == EqMode.SYSTEM && !SystemEq.isAvailable(context)) EqMode.OFF else saved
    }
}

/** The phone's own equalizer, reached through the standard AudioEffect intents. */
@OptIn(UnstableApi::class)
object SystemEq {
    /** True when some app on the phone can show an equalizer panel. Needs the <queries> entry in the manifest. */
    fun isAvailable(context: Context): Boolean =
        context.packageManager.resolveActivity(Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL), 0) != null

    fun panelIntent(context: Context, sessionId: Int): Intent =
        withSession(Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL), context, sessionId)

    fun open(context: Context, sessionId: Int) =
        context.sendBroadcast(withSession(Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION), context, sessionId))

    fun close(context: Context, sessionId: Int) =
        context.sendBroadcast(withSession(Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION), context, sessionId))

    private fun withSession(intent: Intent, context: Context, sessionId: Int): Intent = intent.apply {
        if (sessionId != C.AUDIO_SESSION_ID_UNSET) putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    }
}

/**
 * RBJ cookbook biquad coefficients, normalized so a0 = 1: [b0, b1, b2, a1, a2].
 * Bass is a low shelf, mid a wide peak, treble a high shelf.
 */
object EqFilters {
    const val BASS_HZ = 120.0
    const val MID_HZ = 1000.0
    const val MID_Q = 0.7
    const val TREBLE_HZ = 6000.0

    fun lowShelf(fs: Double, f0: Double, db: Double): DoubleArray = shelf(fs, f0, db, high = false)
    fun highShelf(fs: Double, f0: Double, db: Double): DoubleArray = shelf(fs, f0, db, high = true)

    fun peak(fs: Double, f0: Double, q: Double, db: Double): DoubleArray {
        val a = 10.0.pow(db / 40)
        val w = 2 * PI * f0 / fs
        val alpha = sin(w) / (2 * q)
        val c = cos(w)
        return normalize(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
    }

    // Shelf slope S = 1.
    private fun shelf(fs: Double, f0: Double, db: Double, high: Boolean): DoubleArray {
        val a = 10.0.pow(db / 40)
        val w = 2 * PI * f0 / fs
        val c = cos(w)
        val k = 2 * sqrt(a) * (sin(w) / 2 * sqrt(2.0))
        return if (!high) normalize(
            a * ((a + 1) - (a - 1) * c + k), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - k),
            (a + 1) + (a - 1) * c + k, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - k,
        ) else normalize(
            a * ((a + 1) + (a - 1) * c + k), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - k),
            (a + 1) - (a - 1) * c + k, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - k,
        )
    }

    private fun normalize(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
        doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

    fun forBands(fs: Double, bands: EqBands): List<DoubleArray> = listOf(
        lowShelf(fs, BASS_HZ, bands.bassDb.toDouble()),
        peak(fs, MID_HZ, MID_Q, bands.midDb.toDouble()),
        highShelf(fs, TREBLE_HZ, bands.trebleDb.toDouble()),
    )

    /** Boosts would push loud masters past full scale, so the whole signal drops by the largest boost. */
    fun headroom(bands: EqBands): Double =
        10.0.pow(-max(0, maxOf(bands.bassDb, bands.midDb, bands.trebleDb)) / 20.0)
}

/**
 * The built-in EQ, placed after the visuals tap so the visuals react to the broadcast, not to the listener's
 * settings. [bands] is set from the main thread; null (or flat) passes audio through untouched.
 */
@OptIn(UnstableApi::class)
class EqAudioProcessor : BaseAudioProcessor() {
    @Volatile var bands: EqBands? = null

    private var applied: EqBands? = null
    private var coeffs: List<DoubleArray> = emptyList()
    private var gain = 1.0
    // Per channel, per filter: z1, z2 (transposed direct form II).
    private var state = DoubleArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        val target = bands?.takeUnless { it.isFlat }
        if (target == null) {
            applied = null
            out.put(inputBuffer)
            out.flip()
            return
        }
        val channels = inputAudioFormat.channelCount
        if (target != applied || state.size != channels * 6) {
            if (applied == null || state.size != channels * 6) state = DoubleArray(channels * 6)
            coeffs = EqFilters.forBands(inputAudioFormat.sampleRate.toDouble(), target)
            gain = EqFilters.headroom(target)
            applied = target
        }
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (float) 4 else 2
        var ch = 0
        var pos = inputBuffer.position()
        val end = inputBuffer.limit()
        while (pos < end) {
            var x = (if (float) inputBuffer.getFloat(pos).toDouble() else inputBuffer.getShort(pos) / 32768.0) * gain
            for (f in 0 until 3) {
                val c = coeffs[f]
                val s = (ch * 3 + f) * 2
                val y = c[0] * x + state[s]
                state[s] = c[1] * x - c[3] * y + state[s + 1]
                state[s + 1] = c[2] * x - c[4] * y
                x = y
            }
            if (float) out.putFloat(x.toFloat())
            else out.putShort((x * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            pos += bytesPerSample
            ch = (ch + 1) % channels
        }
        inputBuffer.position(end)
        out.flip()
    }

    override fun onFlush() {
        state.fill(0.0)
    }

    override fun onReset() {
        applied = null
        state = DoubleArray(0)
    }
}
