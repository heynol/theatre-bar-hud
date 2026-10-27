package dev.heynol.barhud.hud

import dev.heynol.barhud.volume.VolumeState
import org.junit.Assert.assertEquals
import org.junit.Test

class HudDemoTest {

    @Test
    fun `counts up one step at a time, then mutes and unmutes`() {
        val states = HudDemo.frames(from = 18, to = 24).map { it.state }
        assertEquals((18..24).map { VolumeState(it) } + VolumeState(24, muted = true) + VolumeState(24), states)
    }

    @Test
    fun `counts down and clamps to the range`() {
        val volumes = HudDemo.frames(from = 3, to = -5, minVolume = 0, maxVolume = 100).map { it.state.volume }
        assertEquals(listOf(3, 2, 1, 0, 0, 0), volumes)
    }

    @Test
    fun `first frame shows at once, steps use the given pace`() {
        val frames = HudDemo.frames(from = 10, to = 12, stepMs = 200)
        assertEquals(listOf(0L, 200L, 200L), frames.take(3).map { it.delayMs })
    }
}
