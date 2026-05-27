import { useState } from "react";
import { uploadTrack } from "../api/tracks";

interface Props {
  onClose: () => void;
  onUploaded: () => void;
}

interface QueueItem {
  file: File;
  title: string;
  artist: string;
  status: "pending" | "uploading" | "done" | "duplicate" | "failed";
  message?: string;
}

async function readDuration(file: File): Promise<number | undefined> {
  return new Promise(resolve => {
    const url = URL.createObjectURL(file);
    const audio = document.createElement("audio");
    audio.preload = "metadata";
    audio.src = url;
    audio.onloadedmetadata = () => {
      URL.revokeObjectURL(url);
      resolve(isFinite(audio.duration) ? audio.duration : undefined);
    };
    audio.onerror = () => { URL.revokeObjectURL(url); resolve(undefined); };
  });
}

function defaultTitle(name: string) { return name.replace(/\.[^.]+$/, ""); }

export function UploadModal({ onClose, onUploaded }: Props) {
  const [items, setItems] = useState<QueueItem[]>([]);
  const [isPublic, setIsPublic] = useState(false);
  const [busy, setBusy] = useState(false);

  const onFiles = (files: FileList | null) => {
    if (!files) return;
    const arr: QueueItem[] = [];
    for (const f of Array.from(files)) {
      arr.push({
        file: f,
        title: defaultTitle(f.name),
        artist: "Unknown",
        status: "pending"
      });
    }
    setItems(prev => [...prev, ...arr]);
  };

  const updateItem = (idx: number, patch: Partial<QueueItem>) =>
    setItems(prev => prev.map((it, i) => i === idx ? { ...it, ...patch } : it));

  const removeItem = (idx: number) =>
    setItems(prev => prev.filter((_, i) => i !== idx));

  const runUpload = async () => {
    if (items.length === 0) return;
    setBusy(true);
    let anySuccess = false;
    for (let i = 0; i < items.length; i++) {
      const it = items[i];
      if (it.status === "done") continue;
      updateItem(i, { status: "uploading", message: undefined });
      try {
        const duration = await readDuration(it.file);
        await uploadTrack(it.file, it.title.trim(), it.artist.trim(), isPublic, duration);
        updateItem(i, { status: "done" });
        anySuccess = true;
      } catch (e: any) {
        const msg = e?.message ?? "Ошибка";
        const isDup = /already have|already in your library/i.test(msg);
        updateItem(i, { status: isDup ? "duplicate" : "failed", message: msg });
      }
    }
    setBusy(false);
    if (anySuccess) onUploaded();
  };

  const allDone = items.length > 0 && items.every(i => i.status === "done");
  const hasPending = items.some(i => i.status === "pending" || i.status === "failed");

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <form
        className="modal modal-wide"
        onMouseDown={e => e.stopPropagation()}
        onSubmit={e => { e.preventDefault(); runUpload(); }}
      >
        <h2>Загрузить треки</h2>

        <label className="filepicker">
          <input
            type="file"
            accept="audio/*"
            multiple
            onChange={e => onFiles(e.target.files)}
          />
          <span>{items.length === 0
            ? "Выберите один или несколько аудиофайлов…"
            : `Добавить ещё (выбрано ${items.length})`}</span>
        </label>

        {items.length > 0 && (
          <div className="upload-queue">
            {items.map((it, idx) => (
              <div key={idx} className={`upload-item upload-${it.status}`}>
                <div className="upload-fields">
                  <input
                    placeholder="Название"
                    value={it.title}
                    onChange={e => updateItem(idx, { title: e.target.value })}
                    disabled={it.status === "uploading" || it.status === "done"}
                  />
                  <input
                    placeholder="Исполнитель"
                    value={it.artist}
                    onChange={e => updateItem(idx, { artist: e.target.value })}
                    disabled={it.status === "uploading" || it.status === "done"}
                  />
                </div>
                <div className="upload-status">
                  {it.status === "pending" && <span className="muted small">Ожидает</span>}
                  {it.status === "uploading" && <span className="muted small">Загрузка…</span>}
                  {it.status === "done" && <span className="success small">✓ Готово</span>}
                  {it.status === "duplicate" && <span className="warn small" title={it.message}>Дубликат</span>}
                  {it.status === "failed" && <span className="error-text small" title={it.message}>Ошибка</span>}
                </div>
                {it.status !== "uploading" && (
                  <button
                    type="button"
                    className="btn-ghost small-btn"
                    onClick={() => removeItem(idx)}
                    title="Убрать из очереди"
                  >✕</button>
                )}
              </div>
            ))}
          </div>
        )}

        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={isPublic}
            onChange={e => setIsPublic(e.target.checked)}
            disabled={busy}
          />
          <span>Сделать публичными (доступны всем в Общем банке)</span>
        </label>

        <div className="modal-actions">
          <button type="button" className="btn-ghost" onClick={onClose} disabled={busy}>
            {allDone ? "Закрыть" : "Отмена"}
          </button>
          <button type="submit" className="btn-primary" disabled={busy || !hasPending}>
            {busy ? "Загрузка…" : allDone ? "Все загружены" : `Загрузить (${items.filter(i => i.status === "pending" || i.status === "failed").length})`}
          </button>
        </div>
      </form>
    </div>
  );
}
