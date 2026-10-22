package com.romanpanin.advancedalarm.ui

import android.os.Bundle
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.romanpanin.advancedalarm.R
import com.romanpanin.advancedalarm.data.Days
import com.romanpanin.advancedalarm.data.Settings
import com.romanpanin.advancedalarm.databinding.ActivitySettingsBinding
import com.romanpanin.advancedalarm.service.AlarmService

/** Настройки: длительность блокировки, число задач, аварийный PIN, рабочие дни. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = Settings.of(this)

        binding.lockDurationInput.setText(settings.lockDurationMinutes.toString())
        binding.taskCountInput.setText(settings.quizTaskCount.toString())
        binding.groupWindowInput.setText(settings.groupWindowMinutes.toString())
        binding.ringDurationInput.setText(settings.ringDurationSeconds.toString())
        binding.emergencyPinInput.setText(settings.emergencyPin)
        binding.hardModeSwitch.isChecked = settings.hardMode
        binding.allowedPackagesInput.setText(settings.allowedPackages.joinToString(", "))

        val dayBoxes = mapOf(
            R.id.workMonday to Days.MONDAY,
            R.id.workTuesday to Days.TUESDAY,
            R.id.workWednesday to Days.WEDNESDAY,
            R.id.workThursday to Days.THURSDAY,
            R.id.workFriday to Days.FRIDAY
        )
        dayBoxes.forEach { (viewId, day) ->
            val box = findViewById<CheckBox>(viewId)
            box.isChecked = day in settings.workingDaysNoLock
            box.setOnCheckedChangeListener { _, checked ->
                val current = settings.workingDaysNoLock.toMutableSet()
                if (checked) current.add(day) else current.remove(day)
                settings.workingDaysNoLock = current
            }
        }

        binding.saveButton.setOnClickListener { save() }
        binding.testButton.setOnClickListener {
            save()
            AlarmService.startTest(this, 1)
        }
    }

    private fun save() {
        settings.lockDurationMinutes = binding.lockDurationInput.text.toString()
            .toIntOrNull() ?: settings.lockDurationMinutes
        settings.quizTaskCount = binding.taskCountInput.text.toString()
            .toIntOrNull() ?: settings.quizTaskCount
        settings.groupWindowMinutes = binding.groupWindowInput.text.toString()
            .toIntOrNull() ?: settings.groupWindowMinutes
        settings.ringDurationSeconds = binding.ringDurationInput.text.toString()
            .toIntOrNull() ?: settings.ringDurationSeconds
        settings.emergencyPin = binding.emergencyPinInput.text.toString().trim()
            .ifEmpty { Settings.DEFAULT_PIN }
        settings.hardMode = binding.hardModeSwitch.isChecked
        settings.allowedPackages = binding.allowedPackagesInput.text.toString()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }
}
