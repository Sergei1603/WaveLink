import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import WaveSurfer from "wavesurfer.js";
import { usePlayer } from "../player/PlayerContext";
import { useAppShell } from "../app/AppShellContext";
import { authHeader } from "../api/client";
import { streamUrl } from "../api/tracks";
import { CoverageSet } from "../player/listeningTracker";
import { flushPlays, reportPlayNow } from "../player/playReporter";
import { QueuePanel } from "./QueuePanel";
import { VolumeIcon } from "./Icons";

function fmt(s: number) {
  if (!isFinite(s) || s < 0) return "0:00";
  const m = Math.floor(s / 60);
  const r = Math.floor(s % 60);
  return `${m}:${r.toString().padStart(2, "0")}`;
}

/** Right-hand column: the player plus the collections shortcut list. */
/** Below this the server rejects the report anyway (PlayStats:MinReportedSeconds). */
const MIN_REPORTABLE_SECONDS = 5;

export function NowPlaying() {
  const { current, next, prev, stop } = usePlayer();
  const { collections, refreshCollections, bumpTracks } = useAppShell();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const wsRef = useRef<WaveSurfer | null>(null);
  const blobRef = useRef<string | null>(null);

  // Listening measurement for the current track.
  const coverageRef = useRef(new CoverageSet());
  const lastTimeRef = useRef(0);
  const startedAtRef = useRef("");
  const durationRef = useRef(0);
  const reportedRef = useRef(false);
  /** Lets the page-close listener reach the current track's emit without re-subscribing. */
  const emitRef = useRef<() => void>(() => {});

  const [playing, setPlaying] = useState(false);
  const [duration, setDuration] = useState(0);
  const [time, setTime] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [volume, setVolume] = useState(0.9);

  useEffect(() => { void refreshCollections(); }, [refreshCollections]);

  // Anything stranded by an earlier failure or an offline session goes out on load.
  useEffect(() => { void flushPlays(); }, []);

  // Closing or backgrounding the tab is the one path the effect cleanup below does not cover.
  useEffect(() => {
    const onLeave = () => emitRef.current();
    const onVisibility = () => { if (document.visibilityState === "hidden") onLeave(); };
    window.addEventListener("pagehide", onLeave);
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      window.removeEventListener("pagehide", onLeave);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, []);

  useEffect(() => {
    if (!current || !containerRef.current) return;

    let cancelled = false;
    setError(null); setLoading(true); setPlaying(false); setTime(0); setDuration(0);

    if (blobRef.current) { URL.revokeObjectURL(blobRef.current); blobRef.current = null; }

    const trackId = current.id;
    coverageRef.current.reset();
    lastTimeRef.current = 0;
    durationRef.current = current.duration;
    startedAtRef.current = new Date().toISOString();
    reportedRef.current = false;

    /** Reports the finished session exactly once per loaded track. */
    const emit = () => {
      if (reportedRef.current) return;
      const listened = coverageRef.current.seconds;
      if (listened < MIN_REPORTABLE_SECONDS) return;
      reportedRef.current = true;
      void reportPlayNow({
        clientEventId: crypto.randomUUID(),
        trackId,
        startedAt: startedAtRef.current,
        listenedSeconds: listened,
        trackDuration: durationRef.current > 0 ? Math.round(durationRef.current) : undefined,
        source: "web"
      }).then(ok => { if (ok) bumpTracks(); });
    };
    emitRef.current = emit;

    const styles = getComputedStyle(document.documentElement);
    const token = (name: string, fallback: string) =>
      styles.getPropertyValue(name).trim() || fallback;

    const ws = WaveSurfer.create({
      container: containerRef.current,
      height: 96,
      waveColor: token("--color-neutral-800", "#3f424d"),
      progressColor: token("--color-accent", "#9184d9"),
      cursorColor: token("--color-accent-200", "#e7e5fe"),
      cursorWidth: 2,
      barWidth: 3,
      barGap: 2,
      barRadius: 2,
      normalize: true
    });
    wsRef.current = ws;

    ws.on("ready", () => {
      if (cancelled) return;
      const d = ws.getDuration();
      setDuration(d);
      if (d > 0) durationRef.current = d;   // more trustworthy than the stored metadata
      setLoading(false);
      ws.setVolume(volume);
      ws.play().catch(() => { /* autoplay may block */ });
    });
    ws.on("play", () => setPlaying(true));
    ws.on("pause", () => setPlaying(false));
    ws.on("timeupdate", t => {
      setTime(t);
      // Only credit normal forward progress. A jump (seek, or the gap after a stall) resyncs
      // the cursor without adding an interval, so skipped audio is never counted as heard.
      const delta = t - lastTimeRef.current;
      if (delta > 0 && delta < 1.5) coverageRef.current.add(lastTimeRef.current, t);
      lastTimeRef.current = t;
    });
    ws.on("seeking", (t: number) => { lastTimeRef.current = t; });
    ws.on("finish", () => { emit(); next(); });
    ws.on("error", e => { setError(String(e)); setLoading(false); });

    (async () => {
      try {
        const res = await fetch(streamUrl(current.id), { headers: authHeader() });
        if (!res.ok) throw new Error(`Ошибка загрузки аудио (${res.status})`);
        const blob = await res.blob();
        if (cancelled) return;
        const url = URL.createObjectURL(blob);
        blobRef.current = url;
        await ws.load(url);
      } catch (e: any) {
        if (!cancelled) { setError(e?.message ?? "Не удалось загрузить трек"); setLoading(false); }
      }
    })();

    return () => {
      cancelled = true;
      emit();                 // covers both switching tracks and unmounting the player
      emitRef.current = () => {};
      ws.destroy();
      wsRef.current = null;
      if (blobRef.current) { URL.revokeObjectURL(blobRef.current); blobRef.current = null; }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [current?.id]);

  useEffect(() => { wsRef.current?.setVolume(volume); }, [volume]);

  const remaining = duration ? duration - time : 0;

  return (
    <aside className="now-panel">
      <h6 className="section-label">Сейчас играет</h6>

      {current ? (
        <>
          <div>
            <div className="now-title">{current.title}</div>
            <div className="now-artist">{current.artist}</div>
          </div>

          <div className="now-wave">
            <div ref={containerRef} />
            {loading && <div className="now-wave-empty">Загрузка…</div>}
          </div>

          <div className="now-times">
            <span className="elapsed">{fmt(time)}</span>
            <span className="remaining">−{fmt(remaining)}</span>
            <span>{fmt(duration)}</span>
          </div>

          {error && <div className="error">{error}</div>}

          <div className="now-controls">
            <button className="btn btn-ghost btn-icon" onClick={prev} title="Предыдущий">‹‹</button>
            <button
              className="btn btn-primary btn-icon btn-round btn-lg"
              onClick={() => wsRef.current?.playPause()}
              disabled={loading}
              title={playing ? "Пауза" : "Играть"}
            >
              {loading ? "…" : playing ? "❚❚" : "▶"}
            </button>
            <button className="btn btn-ghost btn-icon" onClick={next} title="Следующий">››</button>
          </div>

          <div className="now-volume">
            <VolumeIcon />
            <input
              className="range"
              type="range" min={0} max={1} step={0.01}
              value={volume}
              onChange={e => setVolume(parseFloat(e.target.value))}
              title="Громкость"
            />
            <button className="btn btn-danger btn-icon" onClick={stop} title="Закрыть">✕</button>
          </div>

          <QueuePanel />
        </>
      ) : (
        <div className="now-idle">
          Ничего не играет.<br />
          Выберите трек в библиотеке — волна появится здесь.
        </div>
      )}

      <hr className="hr" style={{ margin: "4px 0" }} />

      <div className="now-collections">
        <h6 className="section-label">Коллекции</h6>
        {collections.length === 0 ? (
          <span className="muted small">Коллекций ещё нет.</span>
        ) : (
          collections.map(c => (
            <Link key={c.id} to={`/collections/${c.id}`}>
              <span>{c.name}</span>
              <span className="count">{c.trackCount}</span>
            </Link>
          ))
        )}
      </div>
    </aside>
  );
}
