package dev.heynol.barhud.service

import dev.heynol.barhud.service.RestoreWatch.Verdict
import dev.heynol.barhud.volume.VolumeState
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreWatchTest {

    @Test
    fun `a walk tells its step from repeats and from the user`() {
        val watch = RestoreWatch(ours = setOf(7), expected = 6)
        assertEquals(Verdict.OURS, watch.judge(VolumeState(7)))
        assertEquals(Verdict.STEP_DONE, watch.judge(VolumeState(6)))
        assertEquals(Verdict.USER, watch.judge(VolumeState(8)))
    }

    @Test
    fun `muting is always the user`() {
        assertEquals(Verdict.USER, RestoreWatch(ours = setOf(7)).judge(VolumeState(7, muted = true)))
    }

    @Test
    fun `the step of a cancelled walk doesn't look like the user to the next restore`() {
        // Logged on the TV 2026-11-03: YouTube's walk sent 7 -> 6 and Netflix came to the front
        // before the report arrived. Netflix's restore then took the 6 for the user and learned it.
        val cancelled = RestoreWatch(ours = setOf(7), expected = 6)
        val next = RestoreWatch.waiting(volume = 7, previous = cancelled)
        assertEquals(Verdict.OURS, next.judge(VolumeState(6)))
        assertEquals(Verdict.OURS, next.judge(VolumeState(7)))
    }

    @Test
    fun `a repeat of the cancelled walk's last volume is explained after its step was reported`() {
        val cancelled = RestoreWatch(ours = setOf(7), expected = 6)
        val next = RestoreWatch.waiting(volume = 6, previous = cancelled)
        assertEquals(Verdict.OURS, next.judge(VolumeState(7)))
    }

    @Test
    fun `a late step seen while the cancelled restore was verifying is explained`() {
        val cancelled = RestoreWatch(ours = setOf(6, 5))
        val next = RestoreWatch.waiting(volume = 6, previous = cancelled)
        assertEquals(Verdict.OURS, next.judge(VolumeState(5)))
    }

    @Test
    fun `a change no walk can explain is still the user`() {
        val cancelled = RestoreWatch(ours = setOf(7), expected = 6)
        val next = RestoreWatch.waiting(volume = 7, previous = cancelled)
        assertEquals(Verdict.USER, next.judge(VolumeState(8)))
        assertEquals(Verdict.USER, next.judge(VolumeState(5)))
        assertEquals(Verdict.USER, next.judge(VolumeState(7, muted = true)))
    }

    @Test
    fun `without a cancelled walk every change during the wait is the user`() {
        assertEquals(Verdict.USER, RestoreWatch.waiting(volume = 7, previous = null).judge(VolumeState(8)))

        val waitingOnly = RestoreWatch.waiting(volume = 7, previous = null)
        assertEquals(Verdict.USER, RestoreWatch.waiting(volume = 7, previous = waitingOnly).judge(VolumeState(6)))
    }
}
