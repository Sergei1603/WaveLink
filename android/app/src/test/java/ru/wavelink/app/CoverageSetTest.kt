package ru.wavelink.app

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.wavelink.app.playtracking.CoverageSet

class CoverageSetTest {

    @Test
    fun `sequential playback accumulates`() {
        val c = CoverageSet()
        c.add(0, 1000)
        c.add(1000, 2000)
        c.add(2000, 3000)
        assertEquals(3000, c.coveredMs)
    }

    @Test
    fun `replaying the same stretch counts once`() {
        val c = CoverageSet()
        c.add(0, 10_000)
        c.add(3_000, 7_000)   // rewound and heard the chorus again
        assertEquals(10_000, c.coveredMs)
    }

    @Test
    fun `a skipped gap is never credited`() {
        val c = CoverageSet()
        c.add(0, 20_000)
        // the listener seeks from 20s to 280s; nothing is inserted for the gap
        c.add(280_000, 300_000)
        assertEquals(40_000, c.coveredMs)
    }

    @Test
    fun `overlapping and out-of-order inserts merge`() {
        val c = CoverageSet()
        c.add(5_000, 8_000)
        c.add(0, 6_000)
        c.add(7_500, 12_000)
        assertEquals(12_000, c.coveredMs)
    }

    @Test
    fun `touching intervals merge without double counting`() {
        val c = CoverageSet()
        c.add(0, 5_000)
        c.add(5_000, 10_000)
        assertEquals(10_000, c.coveredMs)
    }

    @Test
    fun `empty and degenerate ranges are ignored`() {
        val c = CoverageSet()
        c.add(1_000, 1_000)
        c.add(2_000, 1_000)
        assertEquals(0, c.coveredMs)
    }
}
