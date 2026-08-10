/**
 * Merged half-open intervals of a track's timeline that were actually heard.
 *
 * This is what makes "listened seconds" honest: a seek forward adds nothing (the skipped
 * stretch is never inserted), and rewinding to replay the same chorus three times still counts
 * once, because overlapping intervals are merged rather than summed.
 */
export class CoverageSet {
  private intervals: Array<[number, number]> = [];

  /** Adds `[from, to)`. Out-of-order and overlapping inserts are fine. */
  add(from: number, to: number): void {
    if (!isFinite(from) || !isFinite(to) || to <= from) return;

    const next: Array<[number, number]> = [];
    let start = from;
    let end = to;
    let inserted = false;

    for (const [s, e] of this.intervals) {
      if (e < start) {
        next.push([s, e]);           // entirely before the new one
      } else if (s > end) {
        if (!inserted) { next.push([start, end]); inserted = true; }
        next.push([s, e]);           // entirely after
      } else {
        start = Math.min(start, s);  // overlaps or touches — absorb it
        end = Math.max(end, e);
      }
    }
    if (!inserted) next.push([start, end]);

    this.intervals = next;
  }

  get seconds(): number {
    return this.intervals.reduce((sum, [s, e]) => sum + (e - s), 0);
  }

  reset(): void {
    this.intervals = [];
  }
}
