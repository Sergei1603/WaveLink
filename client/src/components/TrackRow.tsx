import { useState } from "react";
import type { Track } from "../types";
import { usePlayer } from "../player/PlayerContext";
import { useTelegram } from "../telegram/TelegramContext";
import { sendTrackToTelegram } from "../api/telegram";
import { AddToCollectionMenu } from "./AddToCollectionMenu";
import { EditTrackModal } from "./EditTrackModal";
import { TelegramIcon } from "./Icons";

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
  /** 1-based position, shown in the index column and swapped for ▶ on hover. */
  index?: number;
  onDelete?: (track: Track) => void;
  onUpdated?: (track: Track) => void;
  onRemoveFromCollection?: (track: Track) => void;
  onUnsave?: (track: Track) => void;
  onSave?: (track: Track) => void;
  isSaved?: boolean; // for public bank: marks tracks user already has
}

export function TrackRow({
  track, queue, index, onDelete, onUpdated, onRemoveFromCollection, onUnsave, onSave, isSaved
}: Props) {
  const { play, current } = usePlayer();
  const { linked: tgLinked } = useTelegram();
  const [menuOpen, setMenuOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [tgState, setTgState] = useState<"idle" | "sending" | "sent">("idle");
  const [tgError, setTgError] = useState<string | null>(null);
  const isCurrent = current?.id === track.id;
  const isPublicBank = !!onSave;

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

  const tags = (
    <div className="track-tags">
      {track.isPublic && track.isOwned && <span className="tag tag-accent">Public</span>}
      {!track.isOwned && <span className="tag tag-neutral">Saved</span>}
    </div>
  );

  return (
    <div
      className={`track-row ${isPublicBank ? "track-row-public" : ""} ${isCurrent ? "is-current" : ""}`}
      onDoubleClick={() => play(track, queue)}
    >
      {!isPublicBank && (
        <button
          className="track-index"
          onClick={() => play(track, queue)}
          title="Воспроизвести"
        >
          <span className="glyph-num">{String(index ?? 0).padStart(2, "0")}</span>
          <span className="glyph-play">▶</span>
        </button>
      )}

      <div className="track-main">
        <div className="track-title">{track.title}</div>
        <div className="track-sub">
          {track.artist}
          {isPublicBank ? "" : ` · ${fmtSize(track.fileSize)}`}
        </div>
      </div>

      {!isPublicBank && tags}

      <span className="track-dur">{fmtDuration(track.duration)}</span>

      <div className="track-actions">
        {isPublicBank ? (
          isSaved
            ? <span className="muted small">В библиотеке</span>
            : <button className="btn btn-secondary btn-sm" onClick={() => onSave!(track)}>
                Сохранить
              </button>
        ) : (
          <>
            {tgLinked && (
              <button
                className={`btn btn-ghost btn-icon ${tgState === "sent" ? "is-sent" : ""}`}
                disabled={tgState === "sending"}
                title={tgError ?? "Отправить в Telegram"}
                onClick={sendToTelegram}
              >
                {tgState === "sent" ? "✓" : <TelegramIcon />}
              </button>
            )}
            {onUpdated && (
              <div className="menu-wrap">
                <button
                  className="btn btn-ghost btn-icon"
                  onClick={() => setMenuOpen(v => !v)}
                  title="Добавить в коллекцию"
                >＋</button>
                {menuOpen && (
                  <AddToCollectionMenu trackId={track.id} onDone={() => setMenuOpen(false)} />
                )}
              </div>
            )}
            {onUpdated && track.isOwned && (
              <button className="btn btn-ghost btn-icon" title="Редактировать"
                      onClick={() => setEditOpen(true)}>✎</button>
            )}
            {onRemoveFromCollection && (
              <button className="btn btn-ghost btn-icon" title="Убрать из коллекции"
                      onClick={() => onRemoveFromCollection(track)}>−</button>
            )}
            {onUnsave && !track.isOwned && (
              <button className="btn btn-danger btn-icon" title="Убрать из библиотеки"
                      onClick={() => onUnsave(track)}>✕</button>
            )}
            {onDelete && track.isOwned && (
              <button className="btn btn-danger btn-icon" title="Удалить"
                      onClick={() => onDelete(track)}>✕</button>
            )}
          </>
        )}
      </div>

      {editOpen && (
        <EditTrackModal
          track={track}
          onClose={() => setEditOpen(false)}
          onSaved={t => onUpdated?.(t)}
        />
      )}
    </div>
  );
}
