package com.romanpanin.advancedalarm.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.romanpanin.advancedalarm.App
import com.romanpanin.advancedalarm.R
import com.romanpanin.advancedalarm.alarm.AlarmScheduler
import com.romanpanin.advancedalarm.alarm.SnoozePolicy
import com.romanpanin.advancedalarm.data.AlarmStore
import com.romanpanin.advancedalarm.data.Settings
import com.romanpanin.advancedalarm.lock.AllowedPackages
import com.romanpanin.advancedalarm.lock.LockController
import com.romanpanin.advancedalarm.util.TorchController
import kotlin.math.max

/**
 * Foreground-сервис будильника: крутит звук, держит блокировку и переводит
 * её в фазу с задачами по истечении заданного времени.
 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LockState.init(this)
        val settings = Settings.of(this)

        if (intent?.action == ACTION_STOP) {
            stopEverything()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_STOP_SOUND) {
            // Гасим только звонок: блокировка (если она была) остаётся.
            stopSound()
            stopVibration()
            val state = LockState.current()
            if (!state.ringOnly && state.phase == LockState.Phase.RINGING) {
                LockState.setPhase(LockState.Phase.LOCKED)
                updateNotification()
            }
            if (!LockState.isActive()) stopSelf()
            return START_STICKY
        }

        if (intent?.action == ACTION_SNOOZE) {
            handleSnooze(intent.getStringExtra(EXTRA_ALARM_ID).orEmpty())
            return START_NOT_STICKY
        }

        val isTest = intent?.getBooleanExtra(EXTRA_TEST, false) == true
        val alarmId = intent?.getStringExtra(EXTRA_ALARM_ID).orEmpty()
        val shouldLock = intent?.getBooleanExtra(EXTRA_SHOULD_LOCK, false) ?: false
        val durationMinutes = intent?.getIntExtra(EXTRA_DURATION_MINUTES, 0)
            ?.takeIf { it > 0 } ?: settings.lockDurationMinutes
        val alarm = AlarmStore(this).get(alarmId)

        if (!LockState.isActive()) {
            LockState.start(
                context = this,
                alarmId = alarmId.ifEmpty { "test" },
                label = if (isTest) getString(R.string.test_alarm_label) else alarm?.label.orEmpty(),
                ringOnly = !shouldLock,
                durationMinutes = durationMinutes,
                taskCount = settings.quizTaskCount
            )
        }

        val state = LockState.current()
        startForeground(NOTIFICATION_ID, buildNotification(state))

        // Звук включаем, только если ещё не вышло его время: сервис мог быть
        // перезапущен системой уже после окончания звонка.
        val ringingLeft = settings.ringDurationSeconds * 1000L -
            (System.currentTimeMillis() - state.startedAt)
        val shouldRing = ringingLeft > 0 && alarm?.sound != false
        if (shouldRing) {
            startSound()
            if (alarm?.vibrate != false) startVibration()
        }

        if (state.ringOnly) {
            scheduleRingOnlyFinish(settings)
        } else {
            scheduleLockingTimers(settings)
            // Жёсткий режим: дополнительно поднимаем системный экран блокировки.
            if (settings.hardMode) {
                LockController.lockNow(this)
            }
        }

        LockController.showLockScreen(this)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopEverything()
        super.onDestroy()
    }

    /** Будильник без блокировки: после окончания звука экран закрывается. */
    private fun scheduleRingOnlyFinish(settings: Settings) {
        handler.removeCallbacksAndMessages(null)
        val elapsed = System.currentTimeMillis() - LockState.current().startedAt
        val remaining = (settings.ringDurationSeconds * 1000L - elapsed).coerceAtLeast(0L)
        handler.postDelayed({
            stopSound()
            stopVibration()
            LockState.release(this)
            stopSelf()
        }, remaining)
    }

    /** Блокирующий будильник: звук останавливается, затем появляются задачи. */
    private fun scheduleLockingTimers(settings: Settings) {
        handler.removeCallbacksAndMessages(null)

        val elapsed = System.currentTimeMillis() - LockState.current().startedAt
        val ringLeft = (settings.ringDurationSeconds * 1000L - elapsed).coerceAtLeast(0L)

        handler.postDelayed({
            stopSound()
            stopVibration()
            // Звонок закончился — телефон остаётся заблокированным.
            if (LockState.current().phase == LockState.Phase.RINGING) {
                LockState.setPhase(LockState.Phase.LOCKED)
                updateNotification()
            }
        }, ringLeft)

        val remaining = LockState.current().unlockAt - System.currentTimeMillis()
        handler.postDelayed({
            if (!LockState.isActive()) return@postDelayed
            LockState.setPhase(LockState.Phase.QUIZ)
            LockController.showLockScreen(this)
            updateNotification()
        }, max(0L, remaining))
    }

    /**
     * Перенос будильника: гасим звонок, снимаем текущую блокировку и планируем
     * повторный звонок через заданное число минут.
     *
     * Перенос возможен только пока идёт звонок: он отменяет начало блокировки.
     */
    private fun handleSnooze(alarmId: String) {
        val settings = Settings.of(this)
        val alarm = AlarmStore(this).get(alarmId)
        val state = LockState.current()

        if (alarm == null || state.phase != LockState.Phase.RINGING) {
            // Переносить нечего — просто гасим звонок.
            stopSound()
            stopVibration()
            stopSelf()
            return
        }

        val snoozed = SnoozePolicy.applySnooze(
            alarm = alarm,
            now = System.currentTimeMillis(),
            minutes = settings.snoozeMinutes,
            maxSnoozes = settings.maxSnoozes
        )

        if (snoozed == null) {
            // Лимит переносов исчерпан: звонок гасится, блокировка продолжается.
            stopSound()
            stopVibration()
            if (state.ringOnly) {
                LockState.release(this)
            } else {
                LockState.setPhase(LockState.Phase.LOCKED)
                updateNotification()
            }
            stopSelf()
            return
        }

        AlarmStore(this).save(snoozed)
        stopSound()
        stopVibration()
        LockState.release(this)
        AllowedPackages.clear()
        TorchController.reset()
        AlarmScheduler.rescheduleAll(this)
        stopSelf()
    }

    private fun startSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        if (uri == null) return

        runCatching {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
        }
    }

    private fun stopSound() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    private fun startVibration() {
        val service = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return

        val pattern = longArrayOf(0, 700, 900)
        vibrator = service
        runCatching {
            service.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun stopVibration() {
        vibrator?.runCatching { cancel() }
        vibrator = null
    }

    private fun stopEverything() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        stopVibration()
    }

    private fun buildNotification(state: LockState.Snapshot): Notification {
        val ringOnly = state.ringOnly
        val contentIntent = PendingIntent.getActivity(
            this,
            REQUEST_CONTENT,
            Intent(this, com.romanpanin.advancedalarm.lock.LockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, App.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(state.alarmLabel.ifBlank { getString(R.string.app_name) })
            .setContentText(
                getString(if (ringOnly) R.string.notification_ring_only else R.string.notification_locked)
            )
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)

        if (ringOnly) {
            val stopIntent = PendingIntent.getService(
                this,
                REQUEST_STOP,
                Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stop),
                    getString(R.string.action_stop_alarm),
                    stopIntent
                ).build()
            )
        } else {
            // Блокировка остаётся, но звонок можно погасить.
            val stopSoundIntent = PendingIntent.getService(
                this,
                REQUEST_STOP_SOUND,
                Intent(this, AlarmService::class.java).setAction(ACTION_STOP_SOUND),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stop),
                    getString(R.string.action_stop_ring),
                    stopSoundIntent
                ).build()
            )
        }

        // Перенос доступен только пока идёт звонок.
        if (state.phase == LockState.Phase.RINGING && state.alarmId.isNotBlank()) {
            val snoozeIntent = PendingIntent.getService(
                this,
                REQUEST_SNOOZE,
                Intent(this, AlarmService::class.java)
                    .setAction(ACTION_SNOOZE)
                    .putExtra(EXTRA_ALARM_ID, state.alarmId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_snooze),
                    getString(R.string.action_snooze_short),
                    snoozeIntent
                ).build()
            )
        }
        return builder.build()
    }

    private fun updateNotification() {
        val state = LockState.current()
        runCatching {
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification(state))
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SHOULD_LOCK = "should_lock"
        const val EXTRA_TRIGGER_AT = "trigger_at"
        const val EXTRA_TEST = "test"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"

        const val ACTION_STOP = "com.romanpanin.advancedalarm.ACTION_STOP"
        const val ACTION_STOP_SOUND = "com.romanpanin.advancedalarm.ACTION_STOP_SOUND"
        const val ACTION_SNOOZE = "com.romanpanin.advancedalarm.ACTION_SNOOZE"

        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CONTENT = 10
        private const val REQUEST_STOP = 11
        private const val REQUEST_STOP_SOUND = 12
        private const val REQUEST_SNOOZE = 13

        /** Запуск тестовой блокировки из настроек. */
        fun startTest(context: android.content.Context, durationMinutes: Int) {
            val intent = Intent(context, AlarmService::class.java).apply {
                putExtra(EXTRA_TEST, true)
                putExtra(EXTRA_SHOULD_LOCK, true)
                putExtra(EXTRA_DURATION_MINUTES, durationMinutes)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
