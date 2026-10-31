package app.fca.eq.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.fca.eq.MainActivity
import app.fca.eq.audio.EqService
import app.fca.eq.audio.SessionRegistry
import app.fca.eq.data.Store
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Встроенный плеер: ExoPlayer + наш DSP (FxProcessor) + питч/скорость.
 * Его аудиосессия регистрируется в SessionRegistry, и EqService вешает на неё эквалайзер,
 * как на любой другой плеер.
 */
@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {

    private var session: MediaSession? = null
    private val fx = FxProcessor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var audioSessionId = 0

    override fun onCreate() {
        super.onCreate()
        Store.init(this)

        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(fx))
                .build()
        }

        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        audioSessionId = (getSystemService(AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
        player.setAudioSessionId(audioSessionId)
        SessionRegistry.add(audioSessionId, SessionRegistry.OWN_PLAYER)
        if (Store.state.value.enabled) EqService.start(this)

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(id: Int) {
                if (id == audioSessionId || id == C.AUDIO_SESSION_ID_UNSET) return
                SessionRegistry.remove(audioSessionId)
                audioSessionId = id
                SessionRegistry.add(id, SessionRegistry.OWN_PLAYER)
            }
        })

        scope.launch {
            Store.state.map { it.fx }.distinctUntilChanged().collect { f ->
                fx.fx = f
                val params = PlaybackParameters(f.speed.coerceIn(0.25f, 3f), f.pitchFactor.coerceIn(0.25f, 4f))
                if (player.playbackParameters != params) player.playbackParameters = params
            }
        }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_PLAYER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(Callback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        SessionRegistry.remove(audioSessionId)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** URI не переживает передачу через MediaController — восстанавливаем из mediaId. */
    private class Callback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { item ->
                if (item.localConfiguration != null) item
                else item.buildUpon().setUri(Uri.parse(item.mediaId)).build()
            }.toMutableList()
        )
    }
}
