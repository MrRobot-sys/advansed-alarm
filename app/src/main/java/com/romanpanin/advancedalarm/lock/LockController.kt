package com.romanpanin.advancedalarm.lock

import android.app.Notification
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.romanpanin.advancedalarm.App
import com.romanpanin.advancedalarm.R

/** Управление блокировкой: показ экрана блокировки, системная блокировка, ссылки на настройки. */
object LockController {

    private const val TAG = "LockController"

    /** Показывает экран блокировки поверх всего, включая экран блокировки системы. */
    fun showLockScreen(context: Context) {
        val intent = Intent(context, LockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        try {
            context.startActivity(intent)
        } catch (error: RuntimeException) {
            // Android может запретить запуск Activity из фона — тогда сработает
            // полноэкранное уведомление, опубликованное сервисом.
            Log.w(TAG, "Не удалось показать экран блокировки напрямую", error)
        }
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.isAdminActive(ComponentName(context, DeviceAdminReceiver::class.java))
    }

    /** Есть ли на телефоне PIN, паттерн или пароль. */
    fun isDeviceSecure(context: Context): Boolean {
        val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
        return keyguard?.isDeviceSecure == true
    }

    /**
     * Системная блокировка экрана (DevicePolicyManager.lockNow).
     * Требует активного администратора устройства и работает, только если
     * на телефоне уже установлен PIN/паттерн/пароль.
     */
    fun lockNow(context: Context): Boolean {
        if (!isDeviceAdminActive(context)) return false
        if (!isDeviceSecure(context)) return false
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return runCatching {
            dpm.lockNow()
            true
        }.getOrDefault(false)
    }

    fun openDeviceAdminSettings(context: Context) {
        val admin = ComponentName(context, DeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openSecuritySettings(context: Context) {
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openFullScreenIntentSettings(context: Context) {
        val intent = Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT")
            .setData(android.net.Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Восстанавливает экран блокировки после перезагрузки.
     * Из BOOT_COMPLETED нельзя запускать Activity, поэтому показываем
     * полноэкранное уведомление — систему оно превращает в запуск экрана.
     */
    fun postRestoreNotification(context: Context) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
            ?: return
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            REQUEST_RESTORE,
            Intent(context, LockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, App.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_locked))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .build()

        runCatching { manager.notify(NOTIFICATION_RESTORE_ID, notification) }
    }

    private const val NOTIFICATION_RESTORE_ID = 1002
    private const val REQUEST_RESTORE = 21
}
