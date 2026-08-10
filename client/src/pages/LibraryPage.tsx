import { useEffect, useMemo, useState } from "react";
import { deleteTrack, listTracks, unsaveTrack } from "../api/tracks";
import type { Track, TrackSort } from "../types";
import { TrackRow } from "../components/TrackRow";
import { ShuffleButtons } from "../components/ShuffleButtons";
import { SearchIcon } from "../components/Icons";
import { useAppShell } from "../app/AppShellContext";

const SORTS: { value: TrackSort; label: string }[] = [
  { value: "recent", label: "Недавние" },
  { value: "artist", label: "Исполнитель" },
  { value: "title", label: "Название" }
];

function totalDuration(tracks: Track[]) {
  const sec = tracks.reduce((a, t) => a + (t.duration || 0), 0);
  const h = Math.floor(sec / 3600);
  const m = Math.round((sec % 3600) / 60);
  return h ? `${h} ч ${m} мин` : `${m} мин`;
}

export function LibraryPage() {
  const { tracksVersion } = useAppShell();
  const [tracks, setTracks] = useState<Track[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<TrackSort>("recent");

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true); setErr(null);
      try {
        const res = await listTracks(1, 200, sort);
        if (!cancelled) setTracks(res.items);
      } catch (e: any) {
        if (!cancelled) setErr(e.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [sort, tracksVersion]);

  const onDelete = async (t: Track) => {
    if (!confirm(`Удалить «${t.title}»?`)) return;
    try { await deleteTrack(t.id); setTracks(prev => prev.filter(x => x.id !== t.id)); }
    catch (e: any) { alert(e.message); }
  };

  const onUnsave = async (t: Track) => {
    if (!confirm(`Убрать «${t.title}» из библиотеки?`)) return;
    try { await unsaveTrack(t.id); setTracks(prev => prev.filter(x => x.id !== t.id)); }
    catch (e: any) { alert(e.message); }
  };

  const onUpdated = (updated: Track) =>
    setTracks(prev => prev.map(t => t.id === updated.id ? updated : t));

  const filtered = useMemo(() => {
    if (!search) return tracks;
    const q = search.toLowerCase();
    return tracks.filter(t =>
      t.title.toLowerCase().includes(q) || t.artist.toLowerCase().includes(q));
  }, [tracks, search]);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Библиотека</h1>
          <div className="page-sub">
            {tracks.length} треков · {totalDuration(tracks)}
          </div>
        </div>
        <div className="page-actions">
          <ShuffleButtons />
          <div className="input-search search">
            <SearchIcon />
            <input
              className="input"
              placeholder="Поиск…"
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
          </div>
          <div className="seg">
            {SORTS.map(s => (
              <button
                key={s.value}
                type="button"
                className={`seg-opt ${sort === s.value ? "is-active" : ""}`}
                onClick={() => setSort(s.value)}
              >{s.label}</button>
            ))}
          </div>
        </div>
      </div>

      {err && <div className="error">{err}</div>}
      {loading && <div className="muted">Загрузка…</div>}
      {!loading && filtered.length === 0 && (
        <div className="empty">
          {search
            ? "Треки не найдены."
            : "Библиотека пуста. Загрузите трек или посмотрите Общий банк."}
        </div>
      )}

      <div className="track-list">
        {filtered.map((t, i) => (
          <TrackRow
            key={t.id}
            track={t}
            index={i + 1}
            queue={filtered}
            onDelete={onDelete}
            onUpdated={onUpdated}
            onUnsave={onUnsave}
          />
        ))}
      </div>
    </div>
  );
}
