package dev.heynol.barhud.service

/**
 * Which package is in front, going by window state events.
 *
 * During an app launch the launcher can report one more window state change about 0.5 s after the
 * app's first event, with the app already on screen. The app doesn't always report itself again
 * afterwards, so waiting for it to come back isn't enough: the event has to be dropped. Only the
 * launcher is held to this, every other switch applies at once.
 *
 * Going home within [LAUNCH_NOISE_MS] of a launch looks the same and is dropped too, until the
 * launcher's next event. That is rare, while the launcher's event cancels the restore of the app.
 */
class ForegroundPackage {
    var current: String? = null
        private set

    private var appInFrontSince = NEVER

    /** Returns whether [current] changed. */
    fun onWindowEvent(pkg: String, isHome: Boolean, nowMs: Long): Boolean {
        if (pkg == current) return false
        if (isHome) {
            if (nowMs - appInFrontSince < LAUNCH_NOISE_MS) return false
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
