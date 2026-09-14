package dev.heynol.barhud.hud

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.heynol.barhud.R
import dev.heynol.barhud.volume.VolumeState
import kotlin.math.roundToInt

/**
 * The HUD's view tree: soundbar icon, vertical track, number.
 *
 * Preferably inflated from the TV's own TvSystemUI resources (layout, themed pill background,
 * text style), so it matches the native Xiaomi HUD down to the pixel and follows the system
 * theme. Xiaomi's ProgressView class is swapped for [VerticalTrackView], and the speaker icon for
 * a soundbar glyph so this HUD is distinguishable from the TV's own. If those resources don't
 * exist (other devices, a firmware that renamed them) an equivalent tree is built from [HudStyle].
 */
internal class HudContent private constructor(
    /** Has FrameLayout.LayoutParams including the right margin. */
    val root: View,
    private val icon: ImageView,
    private val track: VerticalTrackView,
    private val value: TextView,
    /** Our app's context: the soundbar glyphs aren't in SystemUI's resources. */
    appContext: Context,
) {
    private val iconNormal: Drawable? = appContext.getDrawable(R.drawable.ic_hud_soundbar)
    private val iconMuted: Drawable? = appContext.getDrawable(R.drawable.ic_hud_soundbar_muted)

    fun bind(state: VolumeState?, animate: Boolean) {
        val muted = state?.muted == true
        value.text = state?.volume?.toString() ?: PLACEHOLDER
        icon.setImageDrawable(if (muted) iconMuted else iconNormal)
        track.setLevel(state?.fraction ?: 0f, muted, animate)
    }

    companion object {
        private const val TAG = "HudContent"
        private const val PLACEHOLDER = "–"
        private const val SYSTEM_UI = "com.android.systemui"
        private const val PROGRESS_VIEW = "com.mitv.systemui.volume.ProgressView"

        fun create(context: Context): HudContent =
            try {
                fromSystemUi(context)
            } catch (e: Exception) {
                Log.w(TAG, "Native Xiaomi HUD resources unavailable, using fallback", e)
                null
            } ?: fallback(context)

        private fun fromSystemUi(context: Context): HudContent? {
            val systemUi = context.createPackageContext(SYSTEM_UI, 0)
            val res = systemUi.resources
            fun id(name: String, type: String) = res.getIdentifier(name, type, SYSTEM_UI)

            val layoutId = id("mi_volume_vertical_dialog_layout", "layout")
            val imgId = id("volum_vertical_img", "id")
            val seekbarId = id("volum_vertical_seekbar", "id")
            val numId = id("volum_vertical_num", "id")
            if (listOf(layoutId, imgId, seekbarId, numId).any { it == 0 }) return null

            val inflater = LayoutInflater.from(systemUi).cloneInContext(systemUi)
            inflater.factory2 = ProgressViewSubstitute
            val root = inflater.inflate(layoutId, FrameLayout(systemUi), false)

            return HudContent(
                root = root,
                icon = root.findViewById<ImageView>(imgId) ?: return null,
                track = root.findViewById<VerticalTrackView>(seekbarId) ?: return null,
                value = root.findViewById<TextView>(numId) ?: return null,
                appContext = context,
            ).also { Log.i(TAG, "Using native Xiaomi HUD resources") }
        }

        private fun fallback(context: Context): HudContent {
            val metrics = context.resources.displayMetrics
            fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, metrics).roundToInt()
            val padding = dp(HudStyle.PADDING_VERTICAL_DP)

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(HudStyle.SURFACE_FALLBACK)
                    cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, HudStyle.CORNER_RADIUS_DP, metrics)
                }
                setPadding(0, padding, 0, padding)
                layoutParams = FrameLayout.LayoutParams(dp(HudStyle.WIDTH_DP), FrameLayout.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = dp(HudStyle.MARGIN_END_DP) }
            }
            val icon = ImageView(context)
            root.addView(
                icon,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(HudStyle.ICON_MARGIN_TOP_DP) },
            )
            val track = VerticalTrackView(context)
            root.addView(
                track,
                LinearLayout.LayoutParams(dp(HudStyle.TRACK_WIDTH_DP), dp(HudStyle.TRACK_HEIGHT_DP))
                    .apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(HudStyle.TRACK_MARGIN_TOP_DP) },
            )
            val value = TextView(context).apply {
                setTextColor(HudStyle.VALUE_TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, HudStyle.VALUE_TEXT_SP)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            root.addView(value, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

            return HudContent(
                root = root,
                icon = icon,
                track = track,
                value = value,
                appContext = context,
            )
        }
    }

    /** Swaps Xiaomi's ProgressView (not loadable from another app) for our equivalent. */
    private object ProgressViewSubstitute : LayoutInflater.Factory2 {
        override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? =
            if (name == PROGRESS_VIEW) VerticalTrackView(context, attrs) else null

        override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
            onCreateView(null, name, context, attrs)
    }
}
