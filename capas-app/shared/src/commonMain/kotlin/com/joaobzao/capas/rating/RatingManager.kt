package com.joaobzao.capas.rating

import com.russhwolf.settings.Settings

/**
 * Owns the decision of *when* to ask the user for an app review, plus the
 * persistence of that decision. Deliberately UI- and platform-free: presenting
 * the native review dialog needs an Activity / UIViewController and lives in the
 * platform layers, which call [registerAppOpenAndCheck] on app start.
 */
interface RatingManager {
    /**
     * Records one app launch and reports whether this launch should trigger the
     * automatic in-app review flow. Returns `true` exactly once, ever: on the
     * launch that reaches the threshold, provided a review hasn't been requested
     * before. Every other call returns `false`.
     */
    fun registerAppOpenAndCheck(): Boolean
}

class RatingManagerImpl(
    private val settings: Settings
) : RatingManager {

    override fun registerAppOpenAndCheck(): Boolean {
        val count = settings.getInt(LAUNCH_COUNT_KEY, 0) + 1
        settings.putInt(LAUNCH_COUNT_KEY, count)

        val alreadyRequested = settings.getBoolean(REVIEW_REQUESTED_KEY, false)
        if (!alreadyRequested && count >= LAUNCH_THRESHOLD) {
            settings.putBoolean(REVIEW_REQUESTED_KEY, true)
            return true
        }
        return false
    }

    private companion object {
        const val LAUNCH_COUNT_KEY = "rating_launch_count"
        const val REVIEW_REQUESTED_KEY = "rating_review_requested"
        const val LAUNCH_THRESHOLD = 5
    }
}
