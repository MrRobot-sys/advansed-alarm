package com.romanpanin.advancedalarm.util

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/** Управление вспышкой (фонариком) через CameraManager. */
object TorchController {

    @Volatile
    private var enabled = false

    /**
     * Переключает фонарик.
     * @return true — включён, false — выключен, null — фонариком управлять не удалось.
     */
    fun toggle(context: Context): Boolean? {
        val manager = context.getSystemService(CameraManager::class.java) ?: return null
        val cameraId = findCameraWithFlash(manager) ?: return null
        val target = !enabled
        return runCatching {
            manager.setTorchMode(cameraId, target)
            enabled = target
            target
        }.getOrNull()
    }

    fun isEnabled(): Boolean = enabled

    fun reset() {
        enabled = false
    }

    private fun findCameraWithFlash(manager: CameraManager): String? = runCatching {
        manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()
}
