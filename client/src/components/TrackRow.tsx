import { useState } from "react";
import type { Track } from "../types";
import { usePlayer } from "../player/PlayerContext";
import { useTelegram } from "../telegram/TelegramContext";
import { sendTrackToTelegram } from "../api/telegram";
import { AddToCollectionMenu } from "./AddToCollectionMenu";
import { EditTrackModal } from "./EditTrackModal";

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

function TelegramIcon() {
  return (
    <svg className="icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M21.94 4.3 18.9 19.1c-.23 1.01-.83 1.26-1.68.79l-4.65-3.43-2.24 2.16c-.25.25-.46.46-.94.46l.33-4.74 8.63-7.8c.38-.33-.08-.52-.58-.19L6.11 13.06l-4.59-1.44c-1-.31-1.02-1 .21-1.48l17.95-6.92c.83-.3 1.56.2 1.26 1.08Z" />
    </svg>
  );
}

interface Props {
  track: Track;
  queue: Track[];
  onDelete?: (track: Track) => void;
  onUpdated?: (track: Track) => void;
  onRemoveFromCollection?: (track: Track) => void;
  onUnsave?: (track: Track) => void;
  onSave?: (track: Track) => void;
  isSaved?: boolean; // for public bank: marks tracks user already has
}

export function TrackRow({
  track, queue, onDelete, onUpdated, onRemoveFromCollection, onUnsave, onSave, isSaved
}: Props) {
  const { play, current } = usePlayer();
  const { linked: tgLinked } = useTelegram();
  const [menuOpen, setMenuOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [tgState, setTgState] = useState<"idle" | "sending" | "sent">("idle");
  const [tgError, setTgError] = useState<string | null>(null);
  const isCurrent = current?.id === track.id;

  const sendToTelegram = async () => {
    setTgError(null);
    setTgState("sending");
    try {
      await sendTrackToTelegram(track.id);
      setTgState("sent");
      setTimeout(() => setTgState("idle"), 2000);
    } catch (e: any) {
      setTgError(e.message);
      setTgState("idle");
    }
  };

  return (
    <div className={`track-row ${isCurrent ? "is-current" : ""}`}>
      <button
        className="track-play"
        onClick={() => play(track, queue)}
        title="Воспроизвести"
      >▶</button>
      <div className="track-main">
        <div className="track-title">
          {track.title}
          {track.isPublic && track.isOwned && <span className="badge badge-public">Public</span>}
          {!track.isOwned && <span className="badge badge-saved">Saved</span>}
        </div>
        <div className="track-artist">{track.artist}</div>
      </div>
      <div className="track-meta">{fmtDuration(track.duration)}</div>
      <div className="track-meta">{fmtSize(track.fileSize)}</div>
      <div className="track-actions">
        {tgLinked && (
          <button
            className={`btn-ghost btn-icon ${tgState === "sent" ? "is-sent" : ""}`}
            disabled={tgState === "sending"}
            title={tgError ?? "Отправить в Telegram"}
            onClick={sendToTelegram}
          >
            {tgState === "sent" ? "✓" : <TelegramIcon />}
          </button>
        )}
        {onSave && (
          isSaved
            ? <span className="muted small">В библиотеке</span>
            : <button className="btn-ghost" title="Сохранить в библиотеку"
                      onClick={() => onSave(track)}>＋ Сохранить</button>
        )}
        {onUpdated && track.isOwned && (
          <>
            <div className="menu-wrap">
              <button className="btn-ghost" onClick={() => setMenuOpen(v => !v)} title="Добавить в коллекцию">＋</button>
              {menuOpen && (
                <AddToCollectionMenu
                  trackId={track.id}
                  onDone={() => setMenuOpen(false)}
                />
              )}
            </div>
            <button className="btn-ghost" title="Редактировать"
                    onClick={() => setEditOpen(true)}>✎</button>
          </>
        )}
        {onUpdated && !track.isOwned && (
          <div className="menu-wrap">
            <button className="btn-ghost" onClick={() => setMenuOpen(v => !v)} title="Добавить в коллекцию">＋</button>
            {menuOpen && (
              <AddToCollectionMenu
                trackId={track.id}
                onDone={() => setMenuOpen(false)}
              />
            )}
          </div>
        )}
        {onRemoveFromCollection && (
          <button className="btn-ghost" title="Убрать из коллекции"
                  onClick={() => onRemoveFromCollection(track)}>−</button>
        )}
        {onUnsave && !track.isOwned && (
          <button className="btn-danger" title="Убрать из библиотеки"
                  onClick={() => onUnsave(track)}>✕</button>
        )}
        {onDelete && track.isOwned && (
          <button className="btn-danger" title="Удалить"
                  onClick={() => onDelete(track)}>✕</button>
        )}
      </div>

      {editOpen && (
        <EditTrackModal
          track={track}
          onClose={() => setEditOpen(false)}
          onSaved={t => { onUpdated?.(t); }}
        />
      )}
    </div>
  );
}
