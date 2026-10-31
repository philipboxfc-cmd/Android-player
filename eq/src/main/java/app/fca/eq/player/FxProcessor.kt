package app.fca.eq.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.fca.eq.data.PlayerFx
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * DSP встроенного плеера: караоке, ширина стерео/моно, эхо, реверб, 8D.
 * Питч и скорость делает сам ExoPlayer (Sonic) — после этого процессора.
 * Вход: PCM16 моно/стерео, выход: всегда PCM16 стерео.
 */
@OptIn(UnstableApi::class)
class FxProcessor : BaseAudioProcessor() {

    @Volatile
    var fx: PlayerFx = PlayerFx()

    private var sampleRate = 44100
    private var inChannels = 2

    private var echoL = FloatArray(0)
    private var echoR = FloatArray(0)
    private var echoPos = 0

    private var reverbL: Reverb? = null
    private var reverbR: Reverb? = null

    private var lfoPhase = 0.0
    private var karaokeLow = 0f
    private var karaokeCoef = 0f

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        inChannels = inputAudioFormat.channelCount
        val maxDelay = sampleRate * 2
        echoL = FloatArray(maxDelay)
        echoR = FloatArray(maxDelay)
        echoPos = 0
        reverbL = Reverb(sampleRate, 0)
        reverbR = Reverb(sampleRate, STEREO_SPREAD)
        karaokeCoef = 1f - exp(-2.0 * PI * 180.0 / sampleRate).toFloat()
        return AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / (2 * inChannels)
        if (frames == 0) return
        val out = replaceOutputBuffer(frames * 4)
        val f = fx
        val bypass = !f.karaoke && f.width == 1f && f.echoMix <= 0f && f.reverbMix <= 0f && f.rate8d <= 0f

        val echoDelay = (f.echoMs * sampleRate / 1000).coerceIn(1, echoL.size - 1)
        val echoFb = f.echoFeedback.coerceIn(0f, 0.9f)
        val lfoStep = 2.0 * PI * f.rate8d / sampleRate
        reverbL?.setRoom(f.roomSize)
        reverbR?.setRoom(f.roomSize)

        repeat(frames) {
            var l = inputBuffer.getShort() / 32768f
            var r = if (inChannels == 2) inputBuffer.getShort() / 32768f else l

            if (!bypass) {
                if (f.karaoke) {
                    // Убираем центр (обычно там голос), но оставляем бас.
                    val mid = (l + r) * 0.5f
                    val side = (l - r) * 0.5f
                    karaokeLow += karaokeCoef * (mid - karaokeLow)
                    l = karaokeLow + side * 1.4f
                    r = karaokeLow - side * 1.4f
                }
                if (f.width != 1f) {
                    val mid = (l + r) * 0.5f
                    val side = (l - r) * 0.5f * f.width
                    l = mid + side
                    r = mid - side
                }
                if (f.echoMix > 0f) {
                    var read = echoPos - echoDelay
                    if (read < 0) read += echoL.size
                    val dl = echoL[read]
                    val dr = echoR[read]
                    echoL[echoPos] = l + dr * echoFb // лёгкий пинг-понг
                    echoR[echoPos] = r + dl * echoFb
                    if (++echoPos >= echoL.size) echoPos = 0
                    l += dl * f.echoMix
                    r += dr * f.echoMix
                }
                if (f.reverbMix > 0f) {
                    val input = (l + r) * 0.5f
                    val wl = reverbL!!.process(input)
                    val wr = reverbR!!.process(input)
                    val dry = 1f - f.reverbMix * 0.5f
                    l = l * dry + wl * f.reverbMix
                    r = r * dry + wr * f.reverbMix
                }
                if (f.rate8d > 0f) {
                    lfoPhase += lfoStep
                    if (lfoPhase > 2 * PI) lfoPhase -= 2 * PI
                    val pan = sin(lfoPhase).toFloat()
                    val mono = (l + r) * 0.5f
                    // Равномощностное панорамирование + немного исходного стерео.
                    val gl = sqrt((1f - pan) * 0.5f) * SQRT2
                    val gr = sqrt((1f + pan) * 0.5f) * SQRT2
                    l = mono * gl * 0.85f + l * 0.15f
                    r = mono * gr * 0.85f + r * 0.15f
                }
            }
            out.putShort(toPcm(l))
            out.putShort(toPcm(r))
        }
        out.flip()
    }

    private fun toPcm(x: Float): Short {
        // Мягкое ограничение, чтобы эффекты не щёлкали при перегрузе.
        val y = when {
            x > 0.9f -> 0.9f + 0.1f * kotlin.math.tanh((x - 0.9f) / 0.1f)
            x < -0.9f -> -0.9f + 0.1f * kotlin.math.tanh((x + 0.9f) / 0.1f)
            else -> x
        }
        return (y * 32767f).toInt().coerceIn(-32768, 32767).toShort()
    }

    override fun onFlush() {
        echoL.fill(0f)
        echoR.fill(0f)
        echoPos = 0
        reverbL?.clear()
        reverbR?.clear()
        karaokeLow = 0f
    }

    override fun onReset() {
        echoL = FloatArray(0)
        echoR = FloatArray(0)
        reverbL = null
        reverbR = null
    }

    /** Freeverb-подобный ревербератор: 8 гребенчатых + 4 всепропускающих фильтра. */
    private class Reverb(sampleRate: Int, spread: Int) {
        private val scale = sampleRate / 44100f
        private val combs = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
            .map { Comb(((it + spread) * scale).toInt().coerceAtLeast(16)) }
        private val allpasses = intArrayOf(556, 441, 341, 225)
            .map { Allpass(((it + spread) * scale).toInt().coerceAtLeast(16)) }

        fun setRoom(size: Float) {
            val fb = 0.7f + size.coerceIn(0f, 1f) * 0.28f
            combs.forEach { it.feedback = fb }
        }

        fun process(x: Float): Float {
            val input = x * 0.015f
            var sum = 0f
            for (c in combs) sum += c.process(input)
            for (a in allpasses) sum = a.process(sum)
            return sum * 3f
        }

        fun clear() {
            combs.forEach { it.buf.fill(0f); it.store = 0f }
            allpasses.forEach { it.buf.fill(0f) }
        }
    }

    private class Comb(size: Int) {
        val buf = FloatArray(size)
        var idx = 0
        var store = 0f
        var feedback = 0.84f
        fun process(x: Float): Float {
            val out = buf[idx]
            store = out * 0.8f + store * 0.2f
            buf[idx] = x + store * feedback
            if (++idx >= buf.size) idx = 0
            return out
        }
    }

    private class Allpass(size: Int) {
        val buf = FloatArray(size)
        var idx = 0
        fun process(x: Float): Float {
            val b = buf[idx]
            buf[idx] = x + b * 0.5f
            if (++idx >= buf.size) idx = 0
            return b - x
        }
    }

    private companion object {
        const val STEREO_SPREAD = 23
        val SQRT2 = sqrt(2f)
    }
}
