package dev.heynol.barhud.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundPackageTest {

    private val foreground = ForegroundPackage()

    private fun app(pkg: String, atMs: Long) = foreground.onWindowEvent(pkg, isHome = false, nowMs = atMs)

    private fun home(atMs: Long, pkg: String = HOME) = foreground.onWindowEvent(pkg, isHome = true, nowMs = atMs)

    @Test
    fun `launcher event right after a launch keeps the app, which never reports again`() {
        // Logged on the TV 2026-10-27: YouTube at .498, launcher at .965, then nothing from YouTube.
        assertTrue(app(YOUTUBE, atMs = 27_498))
        assertFalse(home(atMs = 27_965))
        assertEquals(YOUTUBE, foreground.current)
    }

    @Test
    fun `launcher event between two events of the launching app changes nothing`() {
        // Logged on the TV 2026-09-14: Netflix at 02.985, launcher at 03.528, Netflix at 04.442.
        // The restore only starts over on a change, so the whole launch must count as one.
        assertTrue(app(NETFLIX, atMs = 2_985))
        assertFalse(home(atMs = 3_528))
        assertFalse(app(NETFLIX, atMs = 4_442))
        assertEquals(NETFLIX, foreground.current)
    }

    @Test
    fun `going home after the launch applies at once`() {
        app(YOUTUBE, atMs = 0)
        assertTrue(home(atMs = 1_500))
        assertEquals(HOME, foreground.current)
    }

    @Test
    fun `launcher event after the noise window still gets through`() {
        app(YOUTUBE, atMs = 0)
        home(atMs = 500)
        assertTrue(home(atMs = 20_000))
        assertEquals(HOME, foreground.current)
    }

    @Test
    fun `switching apps applies at once, even right after a launch`() {
        app(YOUTUBE, atMs = 0)
        assertTrue(app(NETFLIX, atMs = 100))
        assertEquals(NETFLIX, foreground.current)
    }

    @Test
    fun `launching right after going home applies at once`() {
        app(YOUTUBE, atMs = 0)
        home(atMs = 5_000)
        assertTrue(app(NETFLIX, atMs = 5_100))
        assertEquals(NETFLIX, foreground.current)
    }

    @Test
    fun `every switch to an app starts its own noise window`() {
        app(YOUTUBE, atMs = 0)
        app(NETFLIX, atMs = 10_000)
        assertFalse(home(atMs = 10_500))
        assertEquals(NETFLIX, foreground.current)
    }

    @Test
    fun `the launcher counts from the start, before any app was in front`() {
        assertTrue(home(atMs = 0))
        assertEquals(HOME, foreground.current)
    }

    private companion object {
        const val HOME = "com.google.android.apps.tv.launcherx"
        const val YOUTUBE = "com.google.android.youtube.tv"
        const val NETFLIX = "com.netflix.ninja"
    }
}
