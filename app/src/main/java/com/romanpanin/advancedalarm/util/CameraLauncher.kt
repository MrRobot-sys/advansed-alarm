package com.romanpanin.advancedalarm.util

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import com.romanpanin.advancedalarm.lock.AllowedPackages

/** Запуск системной камеры с экрана блокировки. */
object CameraLauncher {

    fun launch(context: Context): Boolean {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Пакет камеры разрешаем на время блокировки: служба специальных
        // возможностей не должна выбрасывать пользователя обратно.
        AllowedPackages.allow(intent.resolveActivity(context.packageManager)?.packageName)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
