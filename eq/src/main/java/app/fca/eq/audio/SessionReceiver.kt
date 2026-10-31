package app.fca.eq.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import app.fca.eq.data.Store

/**
 * Ловит сообщения плееров «у меня открыта аудиосессия» — так эквалайзер
 * цепляется к Spotify, YouTube Music, VK Музыке, Яндекс Музыке, AIMP и т.п.
 */
class SessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR)
        if (session <= 0) return
        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                SessionRegistry.add(session, intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME))
                Store.init(context)
                if (Store.state.value.enabled) EqService.start(context)
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> SessionRegistry.remove(session)
        }
    }
}
