package app.fca.player.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Пользовательский плейлист: имя, вайб, цвет и список id треков. Хранится в JSON. */
@Serializable
data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val vibe: Vibe = Vibe.CHILL,
    /** ARGB-цвет; по умолчанию — цвет вайба. */
    val color: Long = vibe.defaultColor,
    val description: String = "",
    val trackIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    val size: Int get() = trackIds.size
}

/** Настройки эквалайзера и эффектов. */
@Serializable
data class EqSettings(
    val enabled: Boolean = false,
    /** -1 = ручная настройка, иначе индекс системного пресета. */
    val presetIndex: Int = -1,
    /** Уровни полос в миллибелах (как у android.media.audiofx.Equalizer). */
    val bandLevels: List<Int> = emptyList(),
    /** 0..1000 */
    val bassBoost: Int = 0,
    /** 0..1000 */
    val virtualizer: Int = 0,
    /** Усиление громкости в мБ, 0..1500 */
    val loudness: Int = 0,
)
