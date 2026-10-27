package dev.heynol.barhud.hud

import dev.heynol.barhud.volume.VolumeState

/**
 * Scripted HUD sequence for screen recordings: count from one volume to another, mute, unmute.
 * Only the HUD plays it; the soundbar's volume is never touched.
 */
object HudDemo {

    /** One HUD update, shown [delayMs] after the previous one. */
    data class Frame(val delayMs: Long, val state: VolumeState)

    /** Default pace: about what the soundbar's `<Report Audio Status>` answers allow. */
    const val STEP_MS = 350L
    private const val HOLD_MS = 900L
    private const val MUTED_MS = 1_200L

    fun frames(from: Int, to: Int, stepMs: Long = STEP_MS, minVolume: Int = 0, maxVolume: Int = 100): List<Frame> {
        val start = from.coerceIn(minVolume, maxVolume)
        val end = to.coerceIn(minVolume, maxVolume)
        fun state(volume: Int, muted: Boolean = false) = VolumeState(volume, minVolume, maxVolume, muted)
        return buildList {
            add(Frame(0, state(start)))
            val direction = if (end >= start) 1 else -1
            var volume = start
            while (volume != end) {
                volume += direction
                add(Frame(stepMs, state(volume)))
            }
            add(Frame(HOLD_MS, state(end, muted = true)))
            add(Frame(MUTED_MS, state(end)))
        }
    }
}
