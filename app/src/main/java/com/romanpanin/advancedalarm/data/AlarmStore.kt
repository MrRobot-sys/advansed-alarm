package com.romanpanin.advancedalarm.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
/** Хранилище будильников: список в SharedPreferences в виде JSON-массива. */
class AlarmStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<AlarmItem> {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(AlarmItem.fromJson(array.getJSONObject(index)))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun get(id: String): AlarmItem? = all().firstOrNull { it.id == id }

    fun save(alarm: AlarmItem) {
        val items = all().filterNot { it.id == alarm.id } + alarm
        write(items)
    }

    fun delete(id: String) {
        write(all().filterNot { it.id == id })
    }

    private fun write(items: List<AlarmItem>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ALARMS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "advanced_alarm_alarms"
        private const val KEY_ALARMS = "alarms"
    }
}
