package dev.heynol.barhud.service

import dev.heynol.barhud.volume.VolumeState
import kotlinx.coroutines.CompletableDeferred

/**
 * What the running restore expects from the soundbar. Reports in [ours] are explained by the
 * walk (repeats, stragglers), [expected] completes the current step, anything else is the user.
 */
class RestoreWatch(
    val ours: Set<Int>,
    val expected: Int? = null,
    val reached: CompletableDeferred<VolumeState>? = null,
) {
    enum class Verdict { OURS, STEP_DONE, USER }

    fun judge(reported: VolumeState): Verdict = when {
        reported.muted -> Verdict.USER
        reported.volume in ours -> Verdict.OURS
        reported.volume == expected -> Verdict.STEP_DONE
        else -> Verdict.USER
    }

    companion object {
        /**
         * The watch for a restore's wait before its first step, at [volume]. A walk cancelled by
         * the switch can't take back the step it already sent, whose report arrives during this
         * wait, so everything [previous] explained stays explained until the wait is over.
         */
        fun waiting(volume: Int, previous: RestoreWatch?) = RestoreWatch(
            ours = setOf(volume) + previous?.ours.orEmpty() + listOfNotNull(previous?.expected),
        )
    }
}
