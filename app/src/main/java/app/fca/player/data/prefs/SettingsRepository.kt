package app.fca.player.data.prefs

import android.content.Context
import android.content.SharedPreferences
import app.fca.player.data.model.EqSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Простые настройки на SharedPreferences: эквалайзер, последний плейлист и т.п. */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("fca_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _eq = MutableStateFlow(loadEq())
    val eq: StateFlow<EqSettings> = _eq.asStateFlow()

    private fun loadEq(): EqSettings = prefs.getString(KEY_EQ, null)
        ?.let { runCatching { json.decodeFromString<EqSettings>(it) }.getOrNull() }
        ?: EqSettings()

    fun saveEq(settings: EqSettings) {
        _eq.value = settings
        prefs.edit().putString(KEY_EQ, json.encodeToString(settings)).apply()
    }

    var lastPlaylistId: String?
        get() = prefs.getString(KEY_LAST_PLAYLIST, null)
        set(value) = prefs.edit().putString(KEY_LAST_PLAYLIST, value).apply()

    var trackSort: String
        get() = prefs.getString(KEY_TRACK_SORT, "TITLE") ?: "TITLE"
        set(value) = prefs.edit().putString(KEY_TRACK_SORT, value).apply()

    private companion object {
        const val KEY_EQ = "eq_settings"
        const val KEY_LAST_PLAYLIST = "last_playlist"
        const val KEY_TRACK_SORT = "track_sort"
    }
}
