package dev.heynol.barhud.hud

import android.view.Gravity

/**
 * Visual tokens of the volume HUD.
 *
 * On Xiaomi TVs the HUD is built from TvSystemUI's own `mi_volume_vertical_dialog_layout`,
 * background and icons at runtime (see [HudContent]), so geometry and the themed surface colour
 * match the native HUD exactly. The geometry values here mirror that layout and are used only for
 * the fallback on other devices. Track colours are hard-coded in Xiaomi's `ProgressView`, so
 * both paths use the values below.
 */
object HudStyle {
    // Window placement, as VolumeVerticalUIManager.initDialog: right edge, vertically centred.
    const val GRAVITY = Gravity.END or Gravity.CENTER_VERTICAL

    // Fallback geometry (mirrors mi_volume_vertical_dialog_layout).
    const val WIDTH_DP = 40f
    const val MARGIN_END_DP = 20f
    const val PADDING_VERTICAL_DP = 10f
    const val CORNER_RADIUS_DP = 30f
    const val ICON_MARGIN_TOP_DP = 5f
    const val TRACK_WIDTH_DP = 5f
    const val TRACK_HEIGHT_DP = 200f
    const val TRACK_MARGIN_TOP_DP = 5f
    const val VALUE_TEXT_SP = 14f

    // Fallback surface; on Xiaomi it's the SystemUI theme's ?android:colorPrimary.
    const val SURFACE_FALLBACK = 0xFFF1F3F4.toInt()
    const val VALUE_TEXT = 0xFF000000.toInt()

    // com.mitv.systemui.volume.ProgressView / VolumeVerticalUIManager.onVolumeChange
    const val TRACK = 0xFFE7E7E7.toInt()
    const val FILL = 0xFF0E98E8.toInt()
    const val FILL_MUTED = 0x33000000

    // Motion. The native dialog has no window animation (windowAnimations = -1) and pops in and
    // out instantly; set both fades to 0 for an exact match.
    const val FADE_IN_MS = 120L
    const val FADE_OUT_MS = 200L
    const val AUTO_HIDE_MS = 3000L
    /** Time constant of the ease the fill uses to chase its target (native jumps; ≈95% in 3τ). */
    const val LEVEL_TAU_MS = 35f
}
