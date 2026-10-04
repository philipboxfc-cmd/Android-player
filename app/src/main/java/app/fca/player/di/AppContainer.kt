package app.fca.player.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import app.fca.player.data.media.LibraryRepository
import app.fca.player.data.playlist.PlaylistRepository
import app.fca.player.data.prefs.SettingsRepository
import app.fca.player.playback.PlayerConnection

/** Ручной DI: один контейнер на процесс, создаётся в Application. */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context)
    val library = LibraryRepository(context)
    val playlists = PlaylistRepository(context)
    val player = PlayerConnection(context, library, settings)
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer не предоставлен")
}
