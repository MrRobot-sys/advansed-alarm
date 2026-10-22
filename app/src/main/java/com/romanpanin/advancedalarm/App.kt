package com.romanpanin.advancedalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.romanpanin.advancedalarm.alarm.AlarmScheduler
import com.romanpanin.advancedalarm.service.LockState

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        LockState.init(this)
        // Процесс мог быть убит системой — восстанавливаем расписание,
        // иначе будильники молча пропадут до перезагрузки.
        AlarmScheduler.rescheduleAll(this)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            getString(R.string.channel_alarm),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.channel_alarm_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            getString(R.string.channel_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_service_desc)
            setSound(null, null)
        }

        manager.createNotificationChannel(alarmChannel)
        manager.createNotificationChannel(serviceChannel)
    }

    companion object {
        const val CHANNEL_ALARM = "alarm_channel"
        const val CHANNEL_SERVICE = "service_channel"
    }
}
