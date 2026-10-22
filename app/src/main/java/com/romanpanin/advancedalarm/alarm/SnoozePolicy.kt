package com.romanpanin.advancedalarm.alarm

import com.romanpanin.advancedalarm.data.AlarmItem

/**
 * Правила переноса будильника («отложить на N минут»).
 *
 * Перенос доступен только пока идёт звонок: он отменяет начало блокировки
 * и переносит следующий звонок на заданное число минут. Число переносов подряд
 * ограничено, иначе задачи можно было бы откладывать бесконечно.
 *
 * Чистое ядро: не зависит от Android, поэтому проверяется юнит-тестами.
 */
object SnoozePolicy {

    /** Можно ли ещё перенести будильник. [maxSnoozes] = 0 — без ограничения. */
    fun canSnooze(snoozeCount: Int, maxSnoozes: Int): Boolean =
        maxSnoozes <= 0 || snoozeCount < maxSnoozes

    /** Момент повторного звонка после переноса. */
    fun snoozeUntil(now: Long, minutes: Int): Long =
        now + minutes * 60_000L

    /** Назначен ли будильнику перенос на будущее. */
    fun isSnoozed(alarm: AlarmItem, now: Long): Boolean =
        alarm.snoozedUntil > now

    /**
     * Применить перенос к будильнику.
     *
     * @return обновлённый будильник или null, если лимит переносов исчерпан.
     */
    fun applySnooze(
        alarm: AlarmItem,
        now: Long,
        minutes: Int,
        maxSnoozes: Int
    ): AlarmItem? {
        if (!canSnooze(alarm.snoozeCount, maxSnoozes)) return null
        return alarm.copy(
            snoozedUntil = snoozeUntil(now, minutes),
            snoozeCount = alarm.snoozeCount + 1
        )
    }
}
