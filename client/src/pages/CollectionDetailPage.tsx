import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getCollection, removeTrackFromCollection } from "../api/collections";
import type { CollectionDetail, Track } from "../types";
import { TrackRow } from "../components/TrackRow";

export function CollectionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [data, setData] = useState<CollectionDetail | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const reload = async () => {
    if (!id) return;
    setLoading(true); setErr(null);
    try { setData(await getCollection(id)); }
    catch (e: any) { setErr(e.message); }
    finally { setLoading(false); }
  };

  useEffect(() => { reload(); }, [id]);

  const onRemove = async (t: Track) => {
    if (!id) return;
    try {
      await removeTrackFromCollection(id, t.id);
      setData(d => d ? { ...d, tracks: d.tracks.filter(x => x.id !== t.id) } : d);
    } catch (e: any) { alert(e.message); }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <Link to="/collections" className="muted">← Коллекции</Link>
          <h1>{data?.name ?? "…"}</h1>
        </div>
      </div>

      {err && <div className="error">{err}</div>}
      {loading && <div className="muted">Загрузка…</div>}
      {data && data.tracks.length === 0 && (
        <div className="empty">
          Коллекция пуста. Откройте Библиотеку и нажмите «＋» на треке, чтобы добавить его.
        </div>
      )}

      <div className="track-list">
        {data?.tracks.map(t => (
          <TrackRow
            key={t.id}
            track={t}
            queue={data.tracks}
            onRemoveFromCollection={onRemove}
          />
        ))}
      </div>
    </div>
  );
}
