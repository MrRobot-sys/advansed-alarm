package com.romanpanin.advancedalarm.data

/** Дни недели в собственной нумерации: 1 = понедельник … 7 = воскресенье. */
object Days {
    const val MONDAY = 1
    const val TUESDAY = 2
    const val WEDNESDAY = 3
    const val THURSDAY = 4
    const val FRIDAY = 5
    const val SATURDAY = 6
    const val SUNDAY = 7

    val ALL = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY)
    val WORKDAYS = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)

    private val SHORT_NAMES = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    private val FULL_NAMES = arrayOf(
        "понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"
    )

    fun shortName(day: Int): String = SHORT_NAMES.getOrElse(day - 1) { "?" }

    fun fullName(day: Int): String = FULL_NAMES.getOrElse(day - 1) { "?" }

    /** Java-календарь (MONDAY = 2 … SUNDAY = 1) → внутренняя нумерация. */
    fun fromCalendar(calendarDay: Int): Int = when (calendarDay) {
        java.util.Calendar.MONDAY -> MONDAY
        java.util.Calendar.TUESDAY -> TUESDAY
        java.util.Calendar.WEDNESDAY -> WEDNESDAY
        java.util.Calendar.THURSDAY -> THURSDAY
        java.util.Calendar.FRIDAY -> FRIDAY
        java.util.Calendar.SATURDAY -> SATURDAY
        else -> SUNDAY
    }

    /** Внутренняя нумерация → java.util.Calendar. */
    fun toCalendar(day: Int): Int = when (day) {
        MONDAY -> java.util.Calendar.MONDAY
        TUESDAY -> java.util.Calendar.TUESDAY
        WEDNESDAY -> java.util.Calendar.WEDNESDAY
        THURSDAY -> java.util.Calendar.THURSDAY
        FRIDAY -> java.util.Calendar.FRIDAY
        SATURDAY -> java.util.Calendar.SATURDAY
        else -> java.util.Calendar.SUNDAY
    }

    /** «Пн, Ср, Пт» — компактное представление набора дней. */
    fun describe(days: Set<Int>): String {
        if (days.isEmpty()) return "однократно"
        if (days == ALL) return "ежедневно"
        if (days == WORKDAYS) return "по рабочим дням"
        if (days == ALL - SUNDAY) return "по рабочим дням + суббота"
        return days.sorted().joinToString(", ") { shortName(it) }
    }
}
