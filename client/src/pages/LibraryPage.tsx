import { useEffect, useState } from "react";
import { deleteTrack, listTracks, unsaveTrack } from "../api/tracks";
import type { Track, TrackSort } from "../types";
import { UploadModal } from "../components/UploadModal";
import { TrackRow } from "../components/TrackRow";

export function LibraryPage() {
  const [tracks, setTracks] = useState<Track[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<TrackSort>("recent");

  const reload = async () => {
    setLoading(true); setErr(null);
    try { const res = await listTracks(1, 200, sort); setTracks(res.items); }
    catch (e: any) { setErr(e.message); }
    finally { setLoading(false); }
  };

  useEffect(() => { reload(); /* eslint-disable-next-line */ }, [sort]);

  const onDelete = async (t: Track) => {
    if (!confirm(`Удалить "${t.title}"?`)) return;
    try { await deleteTrack(t.id); setTracks(prev => prev.filter(x => x.id !== t.id)); }
    catch (e: any) { alert(e.message); }
  };

  const onUnsave = async (t: Track) => {
    if (!confirm(`Убрать "${t.title}" из библиотеки?`)) return;
    try { await unsaveTrack(t.id); setTracks(prev => prev.filter(x => x.id !== t.id)); }
    catch (e: any) { alert(e.message); }
  };

  const onUpdated = (updated: Track) =>
    setTracks(prev => prev.map(t => t.id === updated.id ? updated : t));

  const filtered = search
    ? tracks.filter(t =>
        t.title.toLowerCase().includes(search.toLowerCase()) ||
        t.artist.toLowerCase().includes(search.toLowerCase()))
    : tracks;

  return (
    <div className="page">
      <div className="page-header">
        <h1>Библиотека</h1>
        <div className="page-actions">
          <select
            className="select"
            value={sort}
            onChange={e => setSort(e.target.value as TrackSort)}
            title="Сортировка"
          >
            <option value="recent">Недавние</option>
            <option value="artist">По исполнителю</option>
            <option value="title">По названию</option>
          </select>
          <input
            className="search"
            placeholder="Поиск…"
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          <button className="btn-primary" onClick={() => setUploadOpen(true)}>
            Загрузить
          </button>
        </div>
      </div>

      {err && <div className="error">{err}</div>}
      {loading && <div className="muted">Загрузка…</div>}
      {!loading && filtered.length === 0 && (
        <div className="empty">
          {search ? "Треки не найдены." : "Библиотека пуста. Загрузите трек или посмотрите Общий банк."}
        </div>
      )}

      <div className="track-list">
        {filtered.map(t => (
          <TrackRow
            key={t.id}
            track={t}
            queue={filtered}
            onDelete={onDelete}
            onUpdated={onUpdated}
            onUnsave={onUnsave}
          />
        ))}
      </div>

      {uploadOpen && (
        <UploadModal onClose={() => setUploadOpen(false)} onUploaded={reload} />
      )}
    </div>
  );
}
