package com.romanpanin.advancedalarm.lock

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.romanpanin.advancedalarm.R
import com.romanpanin.advancedalarm.alarm.SnoozePolicy
import com.romanpanin.advancedalarm.data.AlarmStore
import com.romanpanin.advancedalarm.data.Settings
import com.romanpanin.advancedalarm.databinding.ActivityLockBinding
import com.romanpanin.advancedalarm.util.Insets
import com.romanpanin.advancedalarm.quiz.QuizProblem
import com.romanpanin.advancedalarm.service.AlarmService
import com.romanpanin.advancedalarm.service.LockState
import com.romanpanin.advancedalarm.util.CameraLauncher
import com.romanpanin.advancedalarm.util.Num
import com.romanpanin.advancedalarm.util.TorchController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Экран блокировки. Показывается поверх экрана блокировки системы, не даёт
 * уйти в другие приложения и показывает задачи, когда время блокировки истекло.
 */
class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private val inputFields = mutableListOf<EditText>()
    private var pendingHint: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showAboveLockScreen()

        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Insets.applyCutout(binding.root)

        binding.torchButton.setOnClickListener { toggleTorch() }
        binding.cameraButton.setOnClickListener { openCamera() }
        binding.emergencyButton.setOnClickListener { askEmergencyPin() }
        binding.stopButton.setOnClickListener { onStopClicked() }
        binding.snoozeButton.setOnClickListener { snoozeAlarm() }
        binding.quizCheckButton.setOnClickListener { checkAnswer() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (LockState.isActive()) {
                    Toast.makeText(
                        this@LockActivity,
                        R.string.lock_back_blocked,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                LockState.state.collect { render(it) }
            }
        }
        lifecycleScope.launch {
            while (isActive) {
                updateClock()
                updateCountdown()
                delay(500)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun showAboveLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun hideSystemUi() {
        val controller = WindowCompat.getInsetsController(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun render(state: LockState.Snapshot) {
        when (state.phase) {
            LockState.Phase.IDLE -> {
                finishAffinity()
            }

            LockState.Phase.RINGING -> {
                binding.statusText.text = getString(
                    if (state.ringOnly) R.string.lock_ring_only else R.string.lock_alarm
                )
                binding.countdownText.visibility = View.GONE
                binding.quizContainer.visibility = View.GONE
                binding.emergencyButton.visibility =
                    if (state.ringOnly) View.GONE else View.VISIBLE
                binding.actionRow.visibility = View.VISIBLE
                // Без блокировки кнопка выключает будильник целиком,
                // с блокировкой — только звонок.
                binding.stopButton.setText(
                    if (state.ringOnly) R.string.action_stop_alarm else R.string.action_stop_ring
                )
                val snoozeVisible = canSnooze(state)
                binding.snoozeButton.visibility = if (snoozeVisible) View.VISIBLE else View.GONE
                if (snoozeVisible) binding.snoozeButton.text = snoozeLabel()
            }

            LockState.Phase.LOCKED -> {
                binding.statusText.text = getString(R.string.lock_blocked)
                binding.countdownText.visibility = View.VISIBLE
                binding.quizContainer.visibility = View.GONE
                binding.emergencyButton.visibility = View.VISIBLE
                binding.actionRow.visibility = View.GONE
            }

            LockState.Phase.QUIZ -> {
                binding.statusText.text = getString(R.string.lock_quiz)
                binding.countdownText.visibility = View.GONE
                binding.quizContainer.visibility = View.VISIBLE
                binding.emergencyButton.visibility = View.VISIBLE
                binding.actionRow.visibility = View.GONE
                showProblem(state)
            }
        }
    }

    private fun showProblem(state: LockState.Snapshot) {
        val problem = state.quiz.getOrNull(state.quizIndex)
        if (problem == null) {
            unlockEverything()
            return
        }

        binding.quizProgress.text = getString(
            R.string.quiz_progress,
            state.quizIndex + 1,
            state.quiz.size
        )
        binding.quizProblem.text = problem.text
        binding.quizHint.text = pendingHint.orEmpty()
        pendingHint = null

        buildInputs(problem)
    }

    private fun buildInputs(problem: QuizProblem) {
        binding.quizInputs.removeAllViews()
        inputFields.clear()

        val density = resources.displayMetrics.density
        problem.parts.forEach { part ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (10 * density).toInt() }
            }

            val label = TextView(this).apply {
                text = getString(R.string.quiz_variable, part.label)
                textSize = 22f
                setTextColor(getColor(R.color.lock_text))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED
                hint = "0"
                setTextColor(getColor(R.color.lock_text))
                setHintTextColor(getColor(R.color.lock_text_dim))
                setBackgroundResource(R.drawable.bg_lock_input)
                gravity = Gravity.CENTER
                textSize = 22f
                layoutParams = LinearLayout.LayoutParams(
                    (150 * density).toInt(),
                    (58 * density).toInt()
                ).apply { marginStart = (12 * density).toInt() }
            }

            row.addView(label)
            row.addView(input)
            binding.quizInputs.addView(row)
            inputFields.add(input)
        }
    }

    private fun checkAnswer() {
        val state = LockState.current()
        val problem = state.quiz.getOrNull(state.quizIndex) ?: return

        val raw = inputFields.map { it.text.toString() }
        if (raw.any { it.isBlank() }) {
            binding.quizHint.text = getString(R.string.quiz_fill_all)
            return
        }
        val values = raw.map { Num.parse(it) }
        if (values.any { it == null }) {
            binding.quizHint.text = getString(R.string.quiz_bad_number)
            return
        }

        if (problem.matches(values.filterNotNull())) {
            pendingHint = getString(R.string.quiz_correct)
            LockState.advanceQuiz()
            if (LockState.current().quizIndex >= LockState.current().quiz.size) {
                unlockEverything()
            }
        } else {
            LockState.addMistake()
            binding.quizHint.text = getString(R.string.quiz_wrong)
        }
    }

    private fun unlockEverything() {
        LockState.release(this)
        AllowedPackages.clear()
        TorchController.reset()
        stopService(Intent(this, AlarmService::class.java))
        Toast.makeText(this, R.string.quiz_unlocked, Toast.LENGTH_LONG).show()
        finishAffinity()
    }

    private fun askEmergencyPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "••••"
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding / 2, padding, padding / 2)

        AlertDialog.Builder(this)
            .setTitle(R.string.emergency_pin_title)
            .setMessage(R.string.emergency_pin_message)
            .setView(input)
            .setPositiveButton(R.string.action_unlock) { _, _ ->
                val value = input.text.toString().trim()
                if (value == Settings.of(this).emergencyPin) {
                    unlockEverything()
                } else {
                    Toast.makeText(this, R.string.emergency_pin_wrong, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun onStopClicked() {
        if (LockState.current().ringOnly) {
            dismissAlarm()
        } else {
            stopAlarmSound()
        }
    }

    /** Гасит звонок, оставляя блокировку в силе. */
    private fun stopAlarmSound() {
        startService(
            Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP_SOUND)
        )
    }

    /**
     * Перенос будильника: гасит звонок и откладывает следующий звонок.
     * Доступен только пока идёт звонок — перенос отменяет начало блокировки.
     */
    private fun snoozeAlarm() {
        val state = LockState.current()
        if (!canSnooze(state)) {
            Toast.makeText(this, R.string.snooze_limit_reached, Toast.LENGTH_SHORT).show()
            return
        }
        val minutes = Settings.of(this).snoozeMinutes
        startService(
            Intent(this, AlarmService::class.java)
                .setAction(AlarmService.ACTION_SNOOZE)
                .putExtra(AlarmService.EXTRA_ALARM_ID, state.alarmId)
        )
        Toast.makeText(
            this,
            resources.getQuantityString(R.plurals.snooze_done, minutes, minutes),
            Toast.LENGTH_SHORT
        ).show()
    }

    /** Перенос возможен только у настоящего будильника и в пределах лимита. */
    private fun canSnooze(state: LockState.Snapshot): Boolean {
        if (state.alarmId.isBlank()) return false
        val alarm = AlarmStore(this).get(state.alarmId) ?: return false
        val settings = Settings.of(this)
        return SnoozePolicy.canSnooze(alarm.snoozeCount, settings.maxSnoozes)
    }

    private fun snoozeLabel(): String {
        val minutes = Settings.of(this).snoozeMinutes
        return resources.getQuantityString(R.plurals.action_snooze, minutes, minutes)
    }

    private fun dismissAlarm() {
        stopService(Intent(this, AlarmService::class.java))
        LockState.release(this)
        AllowedPackages.clear()
        finishAffinity()
    }

    private fun toggleTorch() {
        when (val result = TorchController.toggle(this)) {
            null -> Toast.makeText(this, R.string.torch_unavailable, Toast.LENGTH_SHORT).show()
            true -> binding.torchButton.text = getString(R.string.action_torch_off)
            false -> binding.torchButton.text = getString(R.string.action_torch_on)
        }
    }

    private fun openCamera() {
        if (!CameraLauncher.launch(this)) {
            Toast.makeText(this, R.string.camera_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCountdown() {
        val state = LockState.current()
        if (state.phase != LockState.Phase.LOCKED) return

        val remaining = state.unlockAt - System.currentTimeMillis()
        val totalSeconds = (remaining / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        binding.countdownText.text = getString(
            R.string.lock_countdown,
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        )
    }

    private fun updateClock() {
        val now = Calendar.getInstance()
        binding.clockText.text = String.format(
            Locale.US,
            "%02d:%02d",
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE)
        )
        binding.dateText.text = String.format(
            Locale.US,
            "%s, %s",
            SimpleDateFormat("EEEE", Locale.getDefault()).format(now.time),
            SimpleDateFormat("d MMMM", Locale.getDefault()).format(now.time)
        )
    }
}
