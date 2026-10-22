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

/** Главный экран: список будильников и быстрый доступ к настройкам. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val store by lazy { AlarmStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        alarms.forEach { alarm ->
            binding.alarmList.addView(createAlarmView(alarm))
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
        }
        view.findViewById<TextView>(R.id.alarmTags).text =
            extra.joinToString(" · ")

        val toggle = view.findViewById<SwitchMaterial>(R.id.alarmEnabled)
        toggle.isChecked = alarm.enabled
        toggle.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, checked ->
            store.save(alarm.copy(enabled = checked))
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
    }
}
