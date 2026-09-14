package dev.heynol.barhud.setup

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import dev.heynol.barhud.R
import dev.heynol.barhud.service.VolumeKeyService
import dev.heynol.barhud.volume.TvVolumeSource

/** D-pad friendly first-run and status screen. Re-checks everything whenever it resumes. */
class SetupActivity : Activity() {

    private lateinit var volumeSource: TvVolumeSource

    private lateinit var accessibilityStatus: TextView
    private lateinit var accessibilityButton: Button
    private lateinit var overlayStatus: TextView
    private lateinit var overlayButton: Button
    private lateinit var soundbarStatus: TextView
    private lateinit var previewButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        volumeSource = TvVolumeSource(this)

        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        accessibilityButton = findViewById(R.id.accessibilityButton)
        overlayStatus = findViewById(R.id.overlayStatus)
        overlayButton = findViewById(R.id.overlayButton)
        soundbarStatus = findViewById(R.id.soundbarStatus)
        previewButton = findViewById(R.id.previewButton)

        accessibilityButton.setOnClickListener {
            openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        overlayButton.setOnClickListener {
            openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
        }
        previewButton.setOnClickListener {
            if (!VolumeKeyService.preview()) {
                Toast.makeText(this, R.string.toast_service_not_running, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val accessibilityOn = VolumeKeyService.isEnabledInSettings(this)
        val overlayOn = Settings.canDrawOverlays(this)

        accessibilityStatus.setText(if (accessibilityOn) R.string.status_enabled else R.string.status_disabled)
        overlayStatus.setText(if (overlayOn) R.string.status_granted else R.string.status_not_granted)
        overlayButton.visibility = if (overlayOn) View.GONE else View.VISIBLE
        volumeSource.start { updateSoundbarStatus() }
        updateSoundbarStatus()

        (if (accessibilityOn) previewButton else accessibilityButton).requestFocus()
    }

    override fun onPause() {
        volumeSource.stop()
        super.onPause()
    }

    private fun updateSoundbarStatus() {
        soundbarStatus.text = if (volumeSource.isSoundbarActive()) {
            val volume = volumeSource.read()
            getString(
                R.string.status_soundbar_active,
                volumeSource.outputName() ?: getString(R.string.soundbar_generic_name),
                volume.volume,
                if (volume.muted) getString(R.string.status_soundbar_muted) else "",
            )
        } else {
            getString(R.string.status_soundbar_inactive)
        }
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                // Nothing to open.
            }
            Toast.makeText(this, R.string.toast_settings_missing, Toast.LENGTH_LONG).show()
        }
    }
}
