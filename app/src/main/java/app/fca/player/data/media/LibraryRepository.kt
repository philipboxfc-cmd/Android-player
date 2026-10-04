package app.fca.player.data.media

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import app.fca.player.data.model.Album
import app.fca.player.data.model.Artist
import app.fca.player.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Единый источник правды о библиотеке: треки, альбомы, исполнители.
 * Автоматически перечитывает MediaStore при изменениях.
 */
class LibraryRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scanner = MediaStoreScanner(context)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _loadedOnce = MutableStateFlow(false)
    val loadedOnce: StateFlow<Boolean> = _loadedOnce.asStateFlow()

    val albums: StateFlow<List<Album>> = tracks.map { list ->
        list.groupBy { it.albumId }.map { (albumId, albumTracks) ->
            val first = albumTracks.first()
            // Исполнитель альбома — самый частый среди треков
            val artist = albumTracks.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: first.artist
            val artistId = albumTracks.firstOrNull { it.artist == artist }?.artistId ?: first.artistId
            Album(
                id = albumId,
                title = first.album,
                artist = artist,
                artistId = artistId,
                year = albumTracks.maxOf { it.year },
                tracks = albumTracks.sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() })),
            )
        }.sortedBy { it.title.lowercase() }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<Artist>> = albums.map { albumList ->
        val allTracks = _tracks.value
        allTracks.groupBy { it.artistId }.map { (artistId, artistTracks) ->
            val name = artistTracks.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: artistTracks.first().artist
            val artistAlbums = albumList.filter { album -> album.tracks.any { it.artistId == artistId } }
                .sortedByDescending { it.year }
            Artist(
                id = artistId,
                name = name,
                albums = artistAlbums,
                tracks = artistTracks.sortedBy { it.title.lowercase() },
            )
        }.sortedBy { it.name.lowercase() }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val byId: StateFlow<Map<Long, Track>> = tracks.map { list -> list.associateBy { it.id } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private var refreshJob: Job? = null
    private var observerRegistered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scheduleRefresh(debounceMs = 1500)
        }
    }

    fun trackById(id: Long): Track? = byId.value[id]
    fun tracksByIds(ids: List<Long>): List<Track> = ids.mapNotNull { byId.value[it] }
    fun albumById(id: Long): Album? = albums.value.firstOrNull { it.id == id }
    fun artistById(id: Long): Artist? = artists.value.firstOrNull { it.id == id }

    /** Вызывается после выдачи разрешения на чтение музыки. */
    fun onPermissionGranted() {
        _hasPermission.value = true
        if (!observerRegistered) {
            context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
            observerRegistered = true
        }
        scheduleRefresh(debounceMs = 0)
    }

    fun refresh() = scheduleRefresh(debounceMs = 0)

    private fun scheduleRefresh(debounceMs: Long) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            _isLoading.value = true
            try {
                _tracks.value = scanner.scan()
            } catch (_: SecurityException) {
                _hasPermission.value = false
            } finally {
                _isLoading.value = false
                _loadedOnce.value = true
            }
        }
    }
}
