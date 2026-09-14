package dev.heynol.barhud.service

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Toggles for how the service treats remote buttons. */
class RemoteSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("remote", Context.MODE_PRIVATE)

    /**
     * Handle volume up/down ourselves while the soundbar is active, instead of passing them to
     * Xiaomi's key handling (which mutes on a quick double "down" and barely repeats when held).
     */
    var ownVolumeKeys: Boolean
        get() = prefs.getBoolean(KEY_OWN_VOLUME_KEYS, true)
        set(value) = prefs.edit { putBoolean(KEY_OWN_VOLUME_KEYS, value) }

    /** Netflix button of Google TV remotes (`BUTTON_4`), which Xiaomi's firmware ignores. */
    var netflixButton: Boolean
        get() = prefs.getBoolean(KEY_NETFLIX_BUTTON, true)
        set(value) = prefs.edit { putBoolean(KEY_NETFLIX_BUTTON, value) }

    private companion object {
        const val KEY_OWN_VOLUME_KEYS = "own_volume_keys"
        const val KEY_NETFLIX_BUTTON = "netflix_button"
    }
}
