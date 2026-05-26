import { createContext, useContext, useMemo, useState } from "react";
import type { ReactNode } from "react";
import type { Track } from "../types";

interface PlayerState {
  current: Track | null;
  queue: Track[];
  play: (track: Track, queue?: Track[]) => void;
  next: () => void;
  prev: () => void;
  stop: () => void;
}

const Ctx = createContext<PlayerState | null>(null);

export function PlayerProvider({ children }: { children: ReactNode }) {
  const [current, setCurrent] = useState<Track | null>(null);
  const [queue, setQueue] = useState<Track[]>([]);

  const value = useMemo<PlayerState>(() => ({
    current, queue,
    play: (track, q) => {
      if (q && q.length) setQueue(q);
      else setQueue(prev => prev.find(t => t.id === track.id) ? prev : [track]);
      setCurrent(track);
    },
    next: () => {
      if (!current) return;
      const idx = queue.findIndex(t => t.id === current.id);
      if (idx >= 0 && idx + 1 < queue.length) setCurrent(queue[idx + 1]);
    },
    prev: () => {
      if (!current) return;
      const idx = queue.findIndex(t => t.id === current.id);
      if (idx > 0) setCurrent(queue[idx - 1]);
    },
    stop: () => setCurrent(null)
  }), [current, queue]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function usePlayer() {
  const v = useContext(Ctx);
  if (!v) throw new Error("usePlayer must be used inside PlayerProvider");
  return v;
}
