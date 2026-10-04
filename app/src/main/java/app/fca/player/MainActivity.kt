package app.fca.player

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.fca.player.di.LocalAppContainer
import app.fca.player.ui.FcaRoot

class MainActivity : ComponentActivity() {

    /** Интент из уведомления: сразу открыть экран «Сейчас играет». */
    private var openNowPlayingRequest by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as FcaApp).container
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true) openNowPlayingRequest++

        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                FcaRoot(openNowPlayingRequest = openNowPlayingRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false)) openNowPlayingRequest++
    }

    override fun onStart() {
        super.onStart()
        (application as FcaApp).container.player.connect()
    }

    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "open_now_playing"
    }
}
