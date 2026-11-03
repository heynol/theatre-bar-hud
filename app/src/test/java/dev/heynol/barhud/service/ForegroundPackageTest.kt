package dev.heynol.barhud.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundPackageTest {

    private val foreground = ForegroundPackage()

    private fun app(pkg: String, atMs: Long) =
        foreground.onWindowEvent(pkg, isHome = false, className = "$pkg.MainActivity", nowMs = atMs)

    /** What the launcher sends during a launch, logged on the TV. */
    private fun launcherView(atMs: Long) =
        foreground.onWindowEvent(HOME, isHome = true, className = "android.widget.FrameLayout", nowMs = atMs)

    /** What the launcher sends when it really comes back, logged on the TV. */
    private fun launcherActivity(atMs: Long) =
        foreground.onWindowEvent(HOME, isHome = true, className = "$HOME.home.HomeActivity", nowMs = atMs)

    @Test
    fun `launcher view event right after a launch keeps the app, which never reports again`() {
        // Logged on the TV 2026-11-03: YouTube at 18.667, launcher at 19.136, then nothing from YouTube.
        assertTrue(app(YOUTUBE, atMs = 18_667))
        assertFalse(launcherView(atMs = 19_136))
        assertEquals(YOUTUBE, foreground.current)
    }

    @Test
    fun `launcher event between two events of the launching app changes nothing`() {
        // Logged on the TV 2026-09-14: Netflix at 02.985, launcher at 03.528, Netflix at 04.442.
        // The restore only starts over on a change, so the whole launch must count as one.
        assertTrue(app(NETFLIX, atMs = 2_985))
        assertFalse(launcherView(atMs = 3_528))
        assertFalse(app(NETFLIX, atMs = 4_442))
        assertEquals(NETFLIX, foreground.current)
    }

    @Test
    fun `going home right after a launch applies at once`() {
        // Logged on the TV 2026-11-03: YouTube at 05.342, HomeActivity at 06.091.
        app(YOUTUBE, atMs = 5_342)
        assertTrue(launcherActivity(atMs = 6_091))
        assertEquals(HOME, foreground.current)
    }

    @Test
    fun `an event without a class counts as noise right after a launch`() {
        app(YOUTUBE, atMs = 0)
        assertFalse(foreground.onWindowEvent(HOME, isHome = true, className = null, nowMs = 500))
        assertEquals(YOUTUBE, foreground.current)
    }

    @Test
    fun `any launcher event after the noise window applies at once`() {
        app(YOUTUBE, atMs = 0)
        assertTrue(launcherView(atMs = 1_500))
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
        launcherActivity(atMs = 5_000)
        assertTrue(app(NETFLIX, atMs = 5_100))
        assertEquals(NETFLIX, foreground.current)
    }

    @Test
    fun `every switch to an app starts its own noise window`() {
        app(YOUTUBE, atMs = 0)
        app(NETFLIX, atMs = 10_000)
        assertFalse(launcherView(atMs = 10_500))
        assertEquals(NETFLIX, foreground.current)
    }

    @Test
    fun `the launcher counts from the start, before any app was in front`() {
        assertTrue(launcherView(atMs = 0))
        assertEquals(HOME, foreground.current)
    }

    private companion object {
        const val HOME = "com.google.android.apps.tv.launcherx"
        const val YOUTUBE = "com.google.android.youtube.tv"
        const val NETFLIX = "com.netflix.ninja"
    }
}
