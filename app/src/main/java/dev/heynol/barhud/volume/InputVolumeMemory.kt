package dev.heynol.barhud.volume

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Remembered soundbar volume per input ("app:com.netflix.ninja", "hdmi:hdmi1"). */
class InputVolumeMemory(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("input_volume", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    operator fun get(input: String): Int? =
        prefs.getInt(PREFIX + input, -1).takeIf { it >= 0 }

    operator fun set(input: String, volume: Int) {
        if (get(input) != volume) prefs.edit { putInt(PREFIX + input, volume) }
    }

    fun all(): Map<String, Int> =
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(PREFIX) && value is Int) key.removePrefix(PREFIX) to value else null
        }.toMap()

    fun clear() = prefs.edit {
        all().keys.forEach { remove(PREFIX + it) }
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val PREFIX = "volume:"
    }
}
