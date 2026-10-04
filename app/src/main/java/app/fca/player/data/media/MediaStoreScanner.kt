package app.fca.player.data.media

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import app.fca.player.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Читает все музыкальные файлы из MediaStore вместе с метаданными. */
class MediaStoreScanner(private val context: Context) {

    suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 15000"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val result = ArrayList<Track>()
        resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (c.moveToNext()) {
                val rawTitle = c.getString(titleCol)
                val displayName = c.getString(nameCol) ?: ""
                val title = rawTitle?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.')
                val artist = c.getString(artistCol).cleanUnknown("Неизвестный исполнитель")
                val album = c.getString(albumCol).cleanUnknown("Неизвестный альбом")
                // В MediaStore номер трека может быть вида 1005 (диск 1, трек 5)
                val trackNo = c.getInt(trackCol).let { if (it >= 1000) it % 1000 else it }
                result += Track(
                    id = c.getLong(idCol),
                    title = title,
                    artist = artist,
                    artistId = c.getLong(artistIdCol),
                    album = album,
                    albumId = c.getLong(albumIdCol),
                    durationMs = c.getLong(durationCol),
                    trackNumber = trackNo,
                    year = c.getInt(yearCol),
                    dateAdded = c.getLong(dateCol),
                    path = c.getString(dataCol) ?: "",
                )
            }
        }
        result
    }

    private fun String?.cleanUnknown(fallback: String): String {
        val s = this?.trim()
        return if (s.isNullOrEmpty() || s == MediaStore.UNKNOWN_STRING || s.equals("unknown", true)) fallback else s
    }
}
