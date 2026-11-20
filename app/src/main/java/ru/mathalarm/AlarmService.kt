package ru.mathalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Служба переднего плана: играет звук и вибрирует, пока AlarmActivity не вызовет stop()
 * (то есть пока не решены все примеры). Полноэкранное уведомление открывает AlarmActivity
 * поверх экрана блокировки.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Служба должна сразу стать foreground, иначе система её завершит.
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } catch (e: Exception) {
            AlarmStore.setRinging(this, -1)
            stopSelf()
            return START_NOT_STICKY
        }

        // intent == null — система перезапустила службу после убийства процесса.
        val id = intent?.getIntExtra(EXTRA_ID, -1)?.takeIf { it != -1 } ?: AlarmStore.ringingId(this)
        if (id == -1) {
            stopSelf()
            return START_NOT_STICKY
        }
        AlarmStore.setRinging(this, id)

        if (player == null) {
            val sound = if (intent != null && intent.hasExtra(EXTRA_SOUND)) {
                intent.getStringExtra(EXTRA_SOUND)
            } else {
                AlarmStore.load(this).find { it.id == id }?.soundUri
            }
            acquireWakeLock()
            ensureAudible()
            startSound(sound)
            startVibration()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        AlarmStore.setRinging(this, -1)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "Будильник", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Звонок будильника"
            setSound(null, null) // звук играет сама служба на потоке будильника
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)

        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Будильник!")
            .setContentText("Реши $PROBLEMS_TO_SOLVE примеров, чтобы выключить")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mathalarm:ring").apply {
            acquire(60 * 60 * 1000L)
        }
    }

    /** Если громкость будильника выставлена в ноль — поднимаем, иначе звонок бессмысленен. */
    private fun ensureAudible() {
        try {
            val am = getSystemService(AudioManager::class.java)
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.7).toInt().coerceAtLeast(1), 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun startSound(uriString: String?) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        fun tryPlay(uri: Uri?): MediaPlayer? {
            if (uri == null) return null
            val p = MediaPlayer()
            return try {
                p.setAudioAttributes(attrs)
                p.setDataSource(this, uri)
                p.isLooping = true
                p.prepare()
                p.start()
                p
            } catch (e: Exception) {
                p.release()
                null
            }
        }

        player = tryPlay(uriString?.let { Uri.parse(it) })
            ?: tryPlay(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            ?: tryPlay(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            ?: tryPlay(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator = v
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0), attrs)
    }

    companion object {
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_SOUND = "alarm_sound"
        private const val CHANNEL = "alarm_ring"
        private const val NOTIF_ID = 4242

        /** Запускает звонок. Метка «звонит» ставится заранее, чтобы AlarmActivity не закрылась по гонке. */
        fun start(c: Context, id: Int, soundUri: String?) {
            AlarmStore.setRinging(c, id)
            try {
                ContextCompat.startForegroundService(
                    c,
                    Intent(c, AlarmService::class.java)
                        .putExtra(EXTRA_ID, id)
                        .putExtra(EXTRA_SOUND, soundUri)
                )
            } catch (e: Exception) {
                AlarmStore.setRinging(c, -1)
            }
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, AlarmService::class.java))
            AlarmStore.setRinging(c, -1)
        }

        fun isRinging(c: Context): Boolean = AlarmStore.ringingId(c) != -1
    }
}
