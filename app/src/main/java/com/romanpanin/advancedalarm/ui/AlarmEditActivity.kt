package com.romanpanin.advancedalarm.ui

import com.romanpanin.advancedalarm.util.Insets
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.romanpanin.advancedalarm.R
import com.romanpanin.advancedalarm.alarm.AlarmScheduler
import com.romanpanin.advancedalarm.data.AlarmItem
import com.romanpanin.advancedalarm.data.AlarmStore
import com.romanpanin.advancedalarm.data.Days
import com.romanpanin.advancedalarm.databinding.ActivityAlarmEditBinding
import java.util.Calendar
import java.util.UUID

/** Создание и правка будильника. */
class AlarmEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmEditBinding
    private val store by lazy { AlarmStore(this) }

    private var alarmId: String? = null
    private var hour = 7
    private var minute = 0
    private val days = linkedSetOf<Int>()
    private var ringOnly = false
    private var vibrate = true
    private var sound = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Insets.applyEdgeToEdge(this)
        Insets.applySafeArea(binding.root, ime = true)

        alarmId = intent.getStringExtra(EXTRA_ALARM_ID)
        val existing = alarmId?.let { store.get(it) }

        if (existing != null) {
            hour = existing.hour
            minute = existing.minute
            days.addAll(existing.days)
            ringOnly = existing.ringOnly
            vibrate = existing.vibrate
            sound = existing.sound
            binding.labelInput.setText(existing.label)
            binding.deleteButton.visibility = android.view.View.VISIBLE
        } else {
            val now = Calendar.getInstance()
            hour = now.get(Calendar.HOUR_OF_DAY)
            minute = now.get(Calendar.MINUTE)
        }

        binding.timeValue.text = formatTime(hour, minute)
        binding.timeValue.setOnClickListener {
            TimePickerDialog(
                this,
                { _, pickedHour, pickedMinute ->
                    hour = pickedHour
                    minute = pickedMinute
                    binding.timeValue.text = formatTime(hour, minute)
                },
                hour,
                minute,
                true
            ).show()
        }

        val dayBoxes = mapOf(
            R.id.dayMonday to Days.MONDAY,
            R.id.dayTuesday to Days.TUESDAY,
            R.id.dayWednesday to Days.WEDNESDAY,
            R.id.dayThursday to Days.THURSDAY,
            R.id.dayFriday to Days.FRIDAY,
            R.id.daySaturday to Days.SATURDAY,
            R.id.daySunday to Days.SUNDAY
        )
        dayBoxes.forEach { (viewId, day) ->
            val box = findViewById<CheckBox>(viewId)
            box.isChecked = day in days
            box.setOnCheckedChangeListener { _, checked ->
                if (checked) days.add(day) else days.remove(day)
            }
        }

        binding.ringOnlySwitch.isChecked = ringOnly
        binding.ringOnlySwitch.setOnCheckedChangeListener { _, checked -> ringOnly = checked }

        binding.vibrateSwitch.isChecked = vibrate
        binding.vibrateSwitch.setOnCheckedChangeListener { _, checked -> vibrate = checked }

        binding.soundSwitch.isChecked = sound
        binding.soundSwitch.setOnCheckedChangeListener { _, checked -> sound = checked }

        binding.saveButton.setOnClickListener { save() }
        binding.deleteButton.setOnClickListener { delete() }
    }

    private fun save() {
        if (days.isEmpty()) {
            Toast.makeText(this, R.string.alarm_pick_days, Toast.LENGTH_SHORT).show()
            return
        }
        val id = alarmId ?: UUID.randomUUID().toString()
        val item = AlarmItem(
            id = id,
            hour = hour,
            minute = minute,
            days = days.toSet(),
            enabled = true,
            label = binding.labelInput.text.toString().trim(),
            ringOnly = ringOnly,
            vibrate = vibrate,
            sound = sound
        )
        store.save(item)
        AlarmScheduler.rescheduleAll(this)
        Toast.makeText(this, R.string.alarm_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun delete() {
        alarmId?.let { store.delete(it) }
        AlarmScheduler.rescheduleAll(this)
        finish()
    }

    private fun formatTime(hour: Int, minute: Int): String =
        "%02d:%02d".format(hour, minute)

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
