import { useEffect, useState } from "react";
import { deleteTrack, listTracks } from "../api/tracks";
import type { Track } from "../types";
import { UploadModal } from "../components/UploadModal";
import { TrackRow } from "../components/TrackRow";

export function LibraryPage() {
  const [tracks, setTracks] = useState<Track[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [search, setSearch] = useState("");

  const reload = async () => {
    setLoading(true); setErr(null);
    try { const res = await listTracks(1, 200); setTracks(res.items); }
    catch (e: any) { setErr(e.message); }
    finally { setLoading(false); }
  };

  useEffect(() => { reload(); }, []);

  const onDelete = async (t: Track) => {
    if (!confirm(`Удалить "${t.title}"?`)) return;
    try { await deleteTrack(t.id); setTracks(prev => prev.filter(x => x.id !== t.id)); }
    catch (e: any) { alert(e.message); }
  };

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
          {search ? "Треки не найдены." : "Библиотека пуста. Загрузите первый трек!"}
        </div>
      )}

      <div className="track-list">
        {filtered.map(t => (
          <TrackRow key={t.id} track={t} queue={filtered} onDelete={onDelete} />
        ))}
      </div>

      {uploadOpen && (
        <UploadModal onClose={() => setUploadOpen(false)} onUploaded={reload} />
      )}
    </div>
  );
}
