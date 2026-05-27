import { useState } from "react";
import type { Track } from "../types";
import { updateTrack } from "../api/tracks";

interface Props {
  track: Track;
  onClose: () => void;
  onSaved: (updated: Track) => void;
}

export function EditTrackModal({ track, onClose, onSaved }: Props) {
  const [title, setTitle] = useState(track.title);
  const [artist, setArtist] = useState(track.artist);
  const [isPublic, setIsPublic] = useState(track.isPublic);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErr(null); setBusy(true);
    try {
      const updated = await updateTrack(track.id, {
        title: title.trim(),
        artist: artist.trim(),
        isPublic
      });
      onSaved(updated);
      onClose();
    } catch (e: any) {
      setErr(e?.message ?? "Ошибка сохранения");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <form className="modal" onMouseDown={e => e.stopPropagation()} onSubmit={submit}>
        <h2>Редактировать трек</h2>
        <label>
          Название
          <input value={title} onChange={e => setTitle(e.target.value)} required />
        </label>
        <label>
          Исполнитель
          <input value={artist} onChange={e => setArtist(e.target.value)} required />
        </label>
        <label className="checkbox-row">
          <input type="checkbox" checked={isPublic} onChange={e => setIsPublic(e.target.checked)} />
          <span>Публичный трек (виден всем в Общем банке)</span>
        </label>
        {err && <div className="error">{err}</div>}
        <div className="modal-actions">
          <button type="button" className="btn-ghost" onClick={onClose}>Отмена</button>
          <button type="submit" className="btn-primary" disabled={busy}>
            {busy ? "Сохранение…" : "Сохранить"}
          </button>
        </div>
      </form>
    </div>
  );
}
