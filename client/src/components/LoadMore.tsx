import type { MutableRefObject } from "react";

interface Props {
  /** Sentinel from `usePagedTracks` — the observer it feeds is what makes the scroll infinite. */
  sentinelRef: MutableRefObject<HTMLDivElement | null>;
  remaining: number;
  loading: boolean;
  onLoadMore: () => void;
}

/** Tail of a paged track list: the scroll sentinel plus an explicit button as a fallback. */
export function LoadMore({ sentinelRef, remaining, loading, onLoadMore }: Props) {
  return (
    <div className="load-more" ref={sentinelRef}>
      <button
        type="button"
        className="btn btn-secondary"
        disabled={loading}
        onClick={onLoadMore}
      >
        {loading ? "Загрузка…" : `Показать ещё (${remaining})`}
      </button>
    </div>
  );
}
