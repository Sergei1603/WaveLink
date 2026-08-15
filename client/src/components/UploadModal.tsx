import { useRef, useState } from "react";
import { uploadTrack } from "../api/tracks";

interface Props {
  onClose: () => void;
  onUploaded: () => void;
}

interface QueueItem {
  id: number;
  file: File;
  title: string;
  artist: string;
  duration?: number;
  /** Set as soon as the user types in either field, so late-arriving tags never clobber manual input. */
  edited: boolean;
  reading: boolean;
  status: "pending" | "uploading" | "done" | "duplicate" | "failed";
  message?: string;
}

interface FileTags {
  title?: string;
  artist?: string;
  duration?: number;
}

/**
 * Reads ID3 / Vorbis / MP4 tags out of the file itself. The parser is a heavy dependency,
 * so it is imported lazily — the upload dialog is the only place that needs it.
 *
 * `music-metadata-browser` still reaches for the Node globals `Buffer` and `global`
 * (its mpeg parser allocates a Buffer in its constructor), so both are installed right
 * before the import. Doing it here rather than in `main.tsx` keeps the shim off the
 * critical path for everyone who never opens this dialog.
 */
async function readTags(file: File): Promise<FileTags> {
  try {
    const { Buffer } = await import("buffer");
    (globalThis as any).Buffer ??= Buffer;
    (globalThis as any).global ??= globalThis;
    const { parseBlob } = await import("music-metadata-browser");
    const { common, format } = await parseBlob(file, { skipCovers: true });
    return {
      title: common.title?.trim() || undefined,
      artist: (common.artist ?? common.albumartist)?.trim() || undefined,
      duration: format.duration && isFinite(format.duration) ? format.duration : undefined
    };
  } catch {
    // Untagged file, unsupported container, corrupt header — the filename fallback stands.
    return {};
  }
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
  const nextId = useRef(0);

  const updateItem = (id: number, patch: Partial<QueueItem>) =>
    setItems(prev => prev.map(it => it.id === id ? { ...it, ...patch } : it));

  const removeItem = (id: number) =>
    setItems(prev => prev.filter(it => it.id !== id));

  const applyTags = async (id: number, file: File) => {
    const tags = await readTags(file);
    setItems(prev => prev.map(it => {
      if (it.id !== id) return it;
      const keepFields = it.edited || it.status !== "pending";
      return {
        ...it,
        reading: false,
        title: keepFields ? it.title : (tags.title ?? it.title),
        artist: keepFields ? it.artist : (tags.artist ?? it.artist),
        duration: it.duration ?? tags.duration
      };
    }));
  };

  const addFiles = (files: FileList | File[] | null) => {
    if (!files) return;
    const arr: QueueItem[] = Array.from(files)
      .filter(f => f.type.startsWith("audio/"))
      .map(f => ({
        id: nextId.current++,
        file: f,
        title: defaultTitle(f.name),
        artist: "Unknown",
        edited: false,
        reading: true,
        status: "pending" as const
      }));
    if (!arr.length) return;
    setItems(prev => [...prev, ...arr]);
    arr.forEach(it => { void applyTags(it.id, it.file); });
  };

  const runUpload = async () => {
    if (items.length === 0) return;
    setBusy(true);
    let anySuccess = false;
    for (const it of items) {
      if (it.status === "done") continue;
      updateItem(it.id, { status: "uploading", message: undefined });
      try {
        const duration = it.duration ?? await readDuration(it.file);
        await uploadTrack(it.file, it.title.trim(), it.artist.trim(), isPublic, duration);
        updateItem(it.id, { status: "done" });
        anySuccess = true;
      } catch (e: any) {
        const msg = e?.message ?? "Ошибка";
        const isDup = /already have|already in your library/i.test(msg);
        updateItem(it.id, { status: isDup ? "duplicate" : "failed", message: msg });
      }
    }
    setBusy(false);
    if (anySuccess) onUploaded();
  };

  const allDone = items.length > 0 && items.every(i => i.status === "done");
  const reading = items.some(i => i.reading);
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
            {items.map(it => (
              <div key={it.id} className={`upload-item upload-${it.status}`}>
                <div className="upload-fields">
                  <input
                    className="input input-sm"
                    placeholder="Название"
                    value={it.title}
                    onChange={e => updateItem(it.id, { title: e.target.value, edited: true })}
                    disabled={it.status === "uploading" || it.status === "done"}
                  />
                  <input
                    className="input input-sm"
                    placeholder="Исполнитель"
                    value={it.artist}
                    onChange={e => updateItem(it.id, { artist: e.target.value, edited: true })}
                    disabled={it.status === "uploading" || it.status === "done"}
                  />
                </div>
                <div className="upload-status">
                  {it.reading && it.status === "pending" && <span className="muted">Читаем теги…</span>}
                  {!it.reading && it.status === "pending" && <span className="muted">Ожидает</span>}
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
                    onClick={() => removeItem(it.id)}
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
          <button type="submit" className="btn btn-primary" disabled={busy || reading || pendingCount === 0}>
            {busy ? "Загрузка…" : reading ? "Читаем теги…" : allDone ? "Все загружены" : `Загрузить (${pendingCount})`}
          </button>
        </div>
      </form>
    </div>
  );
}
