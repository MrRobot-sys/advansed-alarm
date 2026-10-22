package com.romanpanin.advancedalarm.alarm

import com.romanpanin.advancedalarm.data.AlarmItem
import com.romanpanin.advancedalarm.data.Days
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Проверка расписания: перенос будильника важнее обычного времени,
 * а момент фактического звонка известен точно (по нему считаются
 * группировка будильников и рабочие дни).
 */
class AlarmSchedulerTest {

    /** Среда, 21 октября 2026, 21:00. */
    private val now: Long = Calendar.getInstance().apply {
        set(Calendar.YEAR, 2026)
        set(Calendar.MONTH, Calendar.OCTOBER)
        set(Calendar.DAY_OF_MONTH, 21)
        set(Calendar.HOUR_OF_DAY, 21)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun alarm(
        hour: Int = 22,
        minute: Int = 0,
        days: Set<Int> = setOf(Days.WEDNESDAY),
        snoozedUntil: Long = 0L
    ) = AlarmItem(
        id = "a",
        hour = hour,
        minute = minute,
        days = days,
        snoozedUntil = snoozedUntil
    )

    @Test
    fun `snooze wins over the daily schedule`() {
        val snoozedUntil = now + 5 * 60_000L
        val subject = alarm(snoozedUntil = snoozedUntil)
        assertEquals(snoozedUntil, AlarmScheduler.nextTriggerAt(subject, now))
    }

    @Test
    fun `expired snooze falls back to the daily schedule`() {
        val subject = alarm(snoozedUntil = now - 60_000L)
        val next = AlarmScheduler.nextTriggerAt(subject, now)
        assertNotNull(next)
        assertTrue(next!! > now)
    }

    @Test
    fun `trigger time of a snoozed alarm is the moment of the snooze`() {
        val snoozedAt = now - 30_000L
        val subject = alarm(snoozedUntil = snoozedAt)
        assertEquals(snoozedAt, AlarmScheduler.triggerTimeOf(subject, now))
    }

    @Test
    fun `trigger time of an ordinary alarm is its schedule time`() {
        val subject = alarm(hour = 21, minute = 0)
        assertEquals(now, AlarmScheduler.triggerTimeOf(subject, now))
    }
}
