package ru.wavelink.app.playtracking

/**
 * Merged half-open intervals of a track's timeline that were actually rendered.
 *
 * Mirrors the web client's `CoverageSet`. This is what makes "listened seconds" honest: skipping
 * forward adds nothing (the gap is never inserted), and rewinding to replay the same chorus three
 * times still counts once, because overlapping intervals merge instead of summing.
 */
class CoverageSet {

    private val starts = ArrayList<Long>()
    private val ends = ArrayList<Long>()

    /** Adds `[fromMs, toMs)`. Out-of-order and overlapping inserts are fine. */
    fun add(fromMs: Long, toMs: Long) {
        if (toMs <= fromMs) return

        var start = fromMs
        var end = toMs
        val newStarts = ArrayList<Long>(starts.size + 1)
        val newEnds = ArrayList<Long>(ends.size + 1)
        var inserted = false

        for (i in starts.indices) {
            val s = starts[i]
            val e = ends[i]
            when {
                e < start -> { newStarts.add(s); newEnds.add(e) }          // entirely before
                s > end -> {
                    if (!inserted) { newStarts.add(start); newEnds.add(end); inserted = true }
                    newStarts.add(s); newEnds.add(e)                       // entirely after
                }
                else -> {                                                  // overlaps or touches
                    start = minOf(start, s)
                    end = maxOf(end, e)
                }
            }
        }
        if (!inserted) { newStarts.add(start); newEnds.add(end) }

        starts.clear(); starts.addAll(newStarts)
        ends.clear(); ends.addAll(newEnds)
    }

    val coveredMs: Long
        get() {
            var total = 0L
            for (i in starts.indices) total += ends[i] - starts[i]
            return total
        }

    val coveredSeconds: Int get() = (coveredMs / 1000L).toInt()

    fun reset() {
        starts.clear()
        ends.clear()
    }
}
