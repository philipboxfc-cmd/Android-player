package app.fca.eq.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.fca.eq.MainActivity
import app.fca.eq.R
import app.fca.eq.data.AppState
import app.fca.eq.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Фоновый сервис глобального эквалайзера. Держит цепочки эффектов на:
 *  - сессии 0 (весь звук устройства) в режиме «Весь звук»;
 *  - сессиях, о которых сообщили плееры, в режиме «Плееры».
 */
class EqService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val chains = mutableMapOf<Int, EffectChain>()
    private val failed = mutableSetOf<Int>()
    private val receiver = SessionReceiver()
    private var lastMode: Boolean? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(Store.state.value),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        // Дублируем манифестный приёмник: часть прошивок не доставляет такие broadcast'ы в манифест.
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        EqStatus.running.value = true

        scope.launch {
            combine(Store.state, SessionRegistry.sessions) { s, sessions -> s to sessions }
                .collect { (state, sessions) -> sync(state, sessions) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Store.update { it.copy(enabled = false) }
        }
        return START_STICKY
    }

    private fun sync(state: AppState, sessions: Map<Int, String>) {
        if (!state.enabled) {
            stopSelf()
            return
        }
        if (lastMode != state.globalMode) {
            // Сменили режим — пересоздаём всё и даём сессиям ещё один шанс.
            releaseAll()
            failed.clear()
            lastMode = state.globalMode
        }
        val wanted: Map<Int, String> = if (state.globalMode) mapOf(0 to "*") else sessions

        (chains.keys - wanted.keys).forEach { chains.remove(it)?.release() }
        failed.retainAll(wanted.keys)

        for (id in wanted.keys) {
            if (id in chains || id in failed) continue
            val chain = EffectChain(id)
            if (chain.isWorking) chains[id] = chain else {
                chain.release()
                failed += id
                Log.w(TAG, "Не удалось прицепиться к сессии $id")
            }
        }
        chains.values.forEach { it.apply(true, state.eq) }

        EqStatus.active.value = wanted.map { (id, pkg) ->
            val c = chains[id]
            ActiveSession(id, pkg, working = c != null, dynamics = c?.usesDynamics == true)
        }
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun releaseAll() {
        chains.values.forEach { it.release() }
        chains.clear()
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(receiver) }
        releaseAll()
        EqStatus.running.value = false
        EqStatus.active.value = emptyList()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Эквалайзер", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(state: AppState): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, EqService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (state.globalMode) "Весь звук · ${state.eqPresetName}"
        else "Плееров: ${chains.size} · ${state.eqPresetName}"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_eq)
            .setContentTitle("Эквалайзер включён")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Выключить", stop)
            .build()
    }

    companion object {
        private const val TAG = "EqService"
        private const val CHANNEL = "eq"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_STOP = "app.fca.eq.STOP"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, EqService::class.java))
            } catch (t: Throwable) {
                // Android 12+ запрещает запуск из фона — сервис стартует при открытии приложения.
                Log.w(TAG, "Не удалось запустить сервис", t)
            }
        }
    }
}
