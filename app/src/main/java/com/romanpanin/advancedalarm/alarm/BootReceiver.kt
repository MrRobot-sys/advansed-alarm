package com.romanpanin.advancedalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.romanpanin.advancedalarm.lock.LockController
import com.romanpanin.advancedalarm.service.LockState

/**
 * Переустанавливает расписание после перезагрузки, смены времени или обновления
 * приложения. Если в момент перезагрузки шла блокировка — восстанавливает
 * экран блокировки (полноэкранным уведомлением: запуск Activity из
 * BOOT_COMPLETED на Android 10+ запрещён).
 *
 * Событие USER_PRESENT обрабатывается здесь же: сразу после загрузки телефон
 * ещё заблокирован системным PIN, данные приложения зашифрованы и недоступны —
 * расписание и блокировку можно восстановить только после разблокировки.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)

        LockState.init(context)
        if (LockState.isActive() && !LockState.current().ringOnly) {
            LockController.postRestoreNotification(context)
        }
    }
}
