package dev.heynol.barhud.service

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.heynol.barhud.hud.HudOverlay
import dev.heynol.barhud.volume.InputVolumeMemory
import dev.heynol.barhud.volume.TvVolumeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.lang.ref.WeakReference

/**
 * Filters the remote's keys and watches which app or HDMI input is in front.
 *
 * Volume keys go to [VolumeController] (applied by us while the soundbar is active, otherwise
 * passed through). The Netflix button of Google TV remotes opens Netflix. Everything else is
 * passed through untouched.
 */
class VolumeKeyService : AccessibilityService() {

    private var scope: CoroutineScope? = null
    private var hud: HudOverlay? = null
    private var controller: VolumeController? = null
    private var inputTracker: ForegroundInputTracker? = null
    private var remoteSettings: RemoteSettings? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        val hud = HudOverlay(this).also { hud = it }
        val remoteSettings = RemoteSettings(this).also { remoteSettings = it }
        val controller = VolumeController(TvVolumeSource(this), InputVolumeMemory(this), remoteSettings, hud, scope)
            .also { controller = it; it.start() }
        inputTracker = ForegroundInputTracker(this, controller::onInputChanged).also { it.start() }
        instance = WeakReference(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = when (event.keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> {
            val controller = controller
            when {
                controller == null -> false
                event.action == KeyEvent.ACTION_DOWN -> controller.onVolumeKeyDown(event.keyCode)
                event.action == KeyEvent.ACTION_UP -> controller.onVolumeKeyUp(event.keyCode)
                else -> false
            }
        }
        KeyEvent.KEYCODE_BUTTON_4 -> onNetflixButton(event)
        else -> false
    }

    /** Google TV remotes label BUTTON_4 "Netflix" (Vendor_18d1_Product_9451.kl). */
    private fun onNetflixButton(event: KeyEvent): Boolean {
        if (remoteSettings?.netflixButton != true) return false
        val launch = packageManager.getLeanbackLaunchIntentForPackage(NETFLIX_PACKAGE) ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            try {
                // Bound accessibility services may start activities from the background.
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Netflix not launchable", e)
            }
        }
        return true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hud?.reloadContent() // system theme / density may have changed the native HUD resources
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { inputTracker?.onAccessibilityEvent(it) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        inputTracker?.stop()
        controller?.stop()
        hud?.release()
        scope?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VolumeKeyService"
        private const val NETFLIX_PACKAGE = "com.netflix.ninja"

        @Volatile
        private var instance: WeakReference<VolumeKeyService>? = null

        /** Shows the HUD from the setup screen. Returns false if the service isn't running. */
        fun preview(): Boolean {
            val service = instance?.get() ?: return false
            service.controller?.preview() ?: return false
            return true
        }

        fun isEnabledInSettings(context: Context): Boolean {
            val expected = ComponentName(context, VolumeKeyService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
