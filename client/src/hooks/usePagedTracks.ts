import { useCallback, useEffect, useRef, useState } from "react";
import type { PagedTracks, Track } from "../types";

/** The server caps `limit` at 200, so every list arrives page by page. */
export const PAGE_SIZE = 100;

export type FetchTracksPage = (page: number, limit: number) => Promise<PagedTracks>;

/**
 * A track list that grows page by page. `fetchPage` carries the filters, so wrap it in
 * `useCallback` with those filters as deps — a new identity is what resets the list back to
 * page 1. `reloadToken` reloads without changing the filters (e.g. after an upload).
 */
export function usePagedTracks(fetchPage: FetchTracksPage, reloadToken: unknown = 0) {
  const [tracks, setTracks] = useState<Track[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  // Every reset starts a new generation; responses from an older one are dropped, otherwise
  // a slow page 2 could append into a list it no longer belongs to.
  const genRef = useRef(0);

  const load = useCallback(async (target: number) => {
    const gen = genRef.current;
    if (target === 1) setLoading(true); else setLoadingMore(true);
    setErr(null);
    try {
      const res = await fetchPage(target, PAGE_SIZE);
      if (gen !== genRef.current) return;
      setTracks(prev => {
        if (target === 1) return res.items;
        // Uploads and deletions shift rows between pages, so a page can overlap the previous one.
        const seen = new Set(prev.map(t => t.id));
        return [...prev, ...res.items.filter(t => !seen.has(t.id))];
      });
      setTotal(res.total);
      setPage(res.page);
    } catch (e: any) {
      if (gen === genRef.current) setErr(e.message);
    } finally {
      if (gen === genRef.current) { setLoading(false); setLoadingMore(false); }
    }
  }, [fetchPage]);

  useEffect(() => {
    genRef.current += 1;
    setTracks([]); setTotal(0); setPage(1);
    load(1);
  }, [load, reloadToken]);

  const hasMore = tracks.length < total;

  const sentinelRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    const el = sentinelRef.current;
    if (!el || !hasMore || loading || loadingMore) return;
    const io = new IntersectionObserver(
      entries => { if (entries[0].isIntersecting) load(page + 1); },
      { rootMargin: "400px" }
    );
    io.observe(el);
    return () => io.disconnect();
  }, [hasMore, loading, loadingMore, page, load]);

  const loadMore = useCallback(() => {
    if (!loadingMore) load(page + 1);
  }, [load, page, loadingMore]);

  /** Drops a row the caller already removed server-side; `total` follows so paging stays sane. */
  const drop = useCallback((id: string) => {
    setTracks(prev => prev.filter(t => t.id !== id));
    setTotal(prev => Math.max(prev - 1, 0));
  }, []);

  const replace = useCallback((updated: Track) => {
    setTracks(prev => prev.map(t => t.id === updated.id ? updated : t));
  }, []);

  return {
    tracks, total, loading, loadingMore, err, hasMore,
    sentinelRef, loadMore, drop, replace
  };
}
