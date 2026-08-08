import { useState } from "react";
import { addTrackToCollection } from "../api/collections";
import { useAppShell } from "../app/AppShellContext";

export function AddToCollectionMenu({ trackId, onDone }: { trackId: string; onDone: () => void }) {
  const { collections, refreshCollections } = useAppShell();
  const [err, setErr] = useState<string | null>(null);

  const add = async (id: string) => {
    try {
      await addTrackToCollection(id, trackId);
      void refreshCollections();
      onDone();
    } catch (e: any) { setErr(e.message); }
  };

  return (
    <div className="popover">
      <div className="popover-title">Добавить в коллекцию</div>
      {err && <div className="error">{err}</div>}
      {collections.length === 0 && <div className="muted small" style={{ padding: "6px 8px" }}>Коллекций ещё нет.</div>}
      {collections.map(c => (
        <button key={c.id} type="button" className="popover-item" onClick={() => add(c.id)}>
          {c.name} <span className="muted small">({c.trackCount})</span>
        </button>
      ))}
    </div>
  );
}
