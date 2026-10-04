package app.fca.player.ai

import app.fca.player.data.model.Playlist
import app.fca.player.data.model.Vibe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Минимум данных о треке для промпта и сопоставления — без Android-зависимостей. */
data class AiTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)

/**
 * «ИИ-плейлисты»: собираем промпт со списком всех треков, пользователь отдаёт его любому чат-боту,
 * получает JSON и вставляет обратно — мы превращаем его в плейлисты.
 */
object AiImport {

    sealed interface TrackRef {
        data class ById(val id: Long) : TrackRef
        data class ByText(val title: String?, val artist: String?, val raw: String) : TrackRef
    }

    data class ParsedPlaylist(
        val name: String,
        val vibe: Vibe,
        val color: Long?,
        val description: String,
        val tracks: List<TrackRef>,
    )

    data class MatchedPlaylist(
        val parsed: ParsedPlaylist,
        val playlist: Playlist,
        val unmatched: List<String>,
    ) {
        val matchedCount: Int get() = playlist.trackIds.size
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; allowTrailingComma = true }

    // ---------- Промпт ----------

    fun buildPrompt(tracks: List<AiTrack>, playlistsHint: String = "6–10"): String {
        val vibes = Vibe.entries.joinToString(", ") { "${it.name} (${it.title})" }
        val sb = StringBuilder()
        sb.appendLine("Ты — музыкальный куратор с отличным вкусом. Ниже список моих треков в формате:")
        sb.appendLine("id | Исполнитель — Название | Альбом | длительность")
        sb.appendLine()
        sb.appendLine("Задача: разложи треки на $playlistsHint плейлистов по настроению («вайбу»), жанру, энергии и ситуации прослушивания.")
        sb.appendLine("Придумай каждому плейлисту красивое короткое название (можно с эмодзи), подбери вайб из списка и цвет в HEX, подходящий настроению, и одну короткую фразу-описание.")
        sb.appendLine("Один трек может быть в нескольких плейлистах, но старайся задействовать всю коллекцию. В плейлисте — от 8 треков, если коллекция позволяет.")
        sb.appendLine()
        sb.appendLine("Доступные значения vibe: $vibes")
        sb.appendLine()
        sb.appendLine("ОТВЕТЬ ТОЛЬКО ВАЛИДНЫМ JSON без пояснений и без markdown, строго по схеме:")
        sb.appendLine(
            """
            {
              "playlists": [
                {
                  "name": "Ночной неон 🌃",
                  "vibe": "NIGHT_DRIVE",
                  "color": "#8E24AA",
                  "description": "Синтвейв и медленный бит для дороги после полуночи",
                  "tracks": [12, 48, 103]
                }
              ]
            }
            """.trimIndent()
        )
        sb.appendLine()
        sb.appendLine("В поле tracks используй ТОЛЬКО числовые id из списка ниже. Не выдумывай id.")
        sb.appendLine()
        sb.appendLine("=== МОИ ТРЕКИ (${tracks.size}) ===")
        tracks.forEach { t ->
            sb.append(t.id).append(" | ")
                .append(t.artist).append(" — ").append(t.title)
                .append(" | ").append(t.album)
                .append(" | ").append(formatMs(t.durationMs))
                .appendLine()
        }
        return sb.toString()
    }

    private fun formatMs(ms: Long): String {
        val total = ms / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }

    // ---------- Разбор JSON ----------

