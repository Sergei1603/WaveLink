import { useEffect, useState } from "react";
import { addTrackToCollection, listCollections } from "../api/collections";
import type { CollectionSummary } from "../types";

export function AddToCollectionMenu({ trackId, onDone }: { trackId: string; onDone: () => void }) {
  const [items, setItems] = useState<CollectionSummary[]>([]);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    listCollections().then(setItems).catch(e => setErr(e.message));
  }, []);

  const add = async (id: string) => {
    try { await addTrackToCollection(id, trackId); onDone(); }
    catch (e: any) { setErr(e.message); }
  };

  return (
    <div className="popover">
      <div className="popover-title">Add to collection</div>
      {err && <div className="error">{err}</div>}
      {items.length === 0 && <div className="muted">No collections yet.</div>}
      {items.map(c => (
        <button key={c.id} className="popover-item" onClick={() => add(c.id)}>
          {c.name} <span className="muted">({c.trackCount})</span>
        </button>
      ))}
    </div>
  );
}
