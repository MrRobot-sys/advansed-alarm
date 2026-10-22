package com.romanpanin.advancedalarm.alarm

import android.content.Context
import com.romanpanin.advancedalarm.data.AlarmItem
import com.romanpanin.advancedalarm.data.AlarmStore
import com.romanpanin.advancedalarm.data.Days
import com.romanpanin.advancedalarm.data.Settings
import java.util.Calendar

/**
 * Правила, определяющие, должен ли будильник запускать блокировку.
 *
 * 1. «Звонок без блокировки» у конкретного будильника — блокировки нет.
 * 2. День входит в список «рабочих дней без блокировки» — блокировки нет.
 * 3. Если в течение окна группировки (по умолчанию 60 минут) после этого
 *    будильника есть другой включённый будильник того же дня, который сам
 *    способен запустить блокировку, — блокировку запускает только последний.
 */
object AlarmPolicy {

    /** Обёртка: читает состояние приложения и передаёт его чистому ядру. */
    fun shouldLock(context: Context, alarm: AlarmItem, triggerAt: Long): Boolean {
        val settings = Settings.of(context)
        return shouldLock(
            alarm = alarm,
            triggerAt = triggerAt,
            others = AlarmStore(context).all(),
            workingDays = settings.workingDaysNoLock,
            windowMinutes = settings.groupWindowMinutes
        )
    }

    /**
     * Чистое ядро правила: не зависит от Android, поэтому проверяется юнит-тестами.
     *
     * @param others все будильники приложения — включённые и выключенные;
     *               фильтрация выполняется внутри.
     */
    fun shouldLock(
        alarm: AlarmItem,
        triggerAt: Long,
        others: List<AlarmItem>,
        workingDays: Set<Int>,
        windowMinutes: Int
    ): Boolean {
        if (alarm.ringOnly) return false

        val triggerDay = Days.fromCalendar(dayOfWeek(triggerAt))
        if (triggerDay in workingDays) return false

        val window = windowMinutes * 60_000L
        others.asSequence()
            .filter { it.enabled && it.id != alarm.id }
            .filter { canLock(it, triggerDay, workingDays) }
            .forEach { other ->
                val next = nextSameDayOccurrence(other, triggerAt)
                if (next != null && next - triggerAt in 1..window) return false
            }
        return true
    }

    /** Может ли этот будильник запустить блокировку в указанный день. */
    private fun canLock(alarm: AlarmItem, day: Int, workingDays: Set<Int>): Boolean {
        if (alarm.ringOnly) return false
        if (day in workingDays) return false
        return true
    }

    /**
     * Следующее срабатывание будильника [other] строго позже [triggerAt]
     * и строго в тот же календарный день. null — такого срабатывания нет.
     */
    private fun nextSameDayOccurrence(other: AlarmItem, triggerAt: Long): Long? {
        val base = Calendar.getInstance().apply { timeInMillis = triggerAt }
        val probe = base.clone() as Calendar
        probe.set(Calendar.HOUR_OF_DAY, other.hour)
        probe.set(Calendar.MINUTE, other.minute)
        probe.set(Calendar.SECOND, 0)
        probe.set(Calendar.MILLISECOND, 0)

        if (probe.timeInMillis <= triggerAt) return null
        if (probe.get(Calendar.YEAR) != base.get(Calendar.YEAR)) return null
        if (probe.get(Calendar.DAY_OF_YEAR) != base.get(Calendar.DAY_OF_YEAR)) return null

        val day = Days.fromCalendar(probe.get(Calendar.DAY_OF_WEEK))
        if (other.days.isNotEmpty() && day !in other.days) return null

        return probe.timeInMillis
    }

    private fun dayOfWeek(time: Long): Int =
        Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.DAY_OF_WEEK)
}
