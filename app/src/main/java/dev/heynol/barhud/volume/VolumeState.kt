package dev.heynol.barhud.volume

/** Soundbar volume as Android mirrors it from HDMI-CEC `<Report Audio Status>` (0..100). */
data class VolumeState(
    val volume: Int,
    val minVolume: Int = 0,
    val maxVolume: Int = 100,
    val muted: Boolean = false,
) {
    /** Fill level of the bar, 0..1. */
    val fraction: Float
        get() = if (maxVolume > minVolume) {
            ((volume - minVolume).toFloat() / (maxVolume - minVolume)).coerceIn(0f, 1f)
        } else {
            0f
        }

    /** Volume after [steps] key presses, clamped to the range. Changing volume unmutes. */
    fun stepped(steps: Int): VolumeState =
        copy(volume = (volume + steps).coerceIn(minVolume, maxVolume), muted = false)
}
