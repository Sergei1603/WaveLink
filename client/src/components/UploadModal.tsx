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
  const [dragOver, setDragOver] = useState(false);

  const addFiles = (files: FileList | File[] | null) => {
    if (!files) return;
    const arr: QueueItem[] = Array.from(files)
      .filter(f => f.type.startsWith("audio/"))
      .map(f => ({
        file: f,
        title: defaultTitle(f.name),
        artist: "Unknown",
        status: "pending" as const
      }));
    if (arr.length) setItems(prev => [...prev, ...arr]);
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
  const pendingCount = items.filter(i => i.status === "pending" || i.status === "failed").length;

  return (
    <div className="dialog-backdrop" onMouseDown={onClose}>
      <form
        className="dialog dialog-wide"
        onMouseDown={e => e.stopPropagation()}
        onSubmit={e => { e.preventDefault(); runUpload(); }}
      >
        <div className="dialog-title">Загрузить треки</div>

        <label
          className="dropzone"
          style={dragOver ? { borderColor: "var(--color-accent)" } : undefined}
          onDragOver={e => { e.preventDefault(); setDragOver(true); }}
          onDragLeave={() => setDragOver(false)}
          onDrop={e => { e.preventDefault(); setDragOver(false); addFiles(e.dataTransfer.files); }}
        >
          <input type="file" accept="audio/*" multiple onChange={e => addFiles(e.target.files)} />
          Перетащите аудиофайлы или <span className="accent">выберите на диске</span>
          {items.length > 0 && (
            <>
              <br />
              <span className="hint">выбрано {items.length} {items.length === 1 ? "файл" : "файла(ов)"}</span>
            </>
          )}
        </label>

        {items.length > 0 && (
          <div className="upload-queue">
            {items.map((it, idx) => (
              <div key={idx} className={`upload-item upload-${it.status}`}>
                <div className="upload-fields">
                  <input
                    className="input input-sm"
                    placeholder="Название"
                    value={it.title}
                    onChange={e => updateItem(idx, { title: e.target.value })}
                    disabled={it.status === "uploading" || it.status === "done"}
                  />
                  <input
                    className="input input-sm"
                    placeholder="Исполнитель"
                    value={it.artist}
                    onChange={e => updateItem(idx, { artist: e.target.value })}
                    disabled={it.status === "uploading" || it.status === "done"}
                  />
                </div>
                <div className="upload-status">
                  {it.status === "pending" && <span className="muted">Ожидает</span>}
                  {it.status === "uploading" && <span className="muted">Загрузка…</span>}
                  {it.status === "done" && <span className="success">Готово</span>}
                  {it.status === "duplicate" && <span className="warn" title={it.message}>Дубликат</span>}
                  {it.status === "failed" && <span className="error-text" title={it.message}>Ошибка</span>}
                </div>
                {it.status !== "uploading" ? (
                  <button
                    type="button"
                    className="btn btn-danger btn-icon"
                    style={{ width: 28, height: 28 }}
                    onClick={() => removeItem(idx)}
                    title="Убрать из очереди"
                  >✕</button>
                ) : <span />}
              </div>
            ))}
          </div>
        )}

        <label className="checkbox">
          <input
            type="checkbox"
            checked={isPublic}
            onChange={e => setIsPublic(e.target.checked)}
            disabled={busy}
          />
          <span className="dot" />
          <span>Опубликовать в Общем банке</span>
        </label>

        <div className="dialog-actions">
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={busy}>
            {allDone ? "Закрыть" : "Отмена"}
          </button>
          <button type="submit" className="btn btn-primary" disabled={busy || pendingCount === 0}>
            {busy ? "Загрузка…" : allDone ? "Все загружены" : `Загрузить (${pendingCount})`}
          </button>
        </div>
      </form>
    </div>
  );
}
