package app.fca.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.fca.player.data.media.LibraryRepository
import app.fca.player.data.model.Track
import app.fca.player.data.prefs.SettingsRepository
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Элемент очереди (метаданные берутся из MediaItem, чтобы работать даже если библиотека ещё грузится). */
data class QueueEntry(
    val index: Int,
    val mediaId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: Uri?,
)

data class PlayerUiState(
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val current: QueueEntry? = null,
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playlistId: String? = null,
    /** Доминирующий цвет обложки текущего трека (ARGB) */
    val artworkColor: Int? = null,
) {
    val hasMedia: Boolean get() = current != null
}

/**
 * Единое подключение UI к сервису воспроизведения через MediaController.
 * Живёт на уровне Application, поэтому состояние переживает пересоздание Activity.
 */
class PlayerConnection(
    private val context: Context,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
) {
    private data class RawState(
        val queue: List<QueueEntry> = emptyList(),
        val currentIndex: Int = -1,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val durationMs: Long = 0L,
        val shuffle: Boolean = false,
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
        val playlistId: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var tickerJob: Job? = null
    private var paletteJob: Job? = null
    private var sleepJob: Job? = null

    private val _raw = MutableStateFlow(RawState(playlistId = settings.lastPlaylistId))
    private val _artworkColor = MutableStateFlow<Int?>(null)

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _sleepEndAt = MutableStateFlow<Long?>(null)
    val sleepEndAt: StateFlow<Long?> = _sleepEndAt.asStateFlow()

    val state: StateFlow<PlayerUiState> = combine(_raw, library.tracks, _artworkColor) { raw, _, color ->
        val current = raw.queue.getOrNull(raw.currentIndex)
        PlayerUiState(
            queue = raw.queue,
            currentIndex = raw.currentIndex,
            current = current,
            currentTrack = current?.let { library.trackById(it.mediaId) },
            isPlaying = raw.isPlaying,
            isBuffering = raw.isBuffering,
            durationMs = raw.durationMs,
            shuffle = raw.shuffle,
            repeatMode = raw.repeatMode,
            playlistId = raw.playlistId,
            artworkColor = color,
        )
    }.stateIn(scope, SharingStarted.Eagerly, PlayerUiState())

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val rebuildQueue = events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
            )
            sync(player, rebuildQueue)
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                _position.value = player.currentPosition
            }
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = runCatching { future.get() }.getOrNull()
            if (c == null) {
                controllerFuture = null
                return@addListener
            }
            controller = c
            c.addListener(listener)
            sync(c, rebuildQueue = true)
            startTicker()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun sync(player: Player, rebuildQueue: Boolean) {
        val previousMediaId = _raw.value.queue.getOrNull(_raw.value.currentIndex)?.mediaId
        _raw.update { old ->
            val queue = if (rebuildQueue) buildQueue(player) else old.queue
            old.copy(
                queue = queue,
                currentIndex = player.currentMediaItemIndex.takeIf { player.mediaItemCount > 0 } ?: -1,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: old.durationMs,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
            )
        }
        val nowEntry = _raw.value.queue.getOrNull(_raw.value.currentIndex)
        if (nowEntry?.mediaId != previousMediaId || (nowEntry != null && _artworkColor.value == null)) {
            updateArtworkColor(nowEntry)
        }
    }

    private fun buildQueue(player: Player): List<QueueEntry> {
        val count = player.mediaItemCount
        return (0 until count).map { i ->
            val item = player.getMediaItemAt(i)
            val md = item.mediaMetadata
            QueueEntry(
                index = i,
                mediaId = item.mediaId.toLongOrNull() ?: -1L,
                title = md.title?.toString() ?: "Без названия",
                artist = md.artist?.toString() ?: "",
                album = md.albumTitle?.toString() ?: "",
                artworkUri = md.artworkUri,
            )
        }
    }

    private fun updateArtworkColor(entry: QueueEntry?) {
        paletteJob?.cancel()
        if (entry?.artworkUri == null) {
            _artworkColor.value = null
            return
        }
        paletteJob = scope.launch {
            _artworkColor.value = ArtworkPalette.colorFor(context, entry.artworkUri)
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val c = controller
                if (c != null) {
                    _position.value = c.currentPosition.coerceAtLeast(0L)
                    val d = c.duration
                    if (d != C.TIME_UNSET && d > 0 && d != _raw.value.durationMs) {
                        _raw.update { it.copy(durationMs = d) }
                    }
                }
                delay(if (c?.isPlaying == true) 250L else 1000L)
            }
        }
    }

    // ---------- Управление ----------

    /** Запускает список треков с позиции [startIndex]. [playlistId] — для подсветки цветом плейлиста. */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0, playlistId: String? = null, shuffle: Boolean = false) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toMediaItem() }
        val start = if (shuffle) tracks.indices.random() else startIndex.coerceIn(0, items.lastIndex)
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(items, start, C.TIME_UNSET)
        c.prepare()
        c.play()
        _raw.update { it.copy(playlistId = playlistId) }
        settings.lastPlaylistId = playlistId
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() else it.seekToNext() }
    fun previous() = controller?.seekToPrevious()
    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _position.value = positionMs
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun addToQueue(tracks: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playTracks(tracks)
            return
        }
        c.addMediaItems(tracks.map { it.toMediaItem() })
    }

    fun playNext(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playTracks(listOf(track))
            return
        }
        c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
    }

    fun skipToQueueIndex(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun clearQueue() {
        controller?.clearMediaItems()
        _raw.update { it.copy(playlistId = null) }
    }

    /** Таймер сна: через [minutes] минут воспроизведение остановится. null — выключить. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _sleepEndAt.value = null
            return
        }
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        _sleepEndAt.value = endAt
        sleepJob = scope.launch {
            delay(endAt - System.currentTimeMillis())
            controller?.pause()
            _sleepEndAt.value = null
        }
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()
}
