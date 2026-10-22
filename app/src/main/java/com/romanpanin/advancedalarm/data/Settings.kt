package com.romanpanin.advancedalarm.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Пользовательские настройки приложения. Хранятся в SharedPreferences,
 * состояние блокировки — отдельно (см. LockState).
 */
class Settings private constructor(private val prefs: SharedPreferences) {

    /** Длительность блокировки после звонка будильника, минут. */
    var lockDurationMinutes: Int
        get() = prefs.getInt(KEY_LOCK_MINUTES, DEFAULT_LOCK_MINUTES)
        set(value) = prefs.edit().putInt(KEY_LOCK_MINUTES, value.coerceIn(1, 240)).apply()

    /** Количество алгебраических задач для разблокировки. */
    var quizTaskCount: Int
        get() = prefs.getInt(KEY_QUIZ_COUNT, DEFAULT_QUIZ_COUNT)
        set(value) = prefs.edit().putInt(KEY_QUIZ_COUNT, value.coerceIn(1, 50)).apply()

    /**
     * Окно группировки будильников, минут. Если в этот интервал после будильника
     * есть другой включённый будильник, блокировку запускает только последний из них.
     */
    var groupWindowMinutes: Int
        get() = prefs.getInt(KEY_GROUP_WINDOW, DEFAULT_GROUP_WINDOW)
        set(value) = prefs.edit().putInt(KEY_GROUP_WINDOW, value.coerceIn(0, 240)).apply()

    /** Сколько секунд звучит будильник до того, как замолчит (блокировка при этом остаётся). */
    var ringDurationSeconds: Int
        get() = prefs.getInt(KEY_RING_SECONDS, DEFAULT_RING_SECONDS)
        set(value) = prefs.edit().putInt(KEY_RING_SECONDS, value.coerceIn(5, 3600)).apply()

    /** Аварийный PIN для выхода из блокировки. */
    var emergencyPin: String
        get() = prefs.getString(KEY_PIN, DEFAULT_PIN).orEmpty()
        set(value) = prefs.edit().putString(KEY_PIN, value).apply()

    /**
     * Жёсткий режим: дополнительно вызывается системная блокировка экрана
     * (DevicePolicyManager.lockNow). Требует PIN/паттерн на самом телефоне
     * и мешает открывать камеру во время блокировки.
     */
    var hardMode: Boolean
        get() = prefs.getBoolean(KEY_HARD_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_HARD_MODE, value).apply()

    /** Дни недели (1 = понедельник … 7 = воскресенье), в которые блокировка не применяется. */
    var workingDaysNoLock: Set<Int>
        get() = prefs.getStringSet(KEY_WORKING_DAYS, emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()
        set(value) = prefs.edit()
            .putStringSet(KEY_WORKING_DAYS, value.map { it.toString() }.toSet())
            .apply()

    /** Дополнительные пакеты приложений, которые разрешено открывать во время блокировки. */
    var allowedPackages: Set<String>
        get() = prefs.getStringSet(KEY_ALLOWED_PACKAGES, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_ALLOWED_PACKAGES, value.toSet()).apply()

    var onboardingShown: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    companion object {
        const val DEFAULT_PIN = "1234"
        const val DEFAULT_LOCK_MINUTES = 30
        const val DEFAULT_QUIZ_COUNT = 10
        const val DEFAULT_GROUP_WINDOW = 60
        const val DEFAULT_RING_SECONDS = 60

        private const val PREFS_NAME = "advanced_alarm_settings"

        private const val KEY_LOCK_MINUTES = "lock_duration_minutes"
        private const val KEY_QUIZ_COUNT = "quiz_task_count"
        private const val KEY_GROUP_WINDOW = "group_window_minutes"
        private const val KEY_RING_SECONDS = "ring_duration_seconds"
        private const val KEY_PIN = "emergency_pin"
        private const val KEY_HARD_MODE = "hard_mode"
        private const val KEY_WORKING_DAYS = "working_days_no_lock"
        private const val KEY_ALLOWED_PACKAGES = "allowed_packages"
        private const val KEY_ONBOARDING = "onboarding_shown"

        fun of(context: Context): Settings = Settings(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        )
    }
}
