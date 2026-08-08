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
    <div className="dialog-backdrop" onMouseDown={onClose}>
      <form className="dialog" onMouseDown={e => e.stopPropagation()} onSubmit={submit}>
        <div className="dialog-title">Редактировать трек</div>

        <div className="field">
          <label htmlFor="edit-title">Название</label>
          <input id="edit-title" className="input" value={title}
                 onChange={e => setTitle(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="edit-artist">Исполнитель</label>
          <input id="edit-artist" className="input" value={artist}
                 onChange={e => setArtist(e.target.value)} required />
        </div>

        <label className="checkbox">
          <input type="checkbox" checked={isPublic} onChange={e => setIsPublic(e.target.checked)} />
          <span className="dot" />
          <span>Публичный трек — виден всем в Общем банке</span>
        </label>

        {err && <div className="error">{err}</div>}

        <div className="dialog-actions">
          <button type="button" className="btn btn-secondary" onClick={onClose}>Отмена</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? "Сохранение…" : "Сохранить"}
          </button>
        </div>
      </form>
    </div>
  );
}
