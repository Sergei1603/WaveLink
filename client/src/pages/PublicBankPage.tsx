import { useEffect, useState } from "react";
import { listPublicTracks, listTracks, saveTrack } from "../api/tracks";
import type { Track, TrackSort } from "../types";
import { TrackRow } from "../components/TrackRow";

export function PublicBankPage() {
  const [tracks, setTracks] = useState<Track[]>([]);
  const [myIds, setMyIds] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<TrackSort>("recent");

  const reload = async () => {
    setLoading(true); setErr(null);
    try {
      const [pub, mine] = await Promise.all([
        listPublicTracks(1, 200, search, sort),
        listTracks(1, 200, "recent")
      ]);
      setTracks(pub.items);
      setMyIds(new Set(mine.items.map(t => t.id)));
    } catch (e: any) { setErr(e.message); }
    finally { setLoading(false); }
  };

  useEffect(() => {
    const timer = setTimeout(reload, search ? 300 : 0);
    return () => clearTimeout(timer);
    /* eslint-disable-next-line */
  }, [sort, search]);

  const onSave = async (t: Track) => {
    try {
      await saveTrack(t.id);
      setMyIds(prev => new Set(prev).add(t.id));
    } catch (e: any) { alert(e.message); }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Общий банк</h1>
        <div className="page-actions">
          <select
            className="select"
            value={sort}
            onChange={e => setSort(e.target.value as TrackSort)}
          >
            <option value="recent">Недавние</option>
            <option value="artist">По исполнителю</option>
            <option value="title">По названию</option>
          </select>
          <input
            className="search"
            placeholder="Поиск по названию и исполнителю…"
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
        </div>
      </div>

      {err && <div className="error">{err}</div>}
      {loading && <div className="muted">Загрузка…</div>}
      {!loading && tracks.length === 0 && (
        <div className="empty">
          {search ? "Ничего не найдено." : "Публичных треков пока нет."}
        </div>
      )}

      <div className="track-list">
        {tracks.map(t => (
          <TrackRow
            key={t.id}
            track={t}
            queue={tracks}
            onSave={onSave}
            isSaved={myIds.has(t.id)}
          />
        ))}
      </div>
    </div>
  );
}
