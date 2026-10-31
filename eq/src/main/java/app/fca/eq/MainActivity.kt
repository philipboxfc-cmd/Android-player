package app.fca.eq

import android.Manifest
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import app.fca.eq.audio.EqService
import app.fca.eq.audio.SessionRegistry
import app.fca.eq.data.Store
import app.fca.eq.player.PlayerController
import app.fca.eq.ui.EqRoot

class MainActivity : ComponentActivity() {

    private lateinit var player: PlayerController
    private val tab = mutableIntStateOf(TAB_EQ)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        player = PlayerController(applicationContext)
        handleIntent(intent)
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { EqRoot(player = player, tab = tab) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (Store.state.value.enabled) EqService.start(this)
        player.connect()
    }

    override fun onStop() {
        player.release()
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                val index = player.addFiles(listOf(uri))
                tab.intValue = TAB_PLAYER
                player.playAt(index)
            }
            AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL -> {
                val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR)
                SessionRegistry.add(session, intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: callingPackage)
                tab.intValue = TAB_EQ
            }
            else -> if (intent.hasExtra(EXTRA_TAB)) tab.intValue = intent.getIntExtra(EXTRA_TAB, TAB_EQ)
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_EQ = 0
        const val TAB_FX = 1
        const val TAB_PLAYER = 2
    }
}
