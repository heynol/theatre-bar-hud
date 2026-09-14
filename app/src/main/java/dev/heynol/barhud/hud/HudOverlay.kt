package dev.heynol.barhud.hud

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import dev.heynol.barhud.R
import dev.heynol.barhud.volume.VolumeState
import kotlin.math.abs

/**
 * Owns the HUD window: creation, fade in/out and auto-hide.
 *
 * Hosted by a [Dialog] configured like Xiaomi's own VolumVerticalDialog (wrap-content window,
 * transparent background, right edge). It is never focusable or touchable, so playback keeps input
 * focus. The window stays attached for the service's lifetime; see [setWindowVisible].
 *
 * Main thread only.
 */
class HudOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var content = HudContent.create(context)
    private val container = FrameLayout(context).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(content.root)
    }
    private var lastState: VolumeState? = null

    private var dialog: Dialog? = null
    private var windowAttached = false

    private var presence = 0f
    private var targetPresence = 0f
    private var animator: ValueAnimator? = null
    private val hideRunnable = Runnable { animatePresence(0f) }

    /** Window types in order of preference; the first one that attaches wins. */
    private val candidateTypes = buildList {
        if (context is AccessibilityService) add(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        add(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
    }
    private var typeIndex = 0

    init {
        applyPresence(0f)
        // Attach now, invisibly, so the first key press doesn't pay for window + GPU setup.
        handler.post { ensureWindowAttached() }
    }

    val isVisible: Boolean get() = targetPresence > 0f

    /** Shows (or updates) the HUD and restarts the auto-hide timer. */
    fun show(state: VolumeState?) {
        lastState = state
        // Appearing from fully hidden snaps the fill; while visible it eases to the new value.
        content.bind(state, animate = presence > 0f)
        if (!ensureWindowAttached()) return
        setWindowVisible(true)
        animatePresence(1f)
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, HudStyle.AUTO_HIDE_MS)
    }

    /** Re-reads SystemUI's themed resources, e.g. after the system theme changed. */
    fun reloadContent() {
        container.removeView(content.root)
        content = HudContent.create(context)
        container.addView(content.root)
        content.bind(lastState, animate = false)
    }

    fun release() {
        handler.removeCallbacks(hideRunnable)
        animator?.cancel()
        dialog?.dismiss()
        dialog = null
        windowAttached = false
    }

    private fun ensureWindowAttached(): Boolean {
        if (windowAttached) return true
        while (typeIndex < candidateTypes.size) {
            val type = candidateTypes[typeIndex]
            val host = dialog ?: createDialog(type)
            if (host != null) {
                try {
                    host.show()
                    dialog = host
                    windowAttached = true
                    return true
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Cannot attach HUD window of type $type", e)
                    runCatching { host.dismiss() }
                }
            }
            dialog = null
            typeIndex++
        }
        typeIndex = 0 // e.g. overlay permission granted later: try the whole chain again next time
        return false
    }

    private fun createDialog(type: Int): Dialog? {
        val token: IBinder? = when (type) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY -> accessibilityWindowToken() ?: return null
            else -> if (Settings.canDrawOverlays(context)) null else return null
        }
        (container.parent as? ViewGroup)?.removeView(container)
        return Dialog(ContextThemeWrapper(context, R.style.Theme_BarHud_Overlay)).apply {
            setCancelable(false)
            setContentView(
                container,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            val window = checkNotNull(window)
            window.setType(type)
            window.setFormat(PixelFormat.TRANSLUCENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            )
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setGravity(HudStyle.GRAVITY)
            window.attributes = window.attributes.also {
                it.token = token
                it.windowAnimations = 0
                it.alpha = 0f
                it.title = "TheatreBarHud"
            }
        }
    }

    /**
     * An accessibility service's own WindowManager stamps its connection token onto overlay
     * windows, but a Dialog uses a derived WindowManager that doesn't. Attaching a 1px probe
     * through the service's WindowManager reveals the token so the Dialog can carry it.
     */
    private fun accessibilityWindowToken(): IBinder? {
        val probe = View(context)
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        )
        return try {
            windowManager.addView(probe, params)
            params.token
        } catch (e: RuntimeException) {
            Log.w(TAG, "Accessibility overlay unavailable", e)
            null
        } finally {
            if (probe.parent != null) runCatching { windowManager.removeViewImmediate(probe) }
        }
    }

    private fun animatePresence(target: Float) {
        if (targetPresence == target && (animator?.isRunning == true || presence == target)) return
        targetPresence = target
        animator?.cancel()
        val appearing = target > presence
        val fullDuration = if (appearing) HudStyle.FADE_IN_MS else HudStyle.FADE_OUT_MS
        animator = ValueAnimator.ofFloat(presence, target).apply {
            duration = (fullDuration * abs(target - presence)).toLong()
            interpolator = if (appearing) ENTER else EXIT
            addUpdateListener { applyPresence(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled && target == 0f) setWindowVisible(false)
                }
            })
            start()
        }
    }

    /**
     * Hidden HUD = window alpha 0, not a detached or GONE window. SurfaceFlinger skips zero-alpha
     * layers entirely, so nothing is composited over the video, yet the surface and the
     * process's EGL context stay alive. Removing the window instead makes every appearance
     * re-create both, a visible multi-frame delay on TV SoCs.
     */
    private fun setWindowVisible(visible: Boolean) {
        val window = dialog?.window ?: return
        val alpha = if (visible) 1f else 0f
        if (window.attributes.alpha != alpha) window.attributes = window.attributes.also { it.alpha = alpha }
    }

    private fun applyPresence(value: Float) {
        presence = value
        container.alpha = value
    }

    private companion object {
        const val TAG = "HudOverlay"
        val ENTER = PathInterpolator(0.05f, 0.7f, 0.1f, 1f) // Material "emphasized decelerate"
        val EXIT = PathInterpolator(0.3f, 0f, 0.8f, 0.15f) // Material "emphasized accelerate"
    }
}
