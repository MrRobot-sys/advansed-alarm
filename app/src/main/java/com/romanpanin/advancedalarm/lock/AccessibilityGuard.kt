package com.romanpanin.advancedalarm.lock

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.romanpanin.advancedalarm.data.Settings
import com.romanpanin.advancedalarm.service.LockState

/**
 * Служба специальных возможностей — основной «жёсткий» слой блокировки.
 *
 * Пока блокировка активна, приложение не даёт выйти с экрана блокировки:
 *  * перехватывает переход в другое приложение (в том числе по кнопке «Домой»
 *    и в обзоре недавних) и возвращает экран блокировки наверх;
 *  * закрывает шторку уведомлений.
 *
 * Разрешено открывать: звонилки и экран звонка, SystemUI, клавиатуру,
 * камеру, запущенную с экрана блокировки, и пакеты из настроек.
 */
class AccessibilityGuard : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastPullBackAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        LockState.init(this)
        if (!LockState.isActive()) return
        if (packageName == this.packageName) return

        if (packageName.contains("systemui", ignoreCase = true)) {
            // Шторка уведомлений во время блокировки закрывается.
            runCatching { performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE) }
            return
        }

        val settings = Settings.of(this)
        if (AllowedPackages.isAllowed(packageName, settings.allowedPackages)) {
            Log.i(TAG, "Разрешён переход в $packageName")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastPullBackAt < PULL_BACK_THROTTLE_MS) return
        lastPullBackAt = now

        Log.i(TAG, "Возвращаю экран блокировки из $packageName")
        handler.postDelayed({
            if (!LockState.isActive()) return@postDelayed
            LockController.showLockScreen(this)
        }, PULL_BACK_DELAY_MS)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AccessibilityGuard"
        private const val PULL_BACK_DELAY_MS = 150L
        private const val PULL_BACK_THROTTLE_MS = 600L

        @Volatile
        var instance: AccessibilityGuard? = null
            private set
    }
}
