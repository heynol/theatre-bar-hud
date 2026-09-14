package dev.heynol.barhud.setup

import android.app.Activity
import android.content.SharedPreferences
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import dev.heynol.barhud.R
import dev.heynol.barhud.service.ForegroundInputTracker
import dev.heynol.barhud.service.RemoteSettings
import dev.heynol.barhud.service.VolumeKeyService
import dev.heynol.barhud.volume.InputVolumeMemory
import dev.heynol.barhud.volume.TvVolumeSource

/** D-pad friendly first-run and status screen. Re-checks everything whenever it resumes. */
class SetupActivity : Activity() {

    private lateinit var volumeSource: TvVolumeSource
    private lateinit var memory: InputVolumeMemory

    private lateinit var accessibilityStatus: TextView
    private lateinit var accessibilityButton: Button
    private lateinit var overlayStatus: TextView
    private lateinit var overlayButton: Button
    private lateinit var soundbarStatus: TextView
    private lateinit var previewButton: Button
    private lateinit var inputVolumeSwitch: Switch
    private lateinit var inputVolumeList: TextView
    private lateinit var forgetVolumesButton: Button
    private lateinit var remoteSettings: RemoteSettings

    private val memoryListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> updateInputVolumes() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        volumeSource = TvVolumeSource(this)
        memory = InputVolumeMemory(this)
        remoteSettings = RemoteSettings(this)

        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        accessibilityButton = findViewById(R.id.accessibilityButton)
        overlayStatus = findViewById(R.id.overlayStatus)
        overlayButton = findViewById(R.id.overlayButton)
        soundbarStatus = findViewById(R.id.soundbarStatus)
        previewButton = findViewById(R.id.previewButton)
        inputVolumeSwitch = findViewById(R.id.inputVolumeSwitch)
        inputVolumeList = findViewById(R.id.inputVolumeList)
        forgetVolumesButton = findViewById(R.id.forgetVolumesButton)

        inputVolumeSwitch.setOnCheckedChangeListener { _, checked -> memory.enabled = checked }
        forgetVolumesButton.setOnClickListener { memory.clear() }

        findViewById<Switch>(R.id.ownVolumeKeysSwitch).apply {
            isChecked = remoteSettings.ownVolumeKeys
            setOnCheckedChangeListener { _, checked -> remoteSettings.ownVolumeKeys = checked }
        }
        findViewById<Switch>(R.id.undoDoubleDownMuteSwitch).apply {
            isChecked = remoteSettings.undoDoubleDownMute
            setOnCheckedChangeListener { _, checked -> remoteSettings.undoDoubleDownMute = checked }
        }
        findViewById<Switch>(R.id.netflixButtonSwitch).apply {
            isChecked = remoteSettings.netflixButton
            setOnCheckedChangeListener { _, checked -> remoteSettings.netflixButton = checked }
        }

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
        inputVolumeSwitch.isChecked = memory.enabled
        memory.registerListener(memoryListener)
        updateInputVolumes()

        (if (accessibilityOn) previewButton else accessibilityButton).requestFocus()
    }

    override fun onPause() {
        volumeSource.stop()
        memory.unregisterListener(memoryListener)
        super.onPause()
    }

    private fun updateSoundbarStatus() {
        soundbarStatus.text = if (volumeSource.isSoundbarActive()) {
            val volume = volumeSource.read()
            getString(
                R.string.status_soundbar_active,
                getString(R.string.soundbar_generic_name), // the eARC device's product name is the TV's own
                volume.volume,
                if (volume.muted) getString(R.string.status_soundbar_muted) else "",
            )
        } else {
            getString(R.string.status_soundbar_inactive)
        }
    }

    private fun updateInputVolumes() {
        val entries = memory.all()
            .map { (input, volume) -> ForegroundInputTracker.label(this, input) to volume }
            .sortedBy { it.first.lowercase() }
        inputVolumeList.text = if (entries.isEmpty()) {
            getString(R.string.input_volume_empty)
        } else {
            entries.joinToString("\n") { (label, volume) -> getString(R.string.input_volume_entry, label, volume) }
        }
        forgetVolumesButton.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
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
