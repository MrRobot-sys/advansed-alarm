package com.romanpanin.advancedalarm.lock

/**
 * Пакеты, которые разрешено открывать во время блокировки:
 * экран звонка и звонилки, SystemUI, клавиатура, а также пакет камеры,
 * запущенной прямо с экрана блокировки.
 */
object AllowedPackages {

    private val runtime = mutableSetOf<String>()

    /** Пакеты, разрешённые на время текущей блокировки. */
    private val SYSTEM_PATTERNS = listOf(
        "systemui",
        "incallui",
        "callui",
        "dialer",
        "contacts",
        "phone",
        "com.android.server.telecom",
        "inputmethod"
    )

    fun allow(packageName: String?) {
        if (!packageName.isNullOrBlank()) {
            synchronized(runtime) { runtime.add(packageName) }
        }
    }

    fun clear() {
        synchronized(runtime) { runtime.clear() }
    }

    fun isAllowed(packageName: String, extraPackages: Set<String>): Boolean {
        synchronized(runtime) {
            if (packageName in runtime) return true
        }
        if (packageName in extraPackages) return true
        return SYSTEM_PATTERNS.any { packageName.contains(it, ignoreCase = true) }
    }
}
