package com.romanpanin.advancedalarm.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.romanpanin.advancedalarm.R
import com.romanpanin.advancedalarm.alarm.AlarmScheduler
import com.romanpanin.advancedalarm.data.AlarmItem
import com.romanpanin.advancedalarm.data.AlarmStore
import com.romanpanin.advancedalarm.data.Settings
import com.romanpanin.advancedalarm.databinding.ActivityMainBinding
import com.romanpanin.advancedalarm.service.AlarmService
import com.romanpanin.advancedalarm.util.Insets
import java.text.SimpleDateFormat
import java.util.Locale

/** Главный экран: список будильников и быстрый доступ к настройкам. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val store by lazy { AlarmStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Контент не уезжает под строку состояния и панель навигации.
        Insets.applyEdgeToEdge(this)
        Insets.applySafeArea(binding.root)

        binding.addAlarmButton.setOnClickListener {
            startActivity(Intent(this, AlarmEditActivity::class.java))
        }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.onboardingButton.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        binding.testButton.setOnClickListener {
            AlarmService.startTest(this, TEST_DURATION_MINUTES)
        }

        if (!Settings.of(this).onboardingShown) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        renderAlarms()
    }

    private fun renderAlarms() {
        binding.alarmList.removeAllViews()
        val alarms = store.all().sortedWith(
            compareBy({ it.hour }, { it.minute })
        )

        binding.emptyText.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE
        renderNextAlarm(alarms)

        alarms.forEach { alarm ->
            binding.alarmList.addView(createAlarmView(alarm))
        }
    }

    /** Строка «Следующий — 07:30, через 9 ч 12 мин». */
    private fun renderNextAlarm(alarms: List<AlarmItem>) {
        val now = System.currentTimeMillis()
        val next = alarms.asSequence()
            .filter { it.enabled }
            .mapNotNull { alarm -> AlarmScheduler.nextTriggerAt(alarm, now)?.let { alarm to it } }
            .minByOrNull { it.second }

        binding.nextAlarmText.text = if (next == null) {
            getString(R.string.main_next_none)
        } else {
            val delay = next.second - now
            getString(
                R.string.main_next_alarm,
                formatTime(next.second),
                getString(R.string.main_in, relativeDuration(delay))
            )
        }
    }

    /** «через 45 мин», «через 9 ч 12 мин» или «через 2 д» — коротко и без падежей. */
    private fun relativeDuration(delayMillis: Long): String {
        val minutes = (delayMillis / MINUTE_MILLIS).coerceAtLeast(1)
        return when {
            minutes < HOUR_MILLIS / MINUTE_MILLIS ->
                getString(R.string.main_duration_minutes, minutes)
            minutes < DAY_MILLIS / MINUTE_MILLIS -> {
                val hours = minutes / (HOUR_MILLIS / MINUTE_MILLIS)
                val rest = minutes % (HOUR_MILLIS / MINUTE_MILLIS)
                if (rest == 0L) {
                    getString(R.string.main_duration_hours, hours)
                } else {
                    getString(R.string.main_duration_hours_minutes, hours, rest)
                }
            }
            else -> getString(R.string.main_duration_days, minutes / (DAY_MILLIS / MINUTE_MILLIS))
        }
    }

    private fun createAlarmView(alarm: AlarmItem): View {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_alarm, binding.alarmList, false)

        view.findViewById<TextView>(R.id.alarmTime).text = alarm.timeText()
        view.findViewById<TextView>(R.id.alarmDays).text = alarm.daysText()
        view.findViewById<TextView>(R.id.alarmLabel).text = alarm.label.ifBlank {
            getString(R.string.alarm_default_label)
        }

        val extra = buildList {
            if (alarm.ringOnly) add(getString(R.string.alarm_ring_only_tag))
            if (!alarm.sound) add(getString(R.string.alarm_no_sound_tag))
            if (!alarm.vibrate) add(getString(R.string.alarm_no_vibrate_tag))
            if (alarm.snoozedUntil > System.currentTimeMillis()) {
                add(getString(R.string.alarm_snoozed_tag, formatTime(alarm.snoozedUntil)))
            }
        }
        view.findViewById<TextView>(R.id.alarmTags).apply {
            val tags = extra.joinToString(" · ")
            visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
            text = tags
        }

        val toggle = view.findViewById<SwitchMaterial>(R.id.alarmEnabled)
        toggle.isChecked = alarm.enabled
        toggle.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, checked ->
            // Выключенный будильник переноса не ждёт.
            val snoozedUntil = if (checked && alarm.snoozedUntil > System.currentTimeMillis()) {
                alarm.snoozedUntil
            } else {
                0L
            }
            store.save(alarm.copy(enabled = checked, snoozedUntil = snoozedUntil))
            AlarmScheduler.rescheduleAll(this)
        })

        view.setOnClickListener {
            startActivity(
                Intent(this, AlarmEditActivity::class.java)
                    .putExtra(AlarmEditActivity.EXTRA_ALARM_ID, alarm.id)
            )
        }
        return view
    }

    companion object {
        private const val TEST_DURATION_MINUTES = 1
        private const val MINUTE_MILLIS = 60_000L
        private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
        private const val DAY_MILLIS = 24 * HOUR_MILLIS

        /** Время переноса в формате ЧЧ:ММ. */
        fun formatTime(timeInMillis: Long): String =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(timeInMillis)
    }
}
