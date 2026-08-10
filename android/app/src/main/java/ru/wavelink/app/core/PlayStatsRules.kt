package ru.wavelink.app.core

/**
 * The significance rule, duplicated on the device so that discover-shuffle keeps working offline.
 *
 * These constants MUST stay in sync with `PlayStats` in `WaveLink.API/appsettings.json`
 * (`SignificantCompletion` / `SignificantSeconds`). The server is still the authority — it
 * recomputes significance on every report — this copy only drives local counters until a sync.
 */
object PlayStatsRules {
    const val SIGNIFICANT_COMPLETION = 0.60
    const val SIGNIFICANT_SECONDS = 120
    const val MIN_REPORTED_SECONDS = 5
    /** α in the discover weight w = 1 / (myPlays + 1)^α. */
    const val DISCOVER_EXPONENT = 0.7

    fun isSignificant(listenedSeconds: Int, durationSeconds: Int): Boolean {
        val byCompletion = durationSeconds > 0 &&
            listenedSeconds.toDouble() / durationSeconds >= SIGNIFICANT_COMPLETION
        return byCompletion || listenedSeconds >= SIGNIFICANT_SECONDS
    }
}
