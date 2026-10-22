package com.romanpanin.advancedalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.romanpanin.advancedalarm.data.AlarmStore
import com.romanpanin.advancedalarm.service.AlarmService

/** Приёмник срабатывания будильника: запускает foreground-сервис со звуком и блокировкой. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: return
        val alarm = AlarmStore(context).get(alarmId) ?: return

        val now = System.currentTimeMillis()
        val triggerAt = AlarmScheduler.lastTriggerAt(alarm, now)
        val shouldLock = AlarmPolicy.shouldLock(context, alarm, triggerAt)

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmService.EXTRA_SHOULD_LOCK, shouldLock)
            putExtra(AlarmService.EXTRA_TRIGGER_AT, triggerAt)
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        // Сработавший будильник одноразовый — сразу планируем следующее
        // срабатывание, иначе расписание оборвётся после первого звонка.
        AlarmScheduler.rescheduleAll(context)
    }
}
