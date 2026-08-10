package ru.wavelink.app.player

import ru.wavelink.app.core.PlayStatsRules
import ru.wavelink.app.core.model.Track
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * The offline twin of `TrackService.WeightedOrder` on the server — same formula, so shuffling
 * feels identical whether or not there is a network.
 *
 * Ordered weighted sampling without replacement (Efraimidis–Spirakis) in log form:
 * `key = ln(U) / w`, sorted descending. `ln(U)` is negative, so a larger weight pulls the key
 * towards zero, i.e. up. Discover weights are `w = 1 / (myPlays + 1)^α`, which makes a
 * never-played track the strongest candidate and fades much-played ones out smoothly.
 */
object LocalShuffle {

    fun order(
        tracks: List<Track>,
        mode: String,
        limit: Int = 50,
        random: Random = Random.Default
    ): List<Track> {
        if (tracks.isEmpty()) return emptyList()
        val discover = mode.equals("discover", ignoreCase = true)

        return tracks
            .map { track ->
                val weight = if (discover) {
                    1.0 / (track.myPlays.coerceAtLeast(0) + 1).toDouble()
                        .pow(PlayStatsRules.DISCOVER_EXPONENT)
                } else {
                    1.0
                }
                var u = random.nextDouble()
                if (u <= 0.0) u = Double.MIN_VALUE   // ln(0) would be -inf
                track to ln(u) / weight
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
