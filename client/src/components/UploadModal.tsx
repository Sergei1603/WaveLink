import { useState } from "react";
import { uploadTrack } from "../api/tracks";

interface Props {
  onClose: () => void;
  onUploaded: () => void;
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

export function UploadModal({ onClose, onUploaded }: Props) {
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [artist, setArtist] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onFile = async (f: File | null) => {
    setFile(f); setErr(null);
    if (!f) return;
    const base = f.name.replace(/\.[^.]+$/, "");
    if (!title) setTitle(base);
    if (!artist) setArtist("Unknown");
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) { setErr("Pick a file"); return; }
    setErr(null); setBusy(true);
    try {
      const duration = await readDuration(file);
      await uploadTrack(file, title.trim(), artist.trim(), duration);
      onUploaded();
      onClose();
    } catch (e: any) {
      setErr(e?.message ?? "Upload failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <form className="modal" onMouseDown={e => e.stopPropagation()} onSubmit={submit}>
        <h2>Upload track</h2>
        <label className="filepicker">
          <input
            type="file"
            accept="audio/*"
            onChange={e => onFile(e.target.files?.[0] ?? null)}
          />
          <span>{file ? file.name : "Choose an audio file…"}</span>
        </label>
        <label>
          Title
          <input value={title} onChange={e => setTitle(e.target.value)} required />
        </label>
        <label>
          Artist
          <input value={artist} onChange={e => setArtist(e.target.value)} required />
        </label>
        {err && <div className="error">{err}</div>}
        <div className="modal-actions">
          <button type="button" className="btn-ghost" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn-primary" disabled={busy}>
            {busy ? "Uploading…" : "Upload"}
          </button>
        </div>
      </form>
    </div>
  );
}
