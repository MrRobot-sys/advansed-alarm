package com.romanpanin.advancedalarm.ui

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.romanpanin.advancedalarm.lock.AccessibilityGuard

/** Проверка, включена ли наша служба специальных возможностей. */
object AccessibilityState {

    fun isEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val expected = ComponentName(context, AccessibilityGuard::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        splitter.forEach { entry ->
            if (matches(entry, expected)) return true
        }
        return false
    }

    /**
     * Система хранит компонент в двух формах: полной
     * (`pkg/pkg.Class`) и сокращённой (`pkg/.Class`) — сравниваем обе.
     */
    private fun matches(entry: String, expected: ComponentName): Boolean {
        val separator = entry.indexOf('/')
        if (separator < 0) {
            // Запись без класса: включён весь пакет.
            return entry.equals(expected.packageName, ignoreCase = true)
        }

        val packageName = entry.substring(0, separator)
        val className = entry.substring(separator + 1)
        if (!packageName.equals(expected.packageName, ignoreCase = true)) return false

        val fullClassName = if (className.startsWith(".")) {
            expected.packageName + className
        } else {
            className
        }
        return fullClassName.equals(expected.className, ignoreCase = true)
    }
}
