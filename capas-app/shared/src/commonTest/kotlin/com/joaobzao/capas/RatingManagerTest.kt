package com.joaobzao.capas

import com.joaobzao.capas.rating.RatingManagerImpl
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RatingManagerTest {

    private fun manager(settings: Settings) = RatingManagerImpl(settings)

    @Test
    fun doesNotTriggerBeforeThreshold() {
        val manager = manager(MapSettings())
        repeat(4) {
            assertFalse(manager.registerAppOpenAndCheck(), "launch ${it + 1} must not trigger")
        }
    }

    @Test
    fun triggersExactlyOnceOnFifthLaunch() {
        val manager = manager(MapSettings())
        repeat(4) { manager.registerAppOpenAndCheck() }

        assertTrue(manager.registerAppOpenAndCheck(), "5th launch must trigger")
    }

    @Test
    fun neverTriggersAgainAfterFiring() {
        val manager = manager(MapSettings())
        repeat(5) { manager.registerAppOpenAndCheck() }

        repeat(20) {
            assertFalse(manager.registerAppOpenAndCheck(), "post-trigger launch must not trigger")
        }
    }

    @Test
    fun countAndFlagPersistAcrossInstances() {
        val settings = MapSettings()
        // A fresh manager instance each launch, sharing persisted settings.
        repeat(4) { assertFalse(manager(settings).registerAppOpenAndCheck()) }
        assertTrue(manager(settings).registerAppOpenAndCheck(), "5th launch across instances must trigger")
        assertFalse(manager(settings).registerAppOpenAndCheck(), "6th launch must not trigger")

        assertEquals(6, settings.getInt("rating_launch_count", 0))
        assertTrue(settings.getBoolean("rating_review_requested", false))
    }
}
