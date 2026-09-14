package dev.heynol.barhud.volume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Reads the soundbar's volume from the TV itself.
 *
 * With an eARC/ARC audio system Android 14 uses "adjust-only absolute volume": a volume key is
 * sent to the soundbar as a CEC `<User Control Pressed>`, the soundbar answers with
 * `<Report Audio Status>` and Android stores that value as the media stream index for the
 * HDMI (e)ARC device. So [AudioManager] already knows the soundbar's real volume, no network
 * needed.
 *
 * Main thread only.
 */
class TvVolumeSource(private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var listener: ((VolumeState) -> Unit)? = null
    private var lastAudibleVolume = 0
    private var lastNotified: VolumeState? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val stream = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_MUSIC)
            if (stream == AudioManager.STREAM_MUSIC) notifyListener()
        }
    }

    // Backup signal: AudioService persists every index change to Settings.System.
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = notifyListener()
    }

    fun start(onChange: (VolumeState) -> Unit) {
        listener = onChange
        val filter = IntentFilter().apply {
            addAction(ACTION_VOLUME_CHANGED)
            addAction(ACTION_STREAM_MUTE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        for (key in PERSISTED_KEYS) {
            context.contentResolver.registerContentObserver(Settings.System.getUriFor(key), false, observer)
        }
    }

    fun stop() {
        listener = null
        runCatching { context.unregisterReceiver(receiver) }
        context.contentResolver.unregisterContentObserver(observer)
    }

    fun read(): VolumeState {
        val muted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        val volume = if (muted) {
            // A muted stream reports 0; the native HUD keeps showing the level, greyed out. Mute
            // doesn't touch the stored index, which AudioService persists per output device.
            PERSISTED_KEYS.firstNotNullOfOrNull { key ->
                Settings.System.getInt(context.contentResolver, key, -1).takeIf { it >= 0 && isSoundbarActive() }
            } ?: lastAudibleVolume
        } else {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).also { lastAudibleVolume = it }
        }
        return VolumeState(
            volume = volume,
            minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC),
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            muted = muted,
        )
    }

    /**
     * One volume step up (+1) or down (-1). With adjust-only absolute volume this goes to the
     * soundbar as CEC `<User Control Pressed>`; `setStreamVolume` is ignored in that mode.
     */
    fun adjust(direction: Int) {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0,
        )
    }

    /** Unmutes the media stream; goes to the soundbar over CEC like [adjust]. */
    fun unmute() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
    }

    /** True while media plays through an HDMI ARC/eARC audio system (the soundbar is on). */
    fun isSoundbarActive(): Boolean {
        val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            audioManager.getAudioDevicesForAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build(),
            ).map { it.type }
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
        }
        return devices.any { it in HDMI_AUDIO_SYSTEM_TYPES }.also { active ->
            // Enable with: adb shell setprop log.tag.TvVolumeSource DEBUG
            if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "media output types=$devices soundbarActive=$active")
        }
    }

    /** The broadcast and the settings observer both fire for one change; report it once. */
    private fun notifyListener() {
        val state = read()
        if (state == lastNotified) return
        lastNotified = state
        listener?.invoke(state)
    }

    private companion object {
        const val TAG = "TvVolumeSource"

        // Sent by AudioService; not part of the public SDK constants but stable since API 1.
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val ACTION_STREAM_MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

        val PERSISTED_KEYS = listOf("volume_music_hdmi_earc", "volume_music_hdmi_arc")

        val HDMI_AUDIO_SYSTEM_TYPES = buildSet {
            add(AudioDeviceInfo.TYPE_HDMI_ARC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_HDMI_EARC)
        }
    }
}
