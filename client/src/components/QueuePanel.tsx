import { useEffect, useRef, useState } from "react";
import { usePlayer } from "../player/PlayerContext";

function fmt(s: number) {
  if (!s) return "—";
  const m = Math.floor(s / 60);
  const r = Math.floor(s % 60);
  return `${m}:${r.toString().padStart(2, "0")}`;
}

function tracksLabel(n: number) {
  const last = n % 10, tens = n % 100;
  if (last === 1 && tens !== 11) return `${n} трек`;
  if (last >= 2 && last <= 4 && (tens < 12 || tens > 14)) return `${n} трека`;
  return `${n} треков`;
}

/**
 * What plays after the current track. In shuffle mode the list only ever holds the pages loaded
 * so far — scrolling to the bottom pulls the next one, and the player itself prefetches ahead of
 * playback, so the list never actually ends.
 */
export function QueuePanel() {
  const { current, queue, endless, queueLoading, play, loadMoreQueue, clearQueue } = usePlayer();
  const [open, setOpen] = useState(true);
  const sentinelRef = useRef<HTMLDivElement | null>(null);

  const idx = current ? queue.findIndex(t => t.id === current.id) : -1;
  const upcoming = idx >= 0 ? queue.slice(idx + 1) : [];

  useEffect(() => {
    const el = sentinelRef.current;
    if (!el || !open || !endless || queueLoading) return;
    const io = new IntersectionObserver(
      entries => { if (entries[0].isIntersecting) loadMoreQueue(); },
      { rootMargin: "200px" }
    );
    io.observe(el);
    return () => io.disconnect();
  }, [open, endless, queueLoading, upcoming.length, loadMoreQueue]);

  if (!current) return null;

  return (
    <div className="now-queue">
      <div className="now-queue-head">
        <button
          type="button"
          className="now-queue-toggle"
          onClick={() => setOpen(v => !v)}
          aria-expanded={open}
        >
          <span className="section-label">Далее · {tracksLabel(upcoming.length)}</span>
          <span className="chev">{open ? "⌄" : "›"}</span>
        </button>
        {upcoming.length > 0 && (
          <button type="button" className="now-queue-clear" onClick={clearQueue}>Очистить</button>
        )}
      </div>

      {open && (
        <div className="now-queue-list">
          {upcoming.length === 0 && !queueLoading && (
            <span className="muted small">
              {endless ? "Догружаем…" : "Очередь пуста."}
            </span>
          )}

          {upcoming.map((t, i) => (
            <button
              key={`${t.id}-${idx + 1 + i}`}
              type="button"
              className="now-queue-row"
              onClick={() => play(t)}
              title={`${t.title} — ${t.artist}`}
            >
              <span className="pos">{i + 1}</span>
              <span className="meta">
                <span className="t">{t.title}</span>
                <span className="a">{t.artist}</span>
              </span>
              <span className="dur">{fmt(t.duration)}</span>
            </button>
          ))}

          {queueLoading && <span className="muted small">Загрузка…</span>}
          <div ref={sentinelRef} />
        </div>
      )}
    </div>
  );
}
