package app.fca.eq.data

import kotlinx.serialization.Serializable
import kotlin.math.log2
import kotlin.math.pow

/** Центральные частоты 10 полос, Гц. */
val BAND_FREQS = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
const val BAND_MIN_DB = -15f
const val BAND_MAX_DB = 15f

fun bandLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000}k" else "$hz"

/** Системные эффекты: работают и для других приложений, и для встроенного плеера. */
@Serializable
data class EqConfig(
    val bands: List<Float> = List(BAND_FREQS.size) { 0f },
    /** Предусилитель, дБ (-12..+12). */
    val preamp: Float = 0f,
    /** Усиление громкости поверх всего (LoudnessEnhancer), дБ 0..+30. */
    val boostDb: Float = 0f,
    /** Усиление басов 0..1000. */
    val bass: Int = 0,
    /** Объёмный звук 0..1000. */
    val virtualizer: Int = 0,
    /** Баланс -1 (лево) .. +1 (право). */
    val balance: Float = 0f,
    /** Лимитер: не даёт звуку хрипеть при сильном усилении. */
    val limiter: Boolean = true,
    /** Системный ревербератор: 0 — выкл, 1..6 — PresetReverb. */
    val reverb: Int = 0,
)

/** DSP-эффекты встроенного плеера (питч, скорость, 8D и т.д.). */
@Serializable
data class PlayerFx(
    /** Сдвиг тона в полутонах (-12..+12). */
    val pitch: Float = 0f,
    /** Скорость 0.25..3. */
    val speed: Float = 1f,
    /** Скорость вращения 8D, Гц. 0 — выкл. */
    val rate8d: Float = 0f,
    val echoMix: Float = 0f,
    val echoMs: Int = 320,
    val echoFeedback: Float = 0.4f,
    val reverbMix: Float = 0f,
    val roomSize: Float = 0.75f,
    /** Ширина стерео: 0 — моно, 1 — как есть, 2 — очень широко. */
    val width: Float = 1f,
    /** Подавление вокала (караоке). */
    val karaoke: Boolean = false,
) {
    val pitchFactor: Float get() = 2f.pow(pitch / 12f)
}

@Serializable
data class NamedEq(val name: String, val eq: EqConfig)

@Serializable
data class QueueItem(val uri: String, val title: String)

@Serializable
data class AppState(
    val enabled: Boolean = true,
    /** true — эффекты на всём звуке (сессия 0), false — на сессиях плееров. */
    val globalMode: Boolean = false,
    val eq: EqConfig = EqConfig(),
    val eqPresetName: String = "Ровно",
    val fx: PlayerFx = PlayerFx(),
    val fxPresetName: String = "Обычный",
    val customPresets: List<NamedEq> = emptyList(),
    val queue: List<QueueItem> = emptyList(),
)

private fun semis(speed: Float) = 12f * log2(speed)

object Presets {
    private fun eq(vararg b: Number, bass: Int = 0, virt: Int = 0, boost: Float = 0f, preamp: Float = 0f) =
        EqConfig(bands = b.map { it.toFloat() }, bass = bass, virtualizer = virt, boostDb = boost, preamp = preamp)

    val eq: List<NamedEq> = listOf(
        NamedEq("Ровно", EqConfig()),
        NamedEq("Бас-буст", eq(7, 6, 5, 3, 1, 0, 0, 0, 0, 0, bass = 400)),
        NamedEq("Супер-бас", eq(10, 9, 7, 4, 1, 0, 0, 0, 1, 2, bass = 800, boost = 3f)),
        NamedEq("Громко", eq(5, 4, 2, 0, 0, 0, 1, 3, 4, 4, boost = 10f)),
        NamedEq("Максимум", eq(6, 5, 3, 1, 0, 1, 2, 3, 4, 4, bass = 300, boost = 18f, preamp = 3f)),
        NamedEq("Рок", eq(5, 4, 3, 1, -1, -1, 1, 3, 4, 5)),
        NamedEq("Поп", eq(-1, 1, 3, 4, 3, 1, 0, -1, -1, -1)),
        NamedEq("Электроника", eq(6, 5, 2, 0, -2, 1, 0, 2, 4, 5)),
        NamedEq("Хип-хоп", eq(6, 5, 3, 1, -1, -1, 1, 0, 2, 3, bass = 300)),
        NamedEq("Вокал", eq(-2, -2, -1, 1, 3, 4, 4, 3, 1, 0)),
        NamedEq("Высокие", eq(0, 0, 0, 0, 0, 1, 3, 5, 6, 7)),
        NamedEq("Наушники", eq(3, 2, 1, 0, -1, 0, 1, 2, 3, 3, virt = 500)),
        NamedEq("Динамик телефона", eq(-6, -3, 1, 3, 3, 2, 2, 2, 1, 0, boost = 6f)),
        NamedEq("Классика", eq(4, 3, 2, 1, 0, 0, 0, 2, 3, 4)),
        NamedEq("Джаз", eq(3, 2, 1, 2, -1, -1, 0, 1, 2, 3)),
        NamedEq("Речь / подкаст", eq(-6, -4, -1, 1, 3, 4, 3, 1, -1, -3)),
    )

    val fx: List<Pair<String, PlayerFx>> = listOf(
        "Обычный" to PlayerFx(),
        "Nightcore" to PlayerFx(speed = 1.25f, pitch = semis(1.25f)),
        "Slowed + Reverb" to PlayerFx(speed = 0.85f, pitch = semis(0.85f), reverbMix = 0.35f, roomSize = 0.88f),
        "Vaporwave" to PlayerFx(speed = 0.8f, pitch = semis(0.8f), reverbMix = 0.25f, echoMix = 0.12f, echoMs = 420),
        "8D Audio" to PlayerFx(rate8d = 0.12f, reverbMix = 0.15f, roomSize = 0.7f),
        "Бурундук" to PlayerFx(pitch = 7f),
        "Демон" to PlayerFx(pitch = -7f),
        "Караоке" to PlayerFx(karaoke = true),
        "Стадион" to PlayerFx(reverbMix = 0.45f, roomSize = 0.95f, echoMix = 0.18f, echoMs = 450),
        "Эхо" to PlayerFx(echoMix = 0.4f, echoMs = 350, echoFeedback = 0.45f),
        "Широкое стерео" to PlayerFx(width = 1.8f),
        "Моно" to PlayerFx(width = 0f),
    )

    val reverbNames = listOf("Выкл", "Малая комната", "Средняя комната", "Большая комната", "Средний зал", "Большой зал", "Пластина")
}
