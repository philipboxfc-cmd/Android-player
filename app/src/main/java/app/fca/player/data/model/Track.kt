package app.fca.player.data.model

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

private val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")

fun albumArtUri(albumId: Long): Uri = ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)

/** Трек из MediaStore. Все метаданные (название, исполнитель, альбом, обложка) берутся из тегов файла. */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val dateAdded: Long,
    val path: String,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val artworkUri: Uri get() = albumArtUri(albumId)
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val year: Int,
    val tracks: List<Track>,
) {
    val artworkUri: Uri get() = albumArtUri(id)
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

data class Artist(
    val id: Long,
    val name: String,
    val albums: List<Album>,
    val tracks: List<Track>,
) {
    /** Обложка первого альбома — для аватарки исполнителя. */
    val artworkUri: Uri? get() = albums.firstOrNull()?.artworkUri
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatDurationLong(ms: Long): String {
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "$h ч $m мин"
        h > 0 -> "$h ч"
        else -> "$m мин"
    }
}

fun tracksWord(n: Int): String {
    val r10 = n % 10
    val r100 = n % 100
    return when {
        r100 in 11..19 -> "$n треков"
        r10 == 1 -> "$n трек"
        r10 in 2..4 -> "$n трека"
        else -> "$n треков"
    }
}
