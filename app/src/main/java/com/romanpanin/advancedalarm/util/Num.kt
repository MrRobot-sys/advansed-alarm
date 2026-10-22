package com.romanpanin.advancedalarm.util

import kotlin.math.abs

/** Разбор и форматирование чисел, которые вводит пользователь. */
object Num {

    /**
     * Принимает «5», «-5», «−5» (типографский минус), «1,75», «1.75», «7/4».
     * Возвращает null, если строка не является числом.
     */
    fun parse(raw: String): Double? {
        val text = raw.trim()
            .replace('−', '-')
            .replace(" ", "")
            .replace(',', '.')
        if (text.isEmpty()) return null

        if (text.contains('/')) {
            val parts = text.split('/')
            if (parts.size != 2) return null
            val numerator = parts[0].toDoubleOrNull() ?: return null
            val denominator = parts[1].toDoubleOrNull() ?: return null
            if (abs(denominator) < 1e-9) return null
            return numerator / denominator
        }
        return text.toDoubleOrNull()
    }

    /** Печать числа без лишних нулей: 3, -2, 1.75. */
    fun format(value: Double): String {
        val rounded = Math.round(value * 10000.0) / 10000.0
        return if (abs(rounded - Math.round(rounded)) < 1e-9) {
            Math.round(rounded).toLong().toString()
        } else {
            java.lang.String.format(java.util.Locale.US, "%.4f", rounded)
                .trimEnd('0')
                .trimEnd('.')
        }
    }
}
