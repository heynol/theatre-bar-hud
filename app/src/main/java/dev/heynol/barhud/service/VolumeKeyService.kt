package dev.heynol.barhud.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
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
 * Observes the remote's volume keys and which app or HDMI input is in front. Every key is passed
 * through untouched, so the TV keeps controlling the soundbar over eARC/CEC.
 */
class VolumeKeyService : AccessibilityService() {

    private var scope: CoroutineScope? = null
    private var hud: HudOverlay? = null
    private var controller: VolumeController? = null
    private var inputTracker: ForegroundInputTracker? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        val hud = HudOverlay(this).also { hud = it }
        val controller = VolumeController(TvVolumeSource(this), InputVolumeMemory(this), hud, scope)
            .also { controller = it; it.start() }
        inputTracker = ForegroundInputTracker(this, controller::onInputChanged).also { it.start() }
        instance = WeakReference(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
            keyCode != KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            return false
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> controller?.onVolumeKeyDown(keyCode)
            KeyEvent.ACTION_UP -> controller?.onVolumeKeyUp(keyCode)
        }
        return false
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
