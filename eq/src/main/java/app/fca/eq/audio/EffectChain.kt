package app.fca.eq.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import app.fca.eq.data.BAND_FREQS
import app.fca.eq.data.EqConfig
import kotlin.math.log10
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Цепочка системных эффектов на одной аудиосессии.
 *
 * Основа — DynamicsProcessing (Android 9+): 10-полосный EQ, предусилитель, баланс и лимитер.
 * Сверху — BassBoost, Virtualizer, LoudnessEnhancer (сильное усиление) и PresetReverb.
 * Если DynamicsProcessing недоступен, используется обычный Equalizer с полосами устройства.
 */
class EffectChain(val sessionId: Int) {

    private var dp: DynamicsProcessing? = null
    private var legacyEq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var reverb: PresetReverb? = null

    val usesDynamics: Boolean get() = dp != null
    val isWorking: Boolean get() = dp != null || legacyEq != null || loudness != null || bass != null

    init {
        dp = tryCreate("DynamicsProcessing") { createDynamics() }
        if (dp == null) legacyEq = tryCreate("Equalizer") { Equalizer(PRIORITY, sessionId) }
        bass = tryCreate("BassBoost") { BassBoost(PRIORITY, sessionId) }
        virtualizer = tryCreate("Virtualizer") { Virtualizer(PRIORITY, sessionId) }
        loudness = tryCreate("LoudnessEnhancer") { LoudnessEnhancer(sessionId) }
        reverb = tryCreate("PresetReverb") { PresetReverb(PRIORITY, sessionId) }
    }

    private fun <T> tryCreate(name: String, block: () -> T): T? = try {
        block()
    } catch (t: Throwable) {
        Log.w(TAG, "$name недоступен на сессии $sessionId: ${t.message}")
        null
    }

    private fun createDynamics(): DynamicsProcessing {
        val n = BAND_FREQS.size
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNELS,
            /* preEqInUse */ true, n,
            /* mbcInUse */ false, 0,
            /* postEqInUse */ false, 0,
            /* limiterInUse */ true,
        ).setPreferredFrameDuration(10f)
        val eq = DynamicsProcessing.Eq(true, true, n)
        for (i in 0 until n) eq.setBand(i, DynamicsProcessing.EqBand(true, CUTOFFS[i], 0f))
        config.setPreEqAllChannelsTo(eq)
        config.setLimiterAllChannelsTo(limiter(true))
        return DynamicsProcessing(PRIORITY, sessionId, config.build())
    }

    private fun limiter(enabled: Boolean) = DynamicsProcessing.Limiter(
        /* inUse */ true, enabled, /* linkGroup */ 0,
        /* attackMs */ 1f, /* releaseMs */ 80f, /* ratio */ 12f, /* thresholdDb */ -1.5f, /* postGain */ 0f,
    )

    @Synchronized
    fun apply(enabled: Boolean, cfg: EqConfig) {
        dp?.let { d ->
            safe("DP") {
                d.enabled = enabled
                if (enabled) {
                    cfg.bands.forEachIndexed { i, gain ->
                        if (i < BAND_FREQS.size) d.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, CUTOFFS[i], gain))
                    }
                    // Баланс: приглушаем противоположный канал.
                    val left = cfg.preamp + attenuationDb(cfg.balance.coerceAtLeast(0f))
                    val right = cfg.preamp + attenuationDb((-cfg.balance).coerceAtLeast(0f))
                    d.setInputGainbyChannel(0, left)
                    d.setInputGainbyChannel(1, right)
                    d.setLimiterAllChannelsTo(limiter(cfg.limiter))
                }
            }
        }
        legacyEq?.let { e ->
            safe("Equalizer") {
                e.enabled = enabled
                if (enabled) {
                    val range = e.bandLevelRange
                    for (b in 0 until e.numberOfBands) {
                        val hz = e.getCenterFreq(b.toShort()) / 1000f
                        val db = interpolate(cfg.bands, hz) + cfg.preamp
                        val mb = (db * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                        e.setBandLevel(b.toShort(), mb.toShort())
                    }
                }
            }
        }
        bass?.let { b ->
            safe("BassBoost") {
                b.enabled = enabled && cfg.bass > 0
                if (b.strengthSupported) b.setStrength(cfg.bass.coerceIn(0, 1000).toShort())
            }
        }
        virtualizer?.let { v ->
            safe("Virtualizer") {
                v.enabled = enabled && cfg.virtualizer > 0
                if (v.strengthSupported) v.setStrength(cfg.virtualizer.coerceIn(0, 1000).toShort())
            }
        }
        loudness?.let { l ->
            safe("Loudness") {
                l.setTargetGain((cfg.boostDb * 100).toInt().coerceIn(0, 3000))
                l.enabled = enabled && cfg.boostDb > 0f
            }
        }
        reverb?.let { r ->
            safe("Reverb") {
                if (cfg.reverb in 1..6) r.preset = cfg.reverb.toShort()
                r.enabled = enabled && cfg.reverb in 1..6
            }
        }
    }

    private inline fun safe(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "$name: не удалось применить на сессии $sessionId", t)
        }
    }

    @Synchronized
    fun release() {
        listOf(dp, legacyEq, bass, virtualizer, loudness, reverb).forEach { runCatching { it?.release() } }
        dp = null; legacyEq = null; bass = null; virtualizer = null; loudness = null; reverb = null
    }

    companion object {
        private const val TAG = "EffectChain"
        private const val CHANNELS = 2
        /** Максимальный приоритет — забираем управление у системных «улучшайзеров». */
        const val PRIORITY = Int.MAX_VALUE

        /** Верхняя граница каждой полосы — среднее геометрическое соседних центров. */
        private val CUTOFFS: FloatArray = FloatArray(BAND_FREQS.size) { i ->
            if (i == BAND_FREQS.lastIndex) 20000f else sqrt(BAND_FREQS[i].toFloat() * BAND_FREQS[i + 1])
        }

        private fun attenuationDb(amount: Float): Float {
            val g = (1f - amount).coerceIn(0.001f, 1f)
            return 20f * log10(g)
        }

        /** Значение 10-полосной кривой на произвольной частоте (по логарифмической оси). */
        fun interpolate(bands: List<Float>, hz: Float): Float {
            if (hz <= BAND_FREQS.first()) return bands.first()
            if (hz >= BAND_FREQS.last()) return bands.last()
            for (i in 0 until BAND_FREQS.lastIndex) {
                val a = BAND_FREQS[i].toFloat()
                val b = BAND_FREQS[i + 1].toFloat()
                if (hz in a..b) {
                    val t = (ln(hz) - ln(a)) / (ln(b) - ln(a))
                    return bands[i] + (bands[i + 1] - bands[i]) * t
                }
            }
            return 0f
        }
    }
}
