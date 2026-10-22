package com.romanpanin.advancedalarm.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Один будильник.
 *
 * @param days     дни недели внутренней нумерации (1 = понедельник … 7 = воскресенье);
 *                 пустой набор означает «однократно» — срабатывает только в день создания.
 * @param ringOnly «звонок без блокировки»: будильник только звонит, телефон не блокируется.
 */
data class AlarmItem(
    val id: String,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>,
    val enabled: Boolean = true,
    val label: String = "",
    val ringOnly: Boolean = false,
    val vibrate: Boolean = true,
    val sound: Boolean = true,
) {

    fun timeText(): String = "%02d:%02d".format(hour, minute)

    fun daysText(): String = Days.describe(days)

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_HOUR, hour)
        put(KEY_MINUTE, minute)
        put(KEY_DAYS, JSONArray(days.toList()))
        put(KEY_ENABLED, enabled)
        put(KEY_LABEL, label)
        put(KEY_RING_ONLY, ringOnly)
        put(KEY_VIBRATE, vibrate)
        put(KEY_SOUND, sound)
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
        private const val KEY_DAYS = "days"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LABEL = "label"
        private const val KEY_RING_ONLY = "ringOnly"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_SOUND = "sound"

        fun fromJson(json: JSONObject): AlarmItem {
            val daysArray = json.optJSONArray(KEY_DAYS) ?: JSONArray()
            val days = buildSet {
                for (index in 0 until daysArray.length()) {
                    add(daysArray.optInt(index))
                }
            }
            return AlarmItem(
                id = json.optString(KEY_ID),
                hour = json.optInt(KEY_HOUR),
                minute = json.optInt(KEY_MINUTE),
                days = days,
                enabled = json.optBoolean(KEY_ENABLED, true),
                label = json.optString(KEY_LABEL),
                ringOnly = json.optBoolean(KEY_RING_ONLY, false),
                vibrate = json.optBoolean(KEY_VIBRATE, true),
                sound = json.optBoolean(KEY_SOUND, true)
            )
        }
    }
}
