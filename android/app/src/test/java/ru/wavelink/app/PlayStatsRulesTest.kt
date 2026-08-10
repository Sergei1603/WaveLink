package ru.wavelink.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.wavelink.app.core.PlayStatsRules

/**
 * The rule the user chose: significant at ≥60% of the duration OR ≥120 seconds.
 * These constants must match `PlayStats` in the server's appsettings.json.
 */
class PlayStatsRulesTest {

    @Test
    fun `sixty percent of a track is significant`() {
        assertTrue(PlayStatsRules.isSignificant(listenedSeconds = 120, durationSeconds = 200))
    }

    @Test
    fun `just under sixty percent is not`() {
        assertFalse(PlayStatsRules.isSignificant(listenedSeconds = 119, durationSeconds = 200))
    }

    @Test
    fun `two minutes of a long track is significant even below sixty percent`() {
        // 120 / 600 = 20%, but the absolute arm fires.
        assertTrue(PlayStatsRules.isSignificant(listenedSeconds = 120, durationSeconds = 600))
    }

    @Test
    fun `a long track abandoned early is not significant`() {
        assertFalse(PlayStatsRules.isSignificant(listenedSeconds = 119, durationSeconds = 600))
    }

    @Test
    fun `unknown duration degrades to the seconds-only arm`() {
        // Bot document uploads leave duration = 0; only the ≥120s arm can fire.
        assertFalse(PlayStatsRules.isSignificant(listenedSeconds = 60, durationSeconds = 0))
        assertTrue(PlayStatsRules.isSignificant(listenedSeconds = 120, durationSeconds = 0))
    }

    @Test
    fun `a short track played through is significant`() {
        assertTrue(PlayStatsRules.isSignificant(listenedSeconds = 25, durationSeconds = 30))
    }
}
