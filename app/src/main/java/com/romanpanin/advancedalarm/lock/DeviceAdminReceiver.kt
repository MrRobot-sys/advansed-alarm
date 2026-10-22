package com.romanpanin.advancedalarm.lock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Администратор устройства. Даёт доступ к DevicePolicyManager.lockNow()
 * (системная блокировка экрана) — используется в «жёстком режиме».
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) = Unit

    override fun onDisabled(context: Context, intent: Intent) = Unit
}
