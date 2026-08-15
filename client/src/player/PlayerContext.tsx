import { createContext, useCallback, useContext, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { getShuffle } from "../api/plays";
import type { ShuffleMode, Track } from "../types";

/** One shuffle page. Matches the server's own default. */
const QUEUE_PAGE = 50;
/** Fetch the next page once this few tracks remain ahead of the current one. */
const PREFETCH_AHEAD = 15;
/** How many already-played tracks stay behind the current one. */
const MAX_HISTORY = 100;
/** Repeats across cycles are the point of an endless queue, so only the tail is deduped. */
const DEDUPE_TAIL = 50;

/** Where the queue refills from. Null means a plain finite queue (a list the user clicked). */
interface ShuffleFeed {
  mode: ShuffleMode;
  collectionId?: string;
  seed: number;
  cursor: number;
  hasMore: boolean;
}

interface QueueState {
  current: Track | null;
  queue: Track[];
}

interface PlayerState extends QueueState {
  /** True while a shuffle feed is alive — the queue then refills instead of running out. */
  endless: boolean;
  queueLoading: boolean;
  play: (track: Track, queue?: Track[]) => void;
  next: () => void;
  prev: () => void;
  stop: () => void;
  /** Starts an endless shuffled queue and plays its first track. Returns how many it loaded. */
  startShuffle: (mode: ShuffleMode, collectionId?: string) => Promise<number>;
  loadMoreQueue: () => void;
  /** Drops everything after the current track and stops refilling. */
  clearQueue: () => void;
}

const Ctx = createContext<PlayerState | null>(null);

/**
 * Appends a page, skipping what is still fresh in the tail. A short library would otherwise
 * lose a whole cycle to the dedupe, so the window never exceeds the cycle minus one — enough
 * to keep a track from following itself, never enough to starve the queue.
 */
function appendPage(prev: Track[], items: Track[], cycleTotal: number): Track[] {
  const window = Math.min(DEDUPE_TAIL, Math.max(cycleTotal - 1, 0));
  const tail = new Set(prev.slice(-window).map(t => t.id));
  const fresh = items.filter(t => !tail.has(t.id));
  return [...prev, ...(fresh.length ? fresh : items)];
}

/** Keeps the played-out head from growing for the whole session. */
function trimHistory(state: QueueState): QueueState {
  const { current, queue } = state;
  if (!current) return state;
  const idx = queue.findIndex(t => t.id === current.id);
  return idx > MAX_HISTORY ? { current, queue: queue.slice(idx - MAX_HISTORY) } : state;
}

export function PlayerProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<QueueState>({ current: null, queue: [] });
  const [endless, setEndless] = useState(false);
  const [queueLoading, setQueueLoading] = useState(false);

  // The async refill reads the queue it is appending to, so state is mirrored into a ref.
  const stateRef = useRef(state);
  const feedRef = useRef<ShuffleFeed | null>(null);
  const loadingRef = useRef(false);
  /** Set when `next` ran out of loaded tracks: the arriving page advances the player itself. */
  const pendingNextRef = useRef(false);

  const commit = useCallback((next: QueueState) => {
    stateRef.current = next;
    setState(next);
  }, []);

  const dropFeed = useCallback(() => {
    feedRef.current = null;
    pendingNextRef.current = false;
    setEndless(false);
  }, []);

  const loadMoreQueue = useCallback(async () => {
    const feed = feedRef.current;
    if (!feed || loadingRef.current) return;
    loadingRef.current = true;
    setQueueLoading(true);
    try {
      // A spent cycle rolls into a fresh one — that is what makes the queue endless. In discover
      // mode the new cycle is already reweighted by whatever was played in the old one.
      const rollover = !feed.hasMore;
      const page = await getShuffle(feed.mode, {
        limit: QUEUE_PAGE,
        collectionId: feed.collectionId,
        seed: rollover ? undefined : feed.seed,
        cursor: rollover ? 0 : feed.cursor
      });
      if (feedRef.current !== feed) return;      // a new shuffle started while this was in flight
      if (page.total === 0) { dropFeed(); return; }

      feedRef.current = { ...feed, seed: page.seed, cursor: page.nextCursor, hasMore: page.hasMore };
      if (page.items.length === 0) return;

      const { current, queue } = stateRef.current;
      const merged = appendPage(queue, page.items, page.total);
      let head = current;
      if (pendingNextRef.current && current) {
        const idx = merged.findIndex(t => t.id === current.id);
        if (idx >= 0 && idx + 1 < merged.length) {
          pendingNextRef.current = false;
          head = merged[idx + 1];
        }
      }
      commit(trimHistory({ current: head, queue: merged }));
    } catch {
      // Leave the queue as it stands; the next advance retries.
    } finally {
      loadingRef.current = false;
      setQueueLoading(false);
    }
  }, [commit, dropFeed]);

  const startShuffle = useCallback(async (mode: ShuffleMode, collectionId?: string) => {
    loadingRef.current = true;
    setQueueLoading(true);
    try {
      const page = await getShuffle(mode, { limit: QUEUE_PAGE, collectionId });
      if (page.items.length === 0) { dropFeed(); return 0; }
      feedRef.current = {
        mode, collectionId, seed: page.seed, cursor: page.nextCursor, hasMore: page.hasMore
      };
      pendingNextRef.current = false;
      setEndless(true);
      commit({ current: page.items[0], queue: page.items });
      return page.items.length;
    } finally {
      loadingRef.current = false;
      setQueueLoading(false);
    }
  }, [commit, dropFeed]);

  // The controls read the queue through the ref, not through this closure: the player registers
  // its `finish` handler once per track, so a callback that captured `state` would still hold the
  // queue as it looked before the last refill.
  const value = useMemo<PlayerState>(() => ({
    current: state.current,
    queue: state.queue,
    endless,
    queueLoading,
    startShuffle,

    clearQueue: () => {
      dropFeed();
      const { current, queue } = stateRef.current;
      if (!current) { commit({ current: null, queue: [] }); return; }
      const idx = queue.findIndex(t => t.id === current.id);
      commit({ current, queue: idx >= 0 ? queue.slice(0, idx + 1) : [current] });
    },

    loadMoreQueue: () => { void loadMoreQueue(); },

    play: (track, q) => {
      if (q && q.length) {
        // A queue handed in from a list is finite by nature and replaces the shuffle feed.
        dropFeed();
        commit({ current: track, queue: q });
        return;
      }
      // No queue given: a click inside the current one (the «Далее» panel) keeps it and the
      // feed alive; anything else starts a queue of one.
      const { queue } = stateRef.current;
      if (queue.some(t => t.id === track.id)) commit({ current: track, queue });
      else { dropFeed(); commit({ current: track, queue: [track] }); }
    },

    next: () => {
      const { current, queue } = stateRef.current;
      if (!current) return;
      const idx = queue.findIndex(t => t.id === current.id);
      if (idx < 0) return;
      if (feedRef.current && queue.length - 1 - idx <= PREFETCH_AHEAD) void loadMoreQueue();
      if (idx + 1 < queue.length) commit(trimHistory({ current: queue[idx + 1], queue }));
      else if (feedRef.current) pendingNextRef.current = true;   // the page in flight resumes it
    },

    prev: () => {
      const { current, queue } = stateRef.current;
      if (!current) return;
      const idx = queue.findIndex(t => t.id === current.id);
      if (idx > 0) commit({ current: queue[idx - 1], queue });
    },

    stop: () => { dropFeed(); commit({ current: null, queue: [] }); }
  }), [state, endless, queueLoading, commit, dropFeed, loadMoreQueue, startShuffle]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function usePlayer() {
  const v = useContext(Ctx);
  if (!v) throw new Error("usePlayer must be used inside PlayerProvider");
  return v;
}
