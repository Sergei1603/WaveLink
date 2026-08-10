import { useEffect, useState } from "react";
import { getTrackDetail } from "../api/plays";
import type { TrackDetail } from "../types";

function fmtDate(iso: string | null) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("ru-RU", {
    day: "numeric", month: "long", year: "numeric", hour: "2-digit", minute: "2-digit"
  });
}

function fmtDuration(s: number) {
  if (!s) return "—";
  const m = Math.floor(s / 60);
  const r = Math.floor(s % 60);
  return `${m}:${r.toString().padStart(2, "0")}`;
}

function fmtListened(sec: number) {
  if (!sec) return "—";
  if (sec < 60) return `${Math.round(sec)} сек`;
  const h = Math.floor(sec / 3600);
  const m = Math.round((sec % 3600) / 60);
  return h ? `${h} ч ${m} мин` : `${m} мин`;
}

function fmtSize(b: number) {
  if (b < 1024 * 1024) return `${(b / 1024).toFixed(0)} КБ`;
  return `${(b / 1024 / 1024).toFixed(1)} МБ`;
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="detail-row">
      <span className="detail-label">{label}</span>
      <span className="detail-value">{value}</span>
    </div>
  );
}

export function TrackDetailModal({ trackId, onClose }: { trackId: string; onClose: () => void }) {
  const [detail, setDetail] = useState<TrackDetail | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const d = await getTrackDetail(trackId);
        if (!cancelled) setDetail(d);
      } catch (e: any) {
        if (!cancelled) setErr(e?.message ?? "Не удалось загрузить карточку");
      }
    })();
    return () => { cancelled = true; };
  }, [trackId]);

  const t = detail?.track;
  const s = detail?.stats;

  return (
    <div className="dialog-backdrop" onMouseDown={onClose}>
      <div className="dialog" onMouseDown={e => e.stopPropagation()}>
        <div className="dialog-title">{t?.title ?? "Карточка трека"}</div>

        {err && <div className="error">{err}</div>}
        {!detail && !err && <div className="muted">Загрузка…</div>}

        {t && s && (
          <>
            <div className="detail-list">
              <Row label="Исполнитель" value={t.artist} />
              <Row label="Кто добавил" value={`@${t.uploaderUsername}`} />
              <Row label="Когда добавлен" value={fmtDate(t.uploadedAt)} />
              <Row label="Длительность" value={fmtDuration(t.duration)} />
              <Row label="Размер" value={fmtSize(t.fileSize)} />
            </div>

            <hr className="hr" />
            <h6 className="section-label">Прослушивания</h6>

            <div className="detail-list">
              <Row label="Всего" value={s.totalPlays} />
              <Row label="Разных слушателей" value={s.distinctListeners} />
              <Row label="Твоих" value={s.myPlays} />
              <Row label="Ты слушал" value={fmtListened(s.myListenedSeconds)} />
              <Row label="Последний раз" value={fmtDate(s.myLastPlayedAt)} />
              <Row
                label="Дослушан до конца"
                value={s.myCompleted ? "да" : "нет"}
              />
            </div>

            <div className="muted small">
              Прослушивание засчитывается с 60% трека или 2 минут — перемотка не считается.
            </div>
          </>
        )}

        <div className="dialog-actions">
          <button type="button" className="btn btn-secondary" onClick={onClose}>Закрыть</button>
        </div>
      </div>
    </div>
  );
}
