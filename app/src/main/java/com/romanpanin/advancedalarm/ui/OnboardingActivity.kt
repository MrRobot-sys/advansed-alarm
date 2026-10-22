package com.romanpanin.advancedalarm.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.romanpanin.advancedalarm.R
import com.romanpanin.advancedalarm.data.Settings
import com.romanpanin.advancedalarm.databinding.ActivityOnboardingBinding
import com.romanpanin.advancedalarm.lock.LockController

/**
 * Чеклист первичной настройки: какие разрешения нужно выдать вручную,
 * чтобы блокировка работала по-настоящему.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.accessibilityButton.setOnClickListener {
            LockController.openAccessibilitySettings(this)
        }
        binding.deviceAdminButton.setOnClickListener {
            LockController.openDeviceAdminSettings(this)
        }
        binding.notificationButton.setOnClickListener {
            LockController.openNotificationSettings(this)
        }
        binding.batteryButton.setOnClickListener {
            LockController.openBatteryOptimizationSettings(this)
        }
        binding.fullScreenButton.setOnClickListener {
            LockController.openFullScreenIntentSettings(this)
        }
        binding.securityButton.setOnClickListener {
            LockController.openSecuritySettings(this)
        }
        binding.finishButton.setOnClickListener {
            Settings.of(this).onboardingShown = true
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val accessibilityEnabled = AccessibilityState.isEnabled(this)
        val adminEnabled = LockController.isDeviceAdminActive(this)
        val secure = LockController.isDeviceSecure(this)

        binding.statusText.text = getString(
            R.string.onboarding_status,
            statusWord(accessibilityEnabled),
            statusWord(adminEnabled),
            statusWord(secure)
        )
    }

    private fun statusWord(ok: Boolean): String =
        getString(if (ok) R.string.onboarding_ok else R.string.onboarding_missing)
}
