import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getCollection, removeTrackFromCollection } from "../api/collections";
import { useAppShell } from "../app/AppShellContext";
import type { CollectionDetail, Track } from "../types";
import { TrackRow } from "../components/TrackRow";
import { ShuffleButtons } from "../components/ShuffleButtons";

export function CollectionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { refreshCollections } = useAppShell();
  const [data, setData] = useState<CollectionDetail | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    (async () => {
      setLoading(true); setErr(null);
      try {
        const res = await getCollection(id);
        if (!cancelled) setData(res);
      } catch (e: any) {
        if (!cancelled) setErr(e.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [id]);

  const onRemove = async (t: Track) => {
    if (!id) return;
    try {
      await removeTrackFromCollection(id, t.id);
      setData(d => d ? { ...d, tracks: d.tracks.filter(x => x.id !== t.id) } : d);
      void refreshCollections();
    } catch (e: any) { alert(e.message); }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <Link to="/collections" className="muted small">← Коллекции</Link>
          <h1>{data?.name ?? "…"}</h1>
          {data && <div className="page-sub">{data.tracks.length} треков</div>}
        </div>
        {data && data.tracks.length > 0 && (
          <div className="page-actions">
            <ShuffleButtons collectionId={id} />
          </div>
        )}
      </div>

      {err && <div className="error">{err}</div>}
      {loading && <div className="muted">Загрузка…</div>}
      {data && data.tracks.length === 0 && (
        <div className="empty">
          Коллекция пуста. Откройте Библиотеку и нажмите «＋» на треке, чтобы добавить его.
        </div>
      )}

      <div className="track-list">
        {data?.tracks.map((t, i) => (
          <TrackRow
            key={t.id}
            track={t}
            index={i + 1}
            queue={data.tracks}
            onRemoveFromCollection={onRemove}
          />
        ))}
      </div>
    </div>
  );
}
