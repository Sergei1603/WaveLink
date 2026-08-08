import { createContext, useCallback, useContext, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { listCollections } from "../api/collections";
import type { CollectionSummary } from "../types";

interface AppShellState {
  /** Opens the upload dialog owned by the shell (the header button). */
  openUpload: () => void;
  /** Bumps whenever the track set changed; pages re-fetch on it. */
  tracksVersion: number;
  bumpTracks: () => void;
  collections: CollectionSummary[];
  refreshCollections: () => Promise<void>;
}

const Ctx = createContext<AppShellState | null>(null);

export function AppShellProvider({
  children, openUpload
}: { children: ReactNode; openUpload: () => void }) {
  const [tracksVersion, setTracksVersion] = useState(0);
  const [collections, setCollections] = useState<CollectionSummary[]>([]);

  const refreshCollections = useCallback(async () => {
    try { setCollections(await listCollections()); }
    catch { /* the collections list is decorative in the shell */ }
  }, []);

  const bumpTracks = useCallback(() => setTracksVersion(v => v + 1), []);

  const value = useMemo<AppShellState>(
    () => ({ openUpload, tracksVersion, bumpTracks, collections, refreshCollections }),
    [openUpload, tracksVersion, bumpTracks, collections, refreshCollections]
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAppShell(): AppShellState {
  const v = useContext(Ctx);
  if (!v) throw new Error("useAppShell must be used inside AppShellProvider");
  return v;
}
