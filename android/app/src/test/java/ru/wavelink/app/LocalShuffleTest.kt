package ru.wavelink.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.wavelink.app.core.model.Track
import ru.wavelink.app.player.LocalShuffle
import kotlin.random.Random

class LocalShuffleTest {

    private fun track(id: String, plays: Int) = Track(
        id = id,
        title = id,
        artist = "Artist",
        duration = 200,
        fileSize = 1,
        mimeType = "audio/mpeg",
        uploadedAt = null,
        isPublic = false,
        isOwned = true,
        uploaderUsername = "me",
        myPlays = plays,
        myLastPlayedAt = null,
        myCompleted = false,
        downloadState = null
    )

    @Test
    fun `discover favours the least played track`() {
        val tracks = listOf(track("never", 0), track("some", 1), track("worn", 100))
        val random = Random(20260810)

        val firsts = (1..2000).map { LocalShuffle.order(tracks, "discover", limit = 1, random = random).first().id }
        val counts = firsts.groupingBy { it }.eachCount()

        // w = 1/(plays+1)^0.7 → never ≈ 1.0, some ≈ 0.616, worn ≈ 0.0396.
        // Expected shares ≈ 0.604 / 0.372 / 0.024.
        val never = counts.getOrDefault("never", 0) / 2000.0
        val some = counts.getOrDefault("some", 0) / 2000.0
        val worn = counts.getOrDefault("worn", 0) / 2000.0

        assertTrue("never=$never expected ≈0.60", never in 0.55..0.66)
        assertTrue("some=$some expected ≈0.37", some in 0.32..0.43)
        assertTrue("worn=$worn expected ≈0.02", worn < 0.06)
    }

    @Test
    fun `random mode is roughly uniform`() {
        val tracks = listOf(track("a", 0), track("b", 50), track("c", 100))
        val random = Random(7)

        val firsts = (1..3000).map { LocalShuffle.order(tracks, "random", limit = 1, random = random).first().id }
        val counts = firsts.groupingBy { it }.eachCount()

        // Play counts must not influence a plain shuffle at all.
        counts.values.forEach { assertTrue("share=$it", it in 850..1150) }
    }

    @Test
    fun `every track appears exactly once and the limit is honoured`() {
        val tracks = (1..10).map { track("t$it", it) }

        val all = LocalShuffle.order(tracks, "discover", limit = 100, random = Random(1))
        assertEquals(10, all.size)
        assertEquals(10, all.map { it.id }.toSet().size)

        assertEquals(3, LocalShuffle.order(tracks, "discover", limit = 3, random = Random(1)).size)
    }

    @Test
    fun `an empty library yields an empty queue`() {
        assertEquals(emptyList<Track>(), LocalShuffle.order(emptyList(), "discover"))
    }
}