    fun parse(raw: String): Result<List<ParsedPlaylist>> = runCatching {
        val cleaned = extractJson(raw)
        require(cleaned.isNotBlank()) { "Пустой ответ: вставь JSON, который выдал ИИ" }
        val root = json.parseToJsonElement(cleaned)
        val array: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["playlists"] ?: root["плейлисты"] ?: root["lists"] ?: root["items"]
                ?: root.values.firstOrNull { it is JsonArray })
                ?.let { it as? JsonArray }
                ?: throw IllegalArgumentException("Не нашёл массив \"playlists\" в JSON")
            else -> throw IllegalArgumentException("Ожидался объект или массив JSON")
        }
        val playlists = array.mapNotNull { el -> (el as? JsonObject)?.let { parsePlaylist(it) } }
        require(playlists.isNotEmpty()) { "В JSON нет ни одного плейлиста" }
        playlists
    }

    /** Убирает markdown-ограждения и текст вокруг JSON. */
    internal fun extractJson(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            s = s.substringBeforeLast("```")
        }
        val firstObj = s.indexOf('{')
        val firstArr = s.indexOf('[')
        val start = listOf(firstObj, firstArr).filter { it >= 0 }.minOrNull() ?: return s.trim()
        val end = maxOf(s.lastIndexOf('}'), s.lastIndexOf(']'))
        return if (end > start) s.substring(start, end + 1).trim() else s.trim()
    }

    private fun JsonObject.str(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
        (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private fun parsePlaylist(obj: JsonObject): ParsedPlaylist? {
        val name = obj.str("name", "title", "название", "имя") ?: return null
        val vibe = Vibe.parse(obj.str("vibe", "mood", "вайб", "настроение"))
        val color = parseColor(obj.str("color", "colour", "цвет"))
        val description = obj.str("description", "desc", "описание", "subtitle") ?: ""
        val tracksEl = obj["tracks"] ?: obj["songs"] ?: obj["items"] ?: obj["треки"] ?: obj["ids"] ?: obj["track_ids"]
        val refs = (tracksEl as? JsonArray)?.mapNotNull { parseTrackRef(it) } ?: emptyList()
        return ParsedPlaylist(name.trim(), vibe, color, description.trim(), refs)
    }

    private fun parseTrackRef(el: JsonElement): TrackRef? = when (el) {
        is JsonNull -> null
        is JsonPrimitive -> {
            val asLong = el.longOrNull ?: el.contentOrNull?.trim()?.toLongOrNull()
            if (asLong != null) TrackRef.ById(asLong)
            else el.contentOrNull?.takeIf { it.isNotBlank() }?.let { text ->
                val (artist, title) = splitArtistTitle(text)
                TrackRef.ByText(title, artist, text)
            }
        }
        is JsonObject -> {
            val id = el.str("id", "track_id", "trackId")?.toLongOrNull()
                ?: (el["id"] as? JsonPrimitive)?.longOrNull
            if (id != null) TrackRef.ById(id)
            else {
                val title = el.str("title", "name", "track", "song", "название")
                val artist = el.str("artist", "исполнитель", "author")
                if (title == null && artist == null) null
                else TrackRef.ByText(title, artist, listOfNotNull(artist, title).joinToString(" — "))
            }
        }
        is JsonArray -> null
    }

    internal fun splitArtistTitle(text: String): Pair<String?, String?> {
        val separators = listOf(" — ", " – ", " - ", " | ", ": ")
        for (sep in separators) {
            val idx = text.indexOf(sep)
            if (idx > 0) return text.substring(0, idx).trim() to text.substring(idx + sep.length).trim()
        }
        return null to text.trim()
    }

    /** "#RGB", "#RRGGBB", "#AARRGGBB", "0xFFRRGGBB", "RRGGBB" → ARGB Long */
    internal fun parseColor(raw: String?): Long? {
        if (raw == null) return null
        var s = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
        return when (s.length) {
            6 -> s.toLongOrNull(16)?.or(0xFF000000L)
            8 -> s.toLongOrNull(16)
            else -> null
        }
    }

    // ---------- Сопоставление с библиотекой ----------

    fun match(parsed: List<ParsedPlaylist>, library: List<AiTrack>): List<MatchedPlaylist> {
        val byId = library.associateBy { it.id }
        val byExact = HashMap<String, MutableList<AiTrack>>()
        val byTitle = HashMap<String, MutableList<AiTrack>>()
        val byLoose = HashMap<String, MutableList<AiTrack>>()
        val byLooseTitle = HashMap<String, MutableList<AiTrack>>()
        for (t in library) {
            val nt = normalize(t.title)
            val na = normalize(t.artist)
            byExact.getOrPut("$na|$nt") { mutableListOf() }.add(t)
            byTitle.getOrPut(nt) { mutableListOf() }.add(t)
            val lt = loose(t.title)
            val la = loose(t.artist)
            byLoose.getOrPut("$la|$lt") { mutableListOf() }.add(t)
            byLooseTitle.getOrPut(lt) { mutableListOf() }.add(t)
        }

        fun resolve(ref: TrackRef): AiTrack? = when (ref) {
            is TrackRef.ById -> byId[ref.id]
            is TrackRef.ByText -> {
                val title = ref.title?.let { normalize(it) }
                val artist = ref.artist?.let { normalize(it) }
                var found: AiTrack? = null
                if (title != null && artist != null) {
                    found = byExact["$artist|$title"]?.firstOrNull()
                        ?: byExact["$title|$artist"]?.firstOrNull() // ИИ мог перепутать порядок
                }
                if (found == null && title != null) found = byTitle[title]?.singleOrNull()
                if (found == null && title != null && artist != null) {
                    found = byLoose["${loose(ref.artist!!)}|${loose(ref.title!!)}"]?.firstOrNull()
                }
                if (found == null && title != null) found = byLooseTitle[loose(ref.title!!)]?.singleOrNull()
                if (found == null) {
                    val rawNorm = normalize(ref.raw)
                    found = library.firstOrNull { t ->
                        rawNorm.contains(normalize(t.title)) && (t.artist.isBlank() || rawNorm.contains(normalize(t.artist)))
                    }
                }
                found
            }
        }

        return parsed.map { p ->
            val ids = LinkedHashSet<Long>()
            val unmatched = ArrayList<String>()
            for (ref in p.tracks) {
                val t = resolve(ref)
                if (t != null) ids += t.id
                else unmatched += when (ref) {
                    is TrackRef.ById -> "id ${ref.id}"
                    is TrackRef.ByText -> ref.raw
                }
            }
            val playlist = Playlist(
                name = p.name,
                vibe = p.vibe,
                color = p.color ?: p.vibe.defaultColor,
                description = p.description,
                trackIds = ids.toList(),
            )
            MatchedPlaylist(p, playlist, unmatched)
        }
    }

    private val nonWord = Regex("[^\\p{L}\\p{N} ]+")
    private val spaces = Regex("\\s+")
    private val bracketed = Regex("[\\(\\[\\{][^)\\]}]*[\\)\\]}]")
    private val feat = Regex("\\b(feat|ft|featuring|при уч|с уч)\\.?\\b.*$", RegexOption.IGNORE_CASE)

    internal fun normalize(s: String): String = s.lowercase()
        .replace('ё', 'е')
        .replace(nonWord, " ")
        .replace(spaces, " ")
        .trim()

    /** Более «грубая» нормализация: без скобок, feat., remix-хвостов. */
    internal fun loose(s: String): String = normalize(
        s.replace(bracketed, " ").replace(feat, " ")
    )
}
