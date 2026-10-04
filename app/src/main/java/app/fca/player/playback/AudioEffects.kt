package app.fca.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import app.fca.player.data.model.EqSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqBand(val index: Int, val centerFreqHz: Int, val minLevel: Short, val maxLevel: Short) {
    val label: String
        get() = if (centerFreqHz >= 1000) "${centerFreqHz / 1000}k" else "$centerFreqHz"
}

/**
 * Обёртка над системными аудиоэффектами. Живёт в процессе приложения как singleton:
 * сервис привязывает её к audio session id плеера, экран эквалайзера меняет настройки.
 */
object AudioEffects {
    private const val TAG = "AudioEffects"

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var sessionId: Int = 0

    private val _bands = MutableStateFlow<List<EqBand>>(emptyList())
    val bands: StateFlow<List<EqBand>> = _bands.asStateFlow()

    private val _presets = MutableStateFlow<List<String>>(emptyList())
    val presets: StateFlow<List<String>> = _presets.asStateFlow()

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    @Synchronized
    fun attach(audioSessionId: Int, settings: EqSettings) {
        if (audioSessionId == 0) return
        if (audioSessionId == sessionId && equalizer != null) {
            apply(settings)
            return
        }
        release()
        sessionId = audioSessionId
        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            val bandList = (0 until eq.numberOfBands).map { i ->
                val range = eq.bandLevelRange
                EqBand(i, eq.getCenterFreq(i.toShort()) / 1000, range[0], range[1])
            }
            _bands.value = bandList
            _presets.value = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
            bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
            virtualizer = runCatching { Virtualizer(0, audioSessionId) }.getOrNull()
            loudness = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
            _available.value = true
            apply(settings)
        } catch (t: Throwable) {
            Log.w(TAG, "Эквалайзер недоступен", t)
            _available.value = false
        }
    }

    /** Уровни полос для системного пресета (чтобы показать их на слайдерах). */
    @Synchronized
    fun presetLevels(presetIndex: Int): List<Int>? {
        val eq = equalizer ?: return null
        return try {
            eq.usePreset(presetIndex.toShort())
            (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()).toInt() }
        } catch (_: Throwable) {
            null
        }
    }

    @Synchronized
    fun apply(settings: EqSettings) {
        val eq = equalizer ?: return
        try {
            eq.enabled = settings.enabled
            if (settings.enabled) {
                if (settings.presetIndex >= 0 && settings.presetIndex < eq.numberOfPresets) {
                    eq.usePreset(settings.presetIndex.toShort())
                } else {
                    settings.bandLevels.forEachIndexed { i, level ->
                        if (i < eq.numberOfBands) {
                            val clamped = level.coerceIn(eq.bandLevelRange[0].toInt(), eq.bandLevelRange[1].toInt())
                            eq.setBandLevel(i.toShort(), clamped.toShort())
                        }
                    }
                }
            }
            bassBoost?.let {
                it.enabled = settings.enabled && settings.bassBoost > 0
                if (it.strengthSupported) it.setStrength(settings.bassBoost.coerceIn(0, 1000).toShort())
            }
            virtualizer?.let {
                it.enabled = settings.enabled && settings.virtualizer > 0
                if (it.strengthSupported) it.setStrength(settings.virtualizer.coerceIn(0, 1000).toShort())
            }
            loudness?.let {
                it.enabled = settings.enabled && settings.loudness > 0
                it.setTargetGain(settings.loudness.coerceIn(0, 1500))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Не удалось применить эффекты", t)
        }
    }

    @Synchronized
    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null; bassBoost = null; virtualizer = null; loudness = null
        sessionId = 0
        _available.value = false
    }
}
