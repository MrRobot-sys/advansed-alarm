package com.romanpanin.advancedalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.romanpanin.advancedalarm.data.AlarmItem
import com.romanpanin.advancedalarm.data.AlarmStore
import com.romanpanin.advancedalarm.data.Days
import java.util.Calendar

/**
 * Планировщик будильников. Используется setAlarmClock(): система показывает
 * ближайший будильник в статусной строке и не откладывает его в Doze.
 */
object AlarmScheduler {

    const val ACTION_TRIGGER = "com.romanpanin.advancedalarm.ACTION_TRIGGER"
    const val EXTRA_ALARM_ID = "alarm_id"

    fun rescheduleAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val alarms = AlarmStore(context).all()

        alarms.forEach { alarmManager.cancel(triggerPendingIntent(context, it.id)) }

        val now = System.currentTimeMillis()
        alarms.filter { it.enabled }.forEach { alarm ->
            val triggerAt = nextTriggerAt(alarm, now) ?: return@forEach
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, showPendingIntent(context)),
                triggerPendingIntent(context, alarm.id)
            )
        }
    }

    /** Ближайшее срабатывание будильника после [from]. */
    fun nextTriggerAt(alarm: AlarmItem, from: Long): Long? {
        if (alarm.days.isEmpty()) {
            val once = Calendar.getInstance().apply { timeInMillis = from }
            once.set(Calendar.HOUR_OF_DAY, alarm.hour)
            once.set(Calendar.MINUTE, alarm.minute)
            once.set(Calendar.SECOND, 0)
            once.set(Calendar.MILLISECOND, 0)
            return if (once.timeInMillis > from) once.timeInMillis else null
        }

        val base = Calendar.getInstance().apply { timeInMillis = from }
        for (offset in 0..7) {
            val probe = base.clone() as Calendar
            probe.add(Calendar.DAY_OF_YEAR, offset)
            if (Days.fromCalendar(probe.get(Calendar.DAY_OF_WEEK)) !in alarm.days) continue
            probe.set(Calendar.HOUR_OF_DAY, alarm.hour)
            probe.set(Calendar.MINUTE, alarm.minute)
            probe.set(Calendar.SECOND, 0)
            probe.set(Calendar.MILLISECOND, 0)
            if (probe.timeInMillis > from) return probe.timeInMillis
        }
        return null
    }

    /**
     * Время срабатывания будильника, который сработал прямо сейчас.
     * Вычисляется по расписанию, а не по времени доставки broadcast'а:
     * система может задержать доставку на десятки секунд.
     */
    fun lastTriggerAt(alarm: AlarmItem, now: Long): Long {
        val base = Calendar.getInstance().apply { timeInMillis = now }
        for (offset in 0..2) {
            val probe = base.clone() as Calendar
            probe.add(Calendar.DAY_OF_YEAR, -offset)
            if (alarm.days.isNotEmpty() &&
                Days.fromCalendar(probe.get(Calendar.DAY_OF_WEEK)) !in alarm.days
            ) continue
            probe.set(Calendar.HOUR_OF_DAY, alarm.hour)
            probe.set(Calendar.MINUTE, alarm.minute)
            probe.set(Calendar.SECOND, 0)
            probe.set(Calendar.MILLISECOND, 0)
            if (probe.timeInMillis <= now) return probe.timeInMillis
        }
        return now
    }

    private fun triggerPendingIntent(context: Context, alarmId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * PendingIntent для кнопки «показать» рядом с будильником в статусной строке.
     *
     * Открывается приложение (launcher activity). Планировщик не должен зависеть
     * от слоя экранов, поэтому activity не указывается явно.
     */
    private fun showPendingIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(context.packageName)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}