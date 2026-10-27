package dev.heynol.barhud.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.heynol.barhud.hud.HudDemo

/**
 * Plays [HudDemo] on the real HUD, for reproducible screen recordings. Only callers holding DUMP,
 * i.e. the adb shell, can reach it:
 *
 * `adb shell am broadcast -a dev.heynol.barhud.DEMO -n dev.heynol.barhud/.service.DemoReceiver`
 *
 * Extras: `from` / `to` (int, default 18 / 24), `step_ms` (long, default [HudDemo.STEP_MS]).
 * The accessibility service has to be running.
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val from = intent.getIntExtra("from", 18)
        val to = intent.getIntExtra("to", 24)
        val stepMs = intent.getLongExtra("step_ms", HudDemo.STEP_MS).coerceIn(50L, 5_000L)
        val started = VolumeKeyService.demo(from, to, stepMs)
        Log.i(TAG, if (started) "demo $from -> $to, mute, unmute" else "service not running, nothing shown")
    }

    private companion object {
        const val TAG = "DemoReceiver"
    }
}
