package app.fca.eq.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

/** Всё состояние приложения в одном JSON в SharedPreferences. */
object Store {
    private const val KEY = "state"
    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("eq_state", Context.MODE_PRIVATE)
        _state.value = prefs.getString(KEY, null)
            ?.let { runCatching { json.decodeFromString(AppState.serializer(), it) }.getOrNull() }
            ?.let { s -> if (s.eq.bands.size != BAND_FREQS.size) s.copy(eq = s.eq.copy(bands = List(BAND_FREQS.size) { 0f })) else s }
            ?: AppState()
    }

    fun update(block: (AppState) -> AppState) {
        _state.update(block)
        prefs.edit().putString(KEY, json.encodeToString(AppState.serializer(), _state.value)).apply()
    }

    fun updateEq(block: (EqConfig) -> EqConfig) =
        update { it.copy(eq = block(it.eq), eqPresetName = "Своё") }

    fun updateFx(block: (PlayerFx) -> PlayerFx) =
        update { it.copy(fx = block(it.fx), fxPresetName = "Своё") }
}
