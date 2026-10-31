package app.fca.eq.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.fca.eq.data.QueueItem
import app.fca.eq.data.Store
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUiState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val currentUri: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
)

/** Связь экрана с PlayerService через MediaController. */
class PlayerController(private val context: Context) {

    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private var pending: (MediaController.() -> Unit)? = null

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh()
            })
            pending?.invoke(c)
            pending = null
            refresh()
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        controller?.release()
        controller = null
        _state.value = PlayerUiState()
    }

    fun refresh() {
        val c = controller ?: return
        _state.value = PlayerUiState(
            connected = true,
            isPlaying = c.isPlaying,
            title = c.mediaMetadata.title?.toString().orEmpty(),
            currentUri = c.currentMediaItem?.mediaId,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            repeat = c.repeatMode,
            shuffle = c.shuffleModeEnabled,
        )
    }

    private fun withController(block: MediaController.() -> Unit) {
        val c = controller
        if (c != null) block(c) else {
            pending = block
            connect()
        }
    }

    fun playAt(index: Int) = withController {
        val queue = Store.state.value.queue
        if (queue.isEmpty()) return@withController
        setMediaItems(queue.map { it.toMediaItem() }, index.coerceIn(0, queue.lastIndex), 0)
        prepare()
        play()
    }

    fun togglePlay() = withController {
        if (mediaItemCount == 0) {
            playAt(0)
        } else if (isPlaying) pause() else {
            if (playbackState == Player.STATE_IDLE) prepare()
            play()
        }
    }

    fun next() = withController { seekToNextMediaItem() }
    fun previous() = withController { seekToPrevious() }
    fun seekTo(ms: Long) = withController { seekTo(ms) }
    fun toggleShuffle() = withController { shuffleModeEnabled = !shuffleModeEnabled }
    fun cycleRepeat() = withController {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Добавляет файлы в очередь. Возвращает индекс первого добавленного. */
    fun addFiles(uris: List<Uri>): Int {
        val items = uris.map { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            QueueItem(uri.toString(), displayName(uri))
        }
        var first = 0
        Store.update { s ->
            val existing = s.queue.map { it.uri }.toSet()
            val fresh = items.filter { it.uri !in existing }
            first = items.firstOrNull()?.let { item -> (s.queue + fresh).indexOfFirst { it.uri == item.uri } } ?: 0
            s.copy(queue = s.queue + fresh)
        }
        return first.coerceAtLeast(0)
    }

    fun remove(item: QueueItem) {
        Store.update { s -> s.copy(queue = s.queue - item) }
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(item.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        controller?.let { c ->
            for (i in 0 until c.mediaItemCount) {
                if (c.getMediaItemAt(i).mediaId == item.uri) {
                    c.removeMediaItem(i); break
                }
            }
        }
    }

    fun clear() {
        Store.update { it.copy(queue = emptyList()) }
        controller?.clearMediaItems()
    }

    private fun displayName(uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "Трек"
        return name.substringBeforeLast('.', name)
    }

    private fun QueueItem.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(uri)
        .setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()
}
