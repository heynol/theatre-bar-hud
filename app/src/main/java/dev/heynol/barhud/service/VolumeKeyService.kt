package dev.heynol.barhud.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
 * Watches the soundbar volume and which app or HDMI input is in front, and optionally maps the
 * Netflix button of Google TV remotes.
 *
 * Key filtering is only requested while that Netflix mapping is on. Once any service filters
 * keys, Android's KeyboardInterceptor runs the window policy's `interceptKeyBeforeDispatching` for
 * volume keys ahead of normal dispatch. Xiaomi's MitvAudioService counts volume key events there,
 * so one press can be seen twice and trigger its "double down = mute". On Xiaomi the volume keys
 * never reach [onKeyEvent] either way (the policy consumes them in that early call).
 */
class VolumeKeyService : AccessibilityService() {

    private var scope: CoroutineScope? = null
    private var hud: HudOverlay? = null
    private var controller: VolumeController? = null
    private var inputTracker: ForegroundInputTracker? = null
    private var remoteSettings: RemoteSettings? = null
    private val remoteSettingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyKeyFiltering() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        val hud = HudOverlay(this).also { hud = it }
        val remoteSettings = RemoteSettings(this).also { remoteSettings = it }
        remoteSettings.registerListener(remoteSettingsListener)
        applyKeyFiltering()
        val controller = VolumeController(TvVolumeSource(this), InputVolumeMemory(this), hud, scope)
            .also { controller = it; it.start() }
        inputTracker = ForegroundInputTracker(this, controller::onInputChanged).also { it.start() }
        instance = WeakReference(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = when (event.keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> {
            val controller = controller
            when {
                controller == null -> false
                event.action == KeyEvent.ACTION_DOWN -> false.also { controller.onVolumeKeyDown(event.keyCode) }
                event.action == KeyEvent.ACTION_UP -> false.also { controller.onVolumeKeyUp(event.keyCode) }
                else -> false
            }
        }
        KeyEvent.KEYCODE_BUTTON_4 -> onNetflixButton(event)
        else -> false
    }

    private fun applyKeyFiltering() {
        val info = serviceInfo ?: return
        val filter = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        val flags = if (remoteSettings?.netflixButton == true) info.flags or filter else info.flags and filter.inv()
        if (flags != info.flags) {
            info.flags = flags
            serviceInfo = info
            Log.i(TAG, "key filtering ${if (flags and filter != 0) "on" else "off"}")
        }
    }

    /** Google TV remotes label BUTTON_4 "Netflix" (Vendor_18d1_Product_9451.kl). */
    private fun onNetflixButton(event: KeyEvent): Boolean {
        if (remoteSettings?.netflixButton != true) return false
        if (packageManager.getLeanbackLaunchIntentForPackage(NETFLIX_PACKAGE) == null) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) launchNetflix()
        return true
    }

    private fun launchNetflix() {
        val launch = packageManager.getLeanbackLaunchIntentForPackage(NETFLIX_PACKAGE) ?: return
        try {
            // Bound accessibility services may start activities from the background.
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Netflix not launchable", e)
        }
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
        remoteSettings?.unregisterListener(remoteSettingsListener)
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

        /** Plays the scripted demo on the HUD, see [DemoReceiver]. Returns false if the service isn't running. */
        fun demo(from: Int, to: Int, stepMs: Long): Boolean {
            val service = instance?.get() ?: return false
            service.controller?.demo(from, to, stepMs) ?: return false
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
