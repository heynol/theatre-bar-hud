package dev.heynol.barhud.service

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import dev.heynol.barhud.hud.HudOverlay
import dev.heynol.barhud.volume.TvVolumeSource
import dev.heynol.barhud.volume.VolumeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the HUD in sync with the soundbar.
 *
 * Latency strategy: a key press updates the HUD *immediately* with a predicted value (last known
 * volume ± 1). The soundbar's real value follows over HDMI-CEC a few hundred milliseconds later
 * and silently corrects the prediction. While keys are still arriving, reports are held back
 * ([SETTLE_MS]) because they describe an older press and would make the bar stutter backwards.
 *
 * Main thread only.
 */
class VolumeController(
    private val source: TvVolumeSource,
    private val hud: HudOverlay,
    private val scope: CoroutineScope,
) {
    private var state: VolumeState? = null
    private var soundbarWasActive = false
    private var lastKeyAt = NEVER

    private var pending: VolumeState? = null
    private var pendingArrivedAt = NEVER
    private var settleJob: Job? = null

    private val downKeys = HashSet<Int>()

    fun start() {
        state = source.read()
        soundbarWasActive = source.isSoundbarActive()
        source.start(::onSystemReport)
    }

    fun stop() {
        source.stop()
        settleJob?.cancel()
    }

    /**
     * Every ACTION_DOWN from the accessibility key filter. Keys are never consumed: the TV keeps
     * sending them to the soundbar over CEC.
     *
     * Only the first DOWN of a press is predicted. A held key is paced by the TV, which waits for
     * each `<Report Audio Status>` before sending the next step (~2-3 steps/s), while the remote
     * repeats far faster. Predicting repeats would run ahead of the soundbar and then snap back,
     * so during a hold the HUD just stays up and shows the reported values live.
     */
    fun onVolumeKeyDown(keyCode: Int) {
        val repeat = !downKeys.add(keyCode)
        if (!repeat) Log.d(TAG, "key down $keyCode")
        // TV speakers have their own native HUD; only stand in for the soundbar.
        if (!source.isSoundbarActive()) return
        if (repeat) {
            if (keyCode != KeyEvent.KEYCODE_VOLUME_MUTE) hud.show(state)
            return
        }

        lastKeyAt = SystemClock.uptimeMillis()
        val current = state ?: source.read()
        val predicted = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> current.stepped(+1)
            KeyEvent.KEYCODE_VOLUME_DOWN -> current.stepped(-1)
            else -> current.copy(muted = !current.muted)
        }
        state = predicted
        hud.show(predicted)
    }

    fun onVolumeKeyUp(keyCode: Int) {
        downKeys -= keyCode
    }

    fun preview() {
        hud.show(state ?: source.read())
    }

    private fun onSystemReport(reported: VolumeState) {
        val soundbarActive = source.isSoundbarActive()
        if (soundbarActive != soundbarWasActive) {
            // Output switched (soundbar on/off): the index belongs to another device now.
            soundbarWasActive = soundbarActive
            state = reported
            return
        }
        if (!soundbarActive) {
            state = reported
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastKeyAt >= SETTLE_MS) {
            commit(reported)
            return
        }
        pending = reported
        pendingArrivedAt = now
        if (settleJob?.isActive == true) return
        settleJob = scope.launch {
            while (SystemClock.uptimeMillis() - lastKeyAt < SETTLE_MS) {
                delay(SETTLE_MS - (SystemClock.uptimeMillis() - lastKeyAt))
            }
            pending = null
            // Re-read instead of using the held report: it may predate the latest press.
            commit(source.read())
        }
    }

    private fun commit(reported: VolumeState) {
        if (reported == state) return
        state = reported
        // Corrects a visible HUD, and pops it up for changes made elsewhere
        // (soundbar remote, Bravia Connect app, TV settings).
        hud.show(reported)
    }

    private companion object {
        const val TAG = "VolumeController"
        const val NEVER = Long.MIN_VALUE / 2
        const val SETTLE_MS = 400L
    }
}
