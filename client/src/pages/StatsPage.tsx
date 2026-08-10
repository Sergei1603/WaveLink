import { useEffect, useState } from "react";
import { getMyStats } from "../api/plays";
import type { MyStats } from "../types";

const PERIODS = [
  { value: 0, label: "Всё время" },
  { value: 30, label: "30 дней" },
  { value: 7, label: "7 дней" }
];

function fmtListened(sec: number) {
  if (!sec) return "0 мин";
  if (sec < 60) return `${Math.round(sec)} сек`;
  const h = Math.floor(sec / 3600);
  const m = Math.round((sec % 3600) / 60);
  return h ? `${h} ч ${m} мин` : `${m} мин`;
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="stat-tile">
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
    </div>
  );
}

export function StatsPage() {
  const [days, setDays] = useState(0);
  const [stats, setStats] = useState<MyStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true); setErr(null);
      try {
        const from = days
          ? new Date(Date.now() - days * 86400_000).toISOString()
          : undefined;
        const res = await getMyStats(from, undefined, 20);
        if (!cancelled) setStats(res);
      } catch (e: any) {
        if (!cancelled) setErr(e?.message ?? "Не удалось загрузить статистику");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [days]);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Статистика</h1>
          <div className="page-sub">Что ты слушал и сколько</div>
        </div>
        <div className="page-actions">
          <div className="seg">
            {PERIODS.map(p => (
              <button
                key={p.value}
                type="button"
                className={`seg-opt ${days === p.value ? "is-active" : ""}`}
                onClick={() => setDays(p.value)}
              >{p.label}</button>
            ))}
          </div>
        </div>
      </div>

      {err && <div className="error">{err}</div>}
      {loading && <div className="muted">Загрузка…</div>}

      {stats && !loading && (
        <>
          <div className="stat-row">
            <Stat label="прослушиваний" value={stats.totalPlays} />
            <Stat label="разных треков" value={stats.distinctTracks} />
            <Stat label="дослушано до конца" value={stats.completedPlays} />
            <Stat label="времени прослушано" value={fmtListened(stats.totalListenedSeconds)} />
          </div>

          {stats.totalPlays === 0 ? (
            <div className="empty">
              Пока нечего показать — статистика появится после первых прослушиваний.
            </div>
          ) : (
            <div className="stat-columns">
              <section>
                <h6 className="section-label">Топ треков</h6>
                <div className="chart-list">
                  {stats.topTracks.map((t, i) => (
                    <div className="chart-item" key={t.trackId}>
                      <span className="chart-rank">{i + 1}</span>
                      <div className="chart-main">
                        <div className="chart-title">{t.title}</div>
                        <div className="chart-sub">{t.artist}</div>
                      </div>
                      <span className="chart-count">{t.plays}</span>
                    </div>
                  ))}
                </div>
              </section>

              <section>
                <h6 className="section-label">Топ исполнителей</h6>
                <div className="chart-list">
                  {stats.topArtists.map((a, i) => (
                    <div className="chart-item" key={a.artist}>
                      <span className="chart-rank">{i + 1}</span>
                      <div className="chart-main">
                        <div className="chart-title">{a.artist}</div>
                        <div className="chart-sub">
                          {a.trackCount} трек(ов) · {fmtListened(a.listenedSeconds)}
                        </div>
                      </div>
                      <span className="chart-count">{a.plays}</span>
                    </div>
                  ))}
                </div>
              </section>
            </div>
          )}
        </>
      )}
    </div>
  );
}
