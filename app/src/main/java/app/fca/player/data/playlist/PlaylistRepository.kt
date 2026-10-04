package app.fca.player.data.playlist

import android.content.Context
import app.fca.player.data.model.Playlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Плейлисты хранятся в одном JSON-файле в приватной папке приложения. */
class PlaylistRepository(context: Context) {

    @Serializable
    private data class Store(val playlists: List<Playlist> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val file = File(context.filesDir, "playlists.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        scope.launch { load() }
    }

    private suspend fun load() = mutex.withLock {
        if (!file.exists()) return@withLock
        runCatching { json.decodeFromString<Store>(file.readText()) }
            .onSuccess { _playlists.value = it.playlists.sortedByDescending { p -> p.updatedAt } }
    }

    private suspend fun persist(list: List<Playlist>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sorted = list.sortedByDescending { it.updatedAt }
            _playlists.value = sorted
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(Store(sorted)))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    fun byId(id: String): Playlist? = _playlists.value.firstOrNull { it.id == id }

    suspend fun upsert(playlist: Playlist) {
        val now = System.currentTimeMillis()
        val updated = playlist.copy(updatedAt = now)
        val current = _playlists.value
        val list = if (current.any { it.id == playlist.id }) {
            current.map { if (it.id == playlist.id) updated else it }
        } else {
            current + updated
        }
        persist(list)
    }

    suspend fun upsertAll(playlists: List<Playlist>) {
        val now = System.currentTimeMillis()
        val incomingIds = playlists.map { it.id }.toSet()
        val list = _playlists.value.filterNot { it.id in incomingIds } + playlists.map { it.copy(updatedAt = now) }
        persist(list)
    }

    suspend fun delete(id: String) {
        persist(_playlists.value.filterNot { it.id == id })
    }

    suspend fun addTracks(playlistId: String, trackIds: List<Long>) {
        val p = byId(playlistId) ?: return
        val merged = (p.trackIds + trackIds).distinct()
        upsert(p.copy(trackIds = merged))
    }

    suspend fun removeTrack(playlistId: String, trackId: Long) {
        val p = byId(playlistId) ?: return
        upsert(p.copy(trackIds = p.trackIds.filterNot { it == trackId }))
    }

    suspend fun moveTrack(playlistId: String, from: Int, to: Int) {
        val p = byId(playlistId) ?: return
        if (from !in p.trackIds.indices || to !in p.trackIds.indices || from == to) return
        val ids = p.trackIds.toMutableList()
        val item = ids.removeAt(from)
        ids.add(to, item)
        upsert(p.copy(trackIds = ids))
    }
}
