package dev.heynol.barhud.service

/**
 * Which package is in front, going by window state events.
 *
 * During an app launch the launcher often reports one more window state change about 0.5 s after
 * the app's first event, with the app already on screen. The app usually doesn't report itself
 * again afterwards, so waiting for it to come back isn't enough: the event has to be dropped. It
 * names a framework view (`android.widget.FrameLayout`), while really going home names the
 * launcher's activity, so going home right after a launch still counts. Every other switch applies
 * at once.
 */
class ForegroundPackage {
    var current: String? = null
        private set

    private var appInFrontSince = NEVER

    /** Returns whether [current] changed. */
    fun onWindowEvent(pkg: String, isHome: Boolean, className: String?, nowMs: Long): Boolean {
        if (pkg == current) return false
        if (isHome) {
            val framework = className == null || className.startsWith("android.")
            if (framework && nowMs - appInFrontSince < LAUNCH_NOISE_MS) return false
        } else {
            appInFrontSince = nowMs
        }
        current = pkg
        return true
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE / 2
        const val LAUNCH_NOISE_MS = 1_500L
    }
}
