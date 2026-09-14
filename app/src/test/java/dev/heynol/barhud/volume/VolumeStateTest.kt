package dev.heynol.barhud.volume

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeStateTest {

    @Test
    fun `stepping clamps to range and unmutes`() {
        assertEquals(VolumeState(volume = 100, muted = false), VolumeState(volume = 99, muted = true).stepped(+3))
        assertEquals(0, VolumeState(volume = 0).stepped(-1).volume)
    }

    @Test
    fun `fraction follows the reported range`() {
        assertEquals(0.08f, VolumeState(volume = 8).fraction, 0.0001f)
        assertEquals(0f, VolumeState(volume = 5, minVolume = 5, maxVolume = 5).fraction, 0f)
    }
}
