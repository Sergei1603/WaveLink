import { useState } from "react";
import type { Track } from "../types";
import { usePlayer } from "../player/PlayerContext";
import { AddToCollectionMenu } from "./AddToCollectionMenu";

function fmtDuration(s: number) {
  if (!s) return "—";
  const m = Math.floor(s / 60);
  const r = Math.floor(s % 60);
  return `${m}:${r.toString().padStart(2, "0")}`;
}

function fmtSize(b: number) {
  if (b < 1024 * 1024) return `${(b / 1024).toFixed(0)} КБ`;
  return `${(b / 1024 / 1024).toFixed(1)} МБ`;
}

interface Props {
  track: Track;
  queue: Track[];
  onDelete?: (track: Track) => void;
  onRemoveFromCollection?: (track: Track) => void;
}

export function TrackRow({ track, queue, onDelete, onRemoveFromCollection }: Props) {
  const { play, current } = usePlayer();
  const [menuOpen, setMenuOpen] = useState(false);
  const isCurrent = current?.id === track.id;

  return (
    <div className={`track-row ${isCurrent ? "is-current" : ""}`}>
      <button
        className="track-play"
        onClick={() => play(track, queue)}
        title="Воспроизвести"
      >▶</button>
      <div className="track-main">
        <div className="track-title">{track.title}</div>
        <div className="track-artist">{track.artist}</div>
      </div>
      <div className="track-meta">{fmtDuration(track.duration)}</div>
      <div className="track-meta">{fmtSize(track.fileSize)}</div>
      <div className="track-actions">
        <div className="menu-wrap">
          <button className="btn-ghost" onClick={() => setMenuOpen(v => !v)} title="Добавить в коллекцию">＋</button>
          {menuOpen && (
            <AddToCollectionMenu
              trackId={track.id}
              onDone={() => setMenuOpen(false)}
            />
          )}
        </div>
        {onRemoveFromCollection && (
          <button className="btn-ghost" title="Убрать из коллекции"
                  onClick={() => onRemoveFromCollection(track)}>−</button>
        )}
        {onDelete && (
          <button className="btn-danger" title="Удалить"
                  onClick={() => onDelete(track)}>✕</button>
        )}
      </div>
    </div>
  );
}
