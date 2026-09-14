package dev.heynol.barhud.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.exp

/**
 * Vertical rounded track filling from the bottom. Stand-in for TvSystemUI's
 * `com.mitv.systemui.volume.ProgressView`, which can't be loaded from another app.
 */
class VerticalTrackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply { color = HudStyle.TRACK }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply { color = HudStyle.FILL }
    private val rect = RectF()

    private var targetLevel = 0f
    private var level = 0f
    private var lastFrameNanos = 0L

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** @param animate ease towards the new level; false snaps (used when the HUD appears). */
    fun setLevel(fraction: Float, muted: Boolean, animate: Boolean) {
        targetLevel = fraction.coerceIn(0f, 1f)
        fillPaint.color = if (muted) HudStyle.FILL_MUTED else HudStyle.FILL
        if (!animate) {
            level = targetLevel
            lastFrameNanos = 0L
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        advanceLevel()
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = w / 2f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)
        if (level > 0f) {
            rect.top = h - h * level
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        }
    }

    /** Frame-rate independent exponential ease; retargeting mid-flight never jumps. */
    private fun advanceLevel() {
        if (level == targetLevel) {
            lastFrameNanos = 0L
            return
        }
        val now = System.nanoTime()
        val dtMs = if (lastFrameNanos == 0L) FRAME_MS else ((now - lastFrameNanos) / 1_000_000f).coerceIn(0f, 4 * FRAME_MS)
        lastFrameNanos = now
        level += (targetLevel - level) * (1f - exp(-dtMs / HudStyle.LEVEL_TAU_MS))
        if (abs(targetLevel - level) < 0.0005f) {
            level = targetLevel
            lastFrameNanos = 0L
        } else {
            postInvalidateOnAnimation()
        }
    }

    private companion object {
        const val FRAME_MS = 16.6f
    }
}
