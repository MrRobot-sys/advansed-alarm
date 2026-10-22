package com.romanpanin.advancedalarm.alarm

import com.romanpanin.advancedalarm.data.AlarmItem
import com.romanpanin.advancedalarm.data.Days
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверка правил переноса будильника: момент повторного звонка и лимит
 * переносов подряд (иначе задачи можно было бы откладывать бесконечно).
 */
class SnoozePolicyTest {

    /** Среда, 21 октября 2026, 07:00. */
    private val now: Long = 1_792_530_000_000L

    private fun alarm(snoozeCount: Int = 0, snoozedUntil: Long = 0L) = AlarmItem(
        id = "a",
        hour = 7,
        minute = 0,
        days = setOf(Days.WEDNESDAY),
        snoozeCount = snoozeCount,
        snoozedUntil = snoozedUntil
    )

    @Test
    fun `snooze is scheduled after the given number of minutes`() {
        assertEquals(now + 5 * 60_000L, SnoozePolicy.snoozeUntil(now, 5))
    }

    @Test
    fun `snooze is allowed below the limit`() {
        assertTrue(SnoozePolicy.canSnooze(snoozeCount = 0, maxSnoozes = 3))
        assertTrue(SnoozePolicy.canSnooze(snoozeCount = 2, maxSnoozes = 3))
    }

    @Test
    fun `snooze is forbidden once the limit is reached`() {
        assertFalse(SnoozePolicy.canSnooze(snoozeCount = 3, maxSnoozes = 3))
        assertFalse(SnoozePolicy.canSnooze(snoozeCount = 4, maxSnoozes = 3))
    }

    @Test
    fun `zero limit means no restriction`() {
        assertTrue(SnoozePolicy.canSnooze(snoozeCount = 0, maxSnoozes = 0))
        assertTrue(SnoozePolicy.canSnooze(snoozeCount = 99, maxSnoozes = 0))
    }

    @Test
    fun `applySnooze sets the next ring time and counts the snooze`() {
        val subject = alarm()
        val result = SnoozePolicy.applySnooze(
            alarm = subject,
            now = now,
            minutes = 5,
            maxSnoozes = 3
        )
        assertNotNull(result)
        assertEquals(now + 5 * 60_000L, result!!.snoozedUntil)
        assertEquals(1, result.snoozeCount)
    }

    @Test
    fun `applySnooze returns nothing when the limit is reached`() {
        val subject = alarm(snoozeCount = 3)
        assertNull(
            SnoozePolicy.applySnooze(
                alarm = subject,
                now = now,
                minutes = 5,
                maxSnoozes = 3
            )
        )
    }

    @Test
    fun `snoozed alarm is marked as snoozed only before the next ring`() {
        assertTrue(SnoozePolicy.isSnoozed(alarm(snoozedUntil = now + 1000L), now))
        assertFalse(SnoozePolicy.isSnoozed(alarm(snoozedUntil = now - 1000L), now))
        assertFalse(SnoozePolicy.isSnoozed(alarm(), now))
    }
}
