package com.romanpanin.advancedalarm.alarm

import com.romanpanin.advancedalarm.data.AlarmItem
import com.romanpanin.advancedalarm.data.Days
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Проверка правил блокировки: «звонок без блокировки», рабочие дни
 * и группировка будильников (блокирует только последний в окне).
 */
class AlarmPolicyTest {

    /** Среда, 21 октября 2026, 22:00 — момент срабатывания. */
    private val triggerAt: Long = Calendar.getInstance().apply {
        set(Calendar.YEAR, 2026)
        set(Calendar.MONTH, Calendar.OCTOBER)
        set(Calendar.DAY_OF_MONTH, 21)
        set(Calendar.HOUR_OF_DAY, 22)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun alarm(
        id: String,
        hour: Int,
        minute: Int,
        days: Set<Int> = setOf(Days.WEDNESDAY),
        enabled: Boolean = true,
        ringOnly: Boolean = false
    ) = AlarmItem(
        id = id,
        hour = hour,
        minute = minute,
        days = days,
        enabled = enabled,
        ringOnly = ringOnly
    )

    @Test
    fun `single alarm locks the phone`() {
        val subject = alarm("a", 22, 0)
        assertTrue(
            AlarmPolicy.shouldLock(
                alarm = subject,
                triggerAt = triggerAt,
                others = listOf(subject),
                workingDays = emptySet(),
                windowMinutes = 60
            )
        )
    }

    @Test
    fun `ring only alarm never locks`() {
        val subject = alarm("a", 22, 0, ringOnly = true)
        assertFalse(
            AlarmPolicy.shouldLock(
                alarm = subject,
                triggerAt = triggerAt,
                others = listOf(subject),
                workingDays = emptySet(),
                windowMinutes = 60
            )
        )
    }

    @Test
    fun `working day is never locked`() {
        val subject = alarm("a", 22, 0)
        assertFalse(
            AlarmPolicy.shouldLock(
                alarm = subject,
                triggerAt = triggerAt,
                others = listOf(subject),
                workingDays = setOf(Days.WEDNESDAY),
                windowMinutes = 60
            )
        )
    }

    @Test
    fun `later alarm inside the window absorbs the earlier one`() {
        val subject = alarm("a", 22, 0)
        val later = alarm("b", 22, 30)
        assertFalse(
            AlarmPolicy.shouldLock(
                alarm = subject,
                triggerAt = triggerAt,
                others = listOf(subject, later),
                workingDays = emptySet(),
                windowMinutes = 60
            )
        )
    }

    @Test
    fun `later alarm outside the window does not absorb`() {
        val subject = alarm("a", 22, 0)
        val later = alarm("b", 23, 30)
        assertTrue(
            AlarmPolicy.shouldLock(
                alarm = subject,
                triggerAt = triggerAt,
                others = listOf(subject, later),
                workingDays = emptySet(),
                windowMinutes = 60
            )
        )
    }

    @Test
    fun `window boundary is inclusive`() {
        val subject = alarm("a", 22, 0)
        val boundary = alarm("b", 23, 0)
        assertFalse(
            "ровно через 60 минут — ещё поглощает",
            AlarmPolicy.shouldLock(
                alarm = subject,
                triggerAt = triggerAt,
                others = listOf(subject, boundary),
                workingDays = emptySet(),
                windowMinutes = 60
            )
        )
        assertTrue(
            "через 61 минуту — уже не поглощает",
            AlarmPolicy.shouldLock(
                alarm = subject,
                triggerAt = triggerAt,
                others = listOf(subject, alarm("c", 23, 1)),
                workingDays = emptySet(),
                windowMinutes = 60
            )
        )
    }

    @Test
    fun `narrow window does not absorb`() {
        val subject = alarm("a", 22, 0)
        val later = alarm("b", 22, 30)
        assertTrue(
            AlarmPolicy.shouldLock(
                alarm = subject,
                triggerAt = triggerAt,
                others = listOf(subject, later),
                workingDays = emptySet(),
                windowMinutes = 10
            )
        )
    }

    @Test
    fun `ring only alarm does not absorb`() {
        val subject = alarm("a", 22, 0)
        val ringOnly = alarm("b", 22, 30, ringOnly = true)
        assertTrue(
            AlarmPolicy.shouldLock(
                alarm = subject,
                triggerAt = triggerAt,
                others = listOf(subject, ringOnly),
                workingDays = emptySet(),
                windowMinutes = 60
            )
        )
    }

    @Test
    fun `disabled alarm does not absorb`() {
        val subject = alarm("a", 22, 0)
        val disabled = alarm("b", 22, 30, enabled = false)
        assertTrue(
            AlarmPolicy.shouldLock(
                alarm = subject,
                triggerAt = triggerAt,
                others = listOf(subject, disabled),
                workingDays = emptySet(),
                windowMinutes = 60
            )
        )
    }

    @Test
    fun `alarm of another day does not absorb`() {
        val subject = alarm("a", 22, 0)
        val monday = alarm("b", 22, 30, days = setOf(Days.MONDAY))
        assertTrue(
            AlarmPolicy.shouldLock(
                alarm = subject,
                triggerAt = triggerAt,
                others = listOf(subject, monday),
                workingDays = emptySet(),
                windowMinutes = 60
            )
        )
    }

    @Test
    fun `earlier alarm does not absorb`() {
        val subject = alarm("a", 22, 0)
        val earlier = alarm("b", 21, 30)
        assertTrue(
            AlarmPolicy.shouldLock(
                alarm = subject,
                triggerAt = triggerAt,
                others = listOf(subject, earlier),
                workingDays = emptySet(),
                windowMinutes = 60
            )
        )
    }

    @Test
    fun `last alarm of the group locks`() {
        val subject = alarm("b", 22, 30)
        val earlier = alarm("a", 22, 0)
        assertTrue(
            "у последнего в группе поглотителя нет",
            AlarmPolicy.shouldLock(
                alarm = subject,
                triggerAt = triggerAt + 30 * 60_000L,
                others = listOf(subject, earlier),
                workingDays = emptySet(),
                windowMinutes = 60
            )
        )
    }
}
