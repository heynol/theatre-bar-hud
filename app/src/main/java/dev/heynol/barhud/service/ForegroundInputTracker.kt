package dev.heynol.barhud.service

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Works out which input is on screen, as a stable key for [dev.heynol.barhud.volume.InputVolumeMemory]:
 * `app:<package>` for TV apps, `hdmi:<port>` for HDMI inputs (all of which run inside Xiaomi's
 * LiveTV app; the port comes from the `TvSourcesStatus` global setting, e.g. "hdmi1|HDR10"), or
 * null on the home screen.
 *
 * Only launchable TV apps count as inputs. Overlays that open on top of an app (quick settings,
 * keyboard, assistant, our own setup screen) don't change the current input.
 *
 * Main thread only.
 */
class ForegroundInputTracker(
    private val context: Context,
    private val onInputChanged: (String?) -> Unit,
) {
    var current: String? = null
        private set

    private var foregroundPackage: String? = null
    private val launchableCache = HashMap<String, Boolean>()

    private val sourceObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (foregroundPackage == LIVE_TV_PACKAGE) update()
        }
    }

    fun start() {
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(KEY_TV_SOURCE), false, sourceObserver)
    }

    fun stop() {
        context.contentResolver.unregisterContentObserver(sourceObserver)
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != foregroundPackage && (pkg in HOME_PACKAGES || isLaunchableTvApp(pkg))) {
            foregroundPackage = pkg
            update()
        }
    }

    private fun update() {
        val pkg = foregroundPackage
        val input = when {
            pkg == null || pkg in HOME_PACKAGES -> null
            pkg == LIVE_TV_PACKAGE -> currentTvSource()?.let { "hdmi:$it" } ?: "app:$pkg"
            else -> "app:$pkg"
        }
        if (input != current) {
            Log.d(TAG, "input $current -> $input")
            current = input
            onInputChanged(input)
        }
    }

    private fun currentTvSource(): String? =
        runCatching { Settings.Global.getString(context.contentResolver, KEY_TV_SOURCE) }
            .getOrNull()
            ?.substringBefore('|')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun isLaunchableTvApp(pkg: String): Boolean = launchableCache.getOrPut(pkg) {
        pkg != context.packageName &&
            pkg !in IGNORED_PACKAGES &&
            context.packageManager.getLeanbackLaunchIntentForPackage(pkg) != null
    }

    companion object {
        private const val TAG = "ForegroundInputTracker"
        private const val KEY_TV_SOURCE = "TvSourcesStatus"
        private const val LIVE_TV_PACKAGE = "com.mitv.livetv"

        private val HOME_PACKAGES = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.mitv.tvhome.atv",
        )

        // Launchable, but opened on top of whatever plays: keep the previous input.
        private val IGNORED_PACKAGES = setOf(
            "com.android.tv.settings",
            "com.google.android.katniss",
            "com.xiaomi.tvqs.overseas.y24",
        )

        /** Human-readable label for an input key, for the setup screen. */
        fun label(context: Context, input: String): String = when {
            input.startsWith("hdmi:") -> input.removePrefix("hdmi:").uppercase().replace("HDMI", "HDMI ")
            input.startsWith("app:") -> {
                val pkg = input.removePrefix("app:")
                runCatching {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
            }
            else -> input
        }
    }
}
