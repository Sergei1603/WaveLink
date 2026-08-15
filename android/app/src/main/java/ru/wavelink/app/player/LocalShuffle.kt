package ru.wavelink.app.player

import ru.wavelink.app.core.PlayStatsRules
import ru.wavelink.app.core.model.Track
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * The offline twin of `TrackService.WeightedOrder` on the server — same formula, so shuffling
 * feels identical whether or not there is a network. The two do not produce the same sequence
 * for a given seed (different RNGs), and they do not need to: a queue is fed by one of them.
 *
 * Ordered weighted sampling without replacement (Efraimidis–Spirakis) in log form:
 * `key = ln(U) / w`, sorted descending. `ln(U)` is negative, so a larger weight pulls the key
 * towards zero, i.e. up. Discover weights are `w = 1 / (myPlays + 1)^α`, which makes a
 * never-played track the strongest candidate and fades much-played ones out smoothly.
 *
 * [order] is stable for a given seed and track set, which is what lets [page] walk one cycle
 * chunk by chunk the way the server's cursor does.
 */
object LocalShuffle {

    fun order(tracks: List<Track>, mode: String, seed: Int): List<Track> {
        if (tracks.isEmpty()) return emptyList()
        val discover = mode.equals("discover", ignoreCase = true)
        // Sorted first, so the draw a given track gets depends on the seed alone.
        val random = Random(seed)

        return tracks
            .sortedBy { it.id }
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
            .map { it.first }
    }

    /** One slice of [order] — the offline stand-in for the server's `seed` + `cursor`. */
    fun page(
        tracks: List<Track>,
        mode: String,
        seed: Int,
        cursor: Int = 0,
        limit: Int = 50
    ): List<Track> = order(tracks, mode, seed).drop(cursor.coerceAtLeast(0)).take(limit)
}
