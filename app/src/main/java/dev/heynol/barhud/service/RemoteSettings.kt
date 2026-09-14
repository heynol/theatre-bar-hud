package dev.heynol.barhud.service

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Toggles for how the service treats remote buttons. */
class RemoteSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("remote", Context.MODE_PRIVATE)

    /**
     * Netflix button of Google TV remotes (`BUTTON_4`), which Xiaomi's firmware ignores. Needs
     * accessibility key filtering, which has a side effect on Xiaomi; see [VolumeKeyService].
     */
    var netflixButton: Boolean
        get() = prefs.getBoolean(KEY_NETFLIX_BUTTON, false)
        set(value) = prefs.edit { putBoolean(KEY_NETFLIX_BUTTON, value) }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private companion object {
        const val KEY_NETFLIX_BUTTON = "netflix_button"
    }
}
