package dev.heynol.barhud.service

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.ViewConfiguration
import dev.heynol.barhud.hud.HudOverlay
import dev.heynol.barhud.volume.InputVolumeMemory
import dev.heynol.barhud.volume.TvVolumeSource
import dev.heynol.barhud.volume.VolumeState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the HUD in sync with the soundbar, and restores a remembered volume per input.
 *
 * Latency strategy: a key press updates the HUD *immediately* with a predicted value (last known
 * volume ± 1). The soundbar's real value follows over HDMI-CEC a few hundred milliseconds later
 * and silently corrects the prediction. While keys are still arriving, reports are held back
 * ([SETTLE_MS]) because they describe an older press and would make the bar stutter backwards.
 *
 * Per-input volume: whatever volume the soundbar settles at while an input is on screen is
 * remembered for that input. Switching to an input with a remembered volume walks the soundbar
 * there one CEC step at a time (absolute volume can't be set in adjust-only mode), waiting for
 * each report before the next step. Any volume key press cancels the walk.
 *
 * Main thread only.
 */
class VolumeController(
    private val source: TvVolumeSource,
    private val memory: InputVolumeMemory,
    private val remoteSettings: RemoteSettings,
    private val hud: HudOverlay,
    private val scope: CoroutineScope,
) {
    private var state: VolumeState? = null
    private var soundbarWasActive = false
    private var lastKeyAt = NEVER
    private var settleJob: Job? = null

    private val downKeys = HashSet<Int>()
    private val consumedKeys = HashSet<Int>()
    private var holdJob: Job? = null

    private var input: String? = null
    private var restoreJob: Job? = null
    private var reportWaiter: Pair<(VolumeState) -> Boolean, CompletableDeferred<VolumeState>>? = null
    private var restoreEndedAt = NEVER

    // Last value the soundbar actually reported (unlike [state], never a prediction).
    private var lastReported: VolumeState? = null
    private var lastVolumeDownAt = NEVER

    fun start() {
        state = source.read()
        lastReported = state
        soundbarWasActive = source.isSoundbarActive()
        source.start(::onSystemReport)
    }

    fun stop() {
        source.stop()
        settleJob?.cancel()
        restoreJob?.cancel()
        holdJob?.cancel()
    }

    /**
     * Every ACTION_DOWN from the accessibility key filter, including repeats. Returns true if the
     * key is consumed.
     *
     * While the soundbar is active and [RemoteSettings.ownVolumeKeys] is on, volume up/down are
     * consumed and applied here with `adjustStreamVolume` (the same CEC path). Passed through,
     * they'd reach Xiaomi's MitvAudioService, which mutes on a quick double "down" and only acts
     * on a few repeats of a held key. Mute is always passed through.
     *
     * Only the first DOWN of a press is predicted; a held key then steps once per soundbar report
     * (see [holdVolumeKey]). A DOWN for a key that is already down is a repeat from the remote.
     */
    fun onVolumeKeyDown(keyCode: Int): Boolean {
        val repeat = !downKeys.add(keyCode)
        if (repeat) {
            if (keyCode != KeyEvent.KEYCODE_VOLUME_MUTE && source.isSoundbarActive()) hud.show(state)
            return keyCode in consumedKeys
        }
        // TV speakers have their own native HUD and key handling; only stand in for the soundbar.
        if (!source.isSoundbarActive()) return false
        cancelRestore("volume key") // the user takes over

        lastKeyAt = SystemClock.uptimeMillis()
        val current = state ?: source.read()
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            else -> 0
        }
        val predicted = if (direction == 0) current.copy(muted = !current.muted) else current.stepped(direction)
        state = predicted
        hud.show(predicted)

        if (direction == 0 || !remoteSettings.ownVolumeKeys) return false
        consumedKeys += keyCode
        source.adjust(direction)
        holdJob?.cancel()
        holdJob = scope.launch { holdVolumeKey(keyCode, direction, pressedAt = current.volume) }
        return true
    }

    /** Returns true if the matching DOWN was consumed; an UP must follow its DOWN. */
    fun onVolumeKeyUp(keyCode: Int): Boolean {
        downKeys -= keyCode
        if (downKeys.isEmpty()) {
            holdJob?.cancel()
            reportWaiter = null
        }
        return consumedKeys.remove(keyCode)
    }

    /** A held key keeps stepping, each step as soon as the soundbar reported the previous one. */
    private suspend fun holdVolumeKey(keyCode: Int, direction: Int, pressedAt: Int) {
        delay(ViewConfiguration.getKeyRepeatTimeout().toLong())
        // Don't run ahead of the soundbar: start repeating only once the first step is confirmed.
        var volume = source.read().volume
        if (volume == pressedAt) volume = awaitReport { it.volume != pressedAt }?.volume ?: return
        var steps = 0
        while (keyCode in downKeys && steps++ < MAX_HOLD_STEPS) {
            val before = volume
            val limit = if (direction > 0) state?.maxVolume ?: Int.MAX_VALUE else state?.minVolume ?: 0
            if (before == limit) return
            source.adjust(direction)
            volume = awaitReport { it.volume != before }?.volume ?: return
            delay(HOLD_STEP_MIN_INTERVAL_MS)
        }
    }

    fun onInputChanged(newInput: String?) {
        input = newInput
        cancelRestore("input changed")
        if (newInput == null || !memory.enabled) return
        val target = memory[newInput] ?: return
        restoreJob = scope.launch {
            delay(RESTORE_DELAY_MS) // let the app or HDMI source settle; a quick zap-through restores nothing
            restoreVolume(newInput, target)
        }
    }

    fun preview() {
        hud.show(state ?: source.read())
    }

    private suspend fun restoreVolume(forInput: String, target: Int) {
        try {
            for (pass in 1..MAX_RESTORE_PASSES) {
                if (!source.isSoundbarActive()) return
                var current = source.read()
                if (current.muted || current.volume == target) return
                Log.d(TAG, "restoring $forInput (pass $pass): ${current.volume} -> $target")
                var steps = 0
                while (current.volume != target && steps++ < MAX_RESTORE_STEPS) {
                    val before = current.volume
                    source.adjust(if (target > before) 1 else -1)
                    // One step at a time: wait until the soundbar reports a *changed* volume. It sends
                    // every report twice, and sending the next step early queues presses that the
                    // soundbar keeps applying after the target is reached.
                    current = awaitReport { it.volume != before } ?: run {
                        Log.d(TAG, "soundbar stopped answering at $before")
                        return
                    }
                }
                // Catch any late-applied step before declaring victory.
                delay(VERIFY_DELAY_MS)
            }
        } finally {
            reportWaiter = null
            restoreEndedAt = SystemClock.uptimeMillis()
        }
    }

    private suspend fun awaitReport(predicate: (VolumeState) -> Boolean): VolumeState? {
        val deferred = CompletableDeferred<VolumeState>()
        reportWaiter = predicate to deferred
        return withTimeoutOrNull(STEP_TIMEOUT_MS) { deferred.await() }
    }

    private fun cancelRestore(reason: String) {
        if (restoreJob?.isActive == true) Log.d(TAG, "restore cancelled: $reason")
        restoreJob?.cancel()
        restoreJob = null
        reportWaiter = null
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
        if (undoDoubleDownMute(reported)) return
        reportWaiter?.let { (predicate, deferred) ->
            if (predicate(reported)) {
                reportWaiter = null
                deferred.complete(reported)
            }
        }
        if (SystemClock.uptimeMillis() - lastKeyAt >= SETTLE_MS) {
            commit(reported)
            return
        }
        if (settleJob?.isActive == true) return
        settleJob = scope.launch {
            while (SystemClock.uptimeMillis() - lastKeyAt < SETTLE_MS) {
                delay(SETTLE_MS - (SystemClock.uptimeMillis() - lastKeyAt))
            }
            // Re-read instead of using the held report: it may predate the latest press.
            commit(source.read())
        }
    }

    /**
     * Xiaomi's MitvAudioService mutes ~360 ms after two "down" presses within 750 ms, and there
     * is no setting for it. A report that turns mute on shortly after the volume went down is
     * treated as that and undone right away, without flashing the mute state on the HUD. The mute
     * key on its own (no volume change just before) is left alone. Returns true if handled.
     */
    private fun undoDoubleDownMute(reported: VolumeState): Boolean {
        val previous = lastReported
        lastReported = reported
        val now = SystemClock.uptimeMillis()
        if (previous == null) return false
        val restoring = restoreJob?.isActive == true
        if (!restoring && !reported.muted && !previous.muted && reported.volume < previous.volume) {
            lastVolumeDownAt = now
        }
        val doubleDownMute = reported.muted && !previous.muted && now - lastVolumeDownAt < DOUBLE_DOWN_MUTE_WINDOW_MS
        if (!doubleDownMute || !remoteSettings.undoDoubleDownMute) return false
        Log.d(TAG, "undoing Xiaomi's double-down mute")
        lastVolumeDownAt = NEVER
        source.unmute()
        return true
    }

    private fun commit(reported: VolumeState) {
        // Remember what the user settled on for this input. Our own restore steps don't count,
        // including stragglers reported shortly after the restore finished.
        val restoring = restoreJob?.isActive == true ||
            SystemClock.uptimeMillis() - restoreEndedAt < RESTORE_GRACE_MS
        if (!restoring && memory.enabled) input?.let { memory[it] = reported.volume }
        if (reported == state) return
        state = reported
        // Corrects a visible HUD, and pops it up for changes made elsewhere
        // (per-input restore, soundbar remote, Bravia Connect app).
        hud.show(reported)
    }

    private companion object {
        const val TAG = "VolumeController"
        const val NEVER = Long.MIN_VALUE / 2
        const val SETTLE_MS = 400L
        const val RESTORE_DELAY_MS = 1_500L
        const val STEP_TIMEOUT_MS = 1_500L
        const val MAX_RESTORE_STEPS = 100
        const val MAX_RESTORE_PASSES = 3
        const val VERIFY_DELAY_MS = 800L
        const val RESTORE_GRACE_MS = 1_500L
        const val MAX_HOLD_STEPS = 100
        const val HOLD_STEP_MIN_INTERVAL_MS = 80L

        /** Xiaomi's 750 ms double-press window + 360 ms mute timer + CEC round trips. */
        const val DOUBLE_DOWN_MUTE_WINDOW_MS = 1_500L
    }
}
