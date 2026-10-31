package app.fca.eq.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Аудиосессии, о которых сообщили плееры (sessionId → пакет). */
object SessionRegistry {
    const val OWN_PLAYER = "@player"

    private val _sessions = MutableStateFlow<Map<Int, String>>(emptyMap())
    val sessions: StateFlow<Map<Int, String>> = _sessions.asStateFlow()

    fun add(sessionId: Int, pkg: String?) {
        if (sessionId <= 0) return
        _sessions.update { it + (sessionId to (pkg ?: "?")) }
    }

    fun remove(sessionId: Int) {
        _sessions.update { it - sessionId }
    }
}

/** Что сейчас реально работает — для экрана. */
data class ActiveSession(val sessionId: Int, val pkg: String, val working: Boolean, val dynamics: Boolean)

object EqStatus {
    val running = MutableStateFlow(false)
    val active = MutableStateFlow<List<ActiveSession>>(emptyList())
}
