package llm.slop.spazradio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

class EqualizerTest {
    private val fs = 48000.0

    /** |H(e^jw)| in dB at [hz]. */
    private fun responseDb(c: DoubleArray, hz: Double): Double {
        val w = 2 * PI * hz / fs
        val nr = c[0] + c[1] * cos(w) + c[2] * cos(2 * w)
        val ni = -(c[1] * sin(w) + c[2] * sin(2 * w))
        val dr = 1 + c[3] * cos(w) + c[4] * cos(2 * w)
        val di = -(c[3] * sin(w) + c[4] * sin(2 * w))
        return 20 * log10(hypot(nr, ni) / hypot(dr, di))
    }

    @Test fun lowShelfReachesItsGainAtLowFrequencies() {
        for (db in listOf(-12.0, 6.0, 12.0)) {
            val c = EqFilters.lowShelf(fs, EqFilters.BASS_HZ, db)
            assertEquals(db, responseDb(c, 1.0), 0.05)
            assertEquals(0.0, responseDb(c, 10000.0), 0.3)
        }
    }

    @Test fun highShelfReachesItsGainAtHighFrequencies() {
        for (db in listOf(-12.0, 6.0, 12.0)) {
            val c = EqFilters.highShelf(fs, EqFilters.TREBLE_HZ, db)
            assertEquals(db, responseDb(c, 23999.0), 0.05)
            assertEquals(0.0, responseDb(c, 50.0), 0.1)
        }
    }

    @Test fun peakHitsItsGainAtTheCenter() {
        val c = EqFilters.peak(fs, EqFilters.MID_HZ, EqFilters.MID_Q, -9.0)
        assertEquals(-9.0, responseDb(c, EqFilters.MID_HZ), 0.01)
        assertEquals(0.0, responseDb(c, 20.0), 0.2)
    }

    @Test fun headroomMatchesTheLargestBoost() {
        assertEquals(1.0, EqFilters.headroom(EqBands(-6, -3, 0)), 1e-9)
        assertEquals(10.0.pow(-9 / 20.0), EqFilters.headroom(EqBands(9, 3, -12)), 1e-9)
    }

    private fun processor(bands: EqBands?) = EqAudioProcessor().apply {
        this.bands = bands
        configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun run(p: EqAudioProcessor, samples: ShortArray): ShortArray {
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it) }
        input.flip()
        p.queueInput(input)
        val out = p.output
        return ShortArray(out.remaining() / 2) { out.short }
    }

    /** Stereo, the same tone on both channels. */
    private fun tone(hz: Double, frames: Int, amp: Double = 8000.0) =
        ShortArray(frames * 2) { i -> (amp * sin(2 * PI * hz * (i / 2) / fs)).toInt().toShort() }

    private fun peakOfTail(s: ShortArray) = s.drop(s.size / 2).maxOf { abs(it.toInt()) }

    @Test fun flatOrOffPassesAudioUntouched() {
        val input = tone(440.0, 1024)
        assertArrayEquals(input, run(processor(null), input))
        assertArrayEquals(input, run(processor(EqBands()), input))
    }

    @Test fun trebleCutLowersHighsAndLeavesBass() {
        val p = processor(EqBands(trebleDb = -12))
        val highs = peakOfTail(run(p, tone(15000.0, 4800)))
        assertTrue("highs $highs", highs < 8000 * 0.35)
        val lows = peakOfTail(run(processor(EqBands(trebleDb = -12)), tone(100.0, 4800)))
        assertTrue("lows $lows", abs(lows - 8000) < 200)
    }

    @Test fun boostNeverClipsAFullScaleSignal() {
        val out = run(processor(EqBands(bassDb = 12)), tone(60.0, 9600, amp = 32000.0))
        assertTrue(peakOfTail(out) < 32767)
    }
}
