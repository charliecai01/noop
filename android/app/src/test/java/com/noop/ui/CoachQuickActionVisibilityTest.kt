package com.noop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import android.content.Context
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The Coach master switch decides whether Today's quick-actions sheet offers Coach, its door since its
 * bar slot went to Settings. With the AI switched off the row must go, and only that row.
 */
class CoachQuickActionVisibilityTest {

    @Test
    fun `coach enabled offers coach as a quick action`() {
        assertTrue(Destination.Coach.route in quickActionRoutesFor(coachEnabled = true))
    }

    @Test
    fun `coach disabled drops the coach row and nothing else`() {
        val visible = quickActionRoutesFor(coachEnabled = false)
        assertFalse("coach row must not be offered", Destination.Coach.route in visible)
        assertEquals(
            "only Coach may be removed",
            quickActionRoutesFor(coachEnabled = true).filterNot { it == Destination.Coach.route },
            visible,
        )
    }
}

/**
 * The stored default, which is the invariant that actually matters on upgrade.
 *
 * Separate from the filter tests because it needs a Context. The first version of this asserted
 * `BottomBarStyleStore.coachEnabled`, which is an in-memory singleton seeded to true -- that passes
 * whatever the PREF does, so it would not have noticed the one mistake worth catching here: a pref
 * default of false, which silently switches Coach off for every existing install on upgrade.
 */
@RunWith(RobolectricTestRunner::class)
class CoachEnabledPrefDefaultTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `an install that never touched the toggle has coach enabled`() {
        NoopPrefs.of(context).edit().remove(NoopPrefs.KEY_COACH_ENABLED).commit()
        assertTrue("unset must read as ON, or upgrades lose Coach", NoopPrefs.coachEnabled(context))
    }

    @Test
    fun `the stored value round-trips in both directions`() {
        NoopPrefs.setCoachEnabled(context, false)
        assertFalse(NoopPrefs.coachEnabled(context))
        NoopPrefs.setCoachEnabled(context, true)
        assertTrue(NoopPrefs.coachEnabled(context))
    }
}
