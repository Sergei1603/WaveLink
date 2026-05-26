import { useEffect, useRef, useState } from "react";
import WaveSurfer from "wavesurfer.js";
import { usePlayer } from "../player/PlayerContext";
import { authHeader } from "../api/client";
import { streamUrl } from "../api/tracks";

function fmt(s: number) {
  if (!isFinite(s)) return "0:00";
  const m = Math.floor(s / 60);
  const r = Math.floor(s % 60);
  return `${m}:${r.toString().padStart(2, "0")}`;
}

export function PlayerBar() {
  const { current, next, prev, stop } = usePlayer();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const wsRef = useRef<WaveSurfer | null>(null);
  const blobRef = useRef<string | null>(null);

  const [playing, setPlaying] = useState(false);
  const [duration, setDuration] = useState(0);
  const [time, setTime] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [volume, setVolume] = useState(0.9);

  useEffect(() => {
    if (!current || !containerRef.current) return;

    let cancelled = false;
    setError(null); setLoading(true); setPlaying(false);

    wsRef.current?.destroy();
    if (blobRef.current) { URL.revokeObjectURL(blobRef.current); blobRef.current = null; }

    const ws = WaveSurfer.create({
      container: containerRef.current,
      height: 56,
      waveColor: "#3b3d4a",
      progressColor: "#7c7df0",
      cursorColor: "#aaa",
      barWidth: 2,
      barGap: 2,
      barRadius: 2,
      normalize: true
    });
    wsRef.current = ws;

    ws.on("ready", () => {
      if (cancelled) return;
      setDuration(ws.getDuration());
      setLoading(false);
      ws.setVolume(volume);
      ws.play().catch(() => { /* autoplay may block */ });
    });
    ws.on("play", () => setPlaying(true));
    ws.on("pause", () => setPlaying(false));
    ws.on("timeupdate", t => setTime(t));
    ws.on("finish", () => next());
    ws.on("error", e => { setError(String(e)); setLoading(false); });

    (async () => {
      try {
        const res = await fetch(streamUrl(current.id), { headers: authHeader() });
        if (!res.ok) throw new Error(`Failed to load audio (${res.status})`);
        const blob = await res.blob();
        if (cancelled) return;
        const url = URL.createObjectURL(blob);
        blobRef.current = url;
        await ws.load(url);
      } catch (e: any) {
        if (!cancelled) { setError(e?.message ?? "Failed to load"); setLoading(false); }
      }
    })();

    return () => {
      cancelled = true;
      ws.destroy();
      if (blobRef.current) { URL.revokeObjectURL(blobRef.current); blobRef.current = null; }
    };
  }, [current?.id]);

  useEffect(() => {
    wsRef.current?.setVolume(volume);
  }, [volume]);

  if (!current) return null;

  return (
    <div className="player-bar">
      <div className="player-meta">
        <div className="player-title">{current.title}</div>
        <div className="player-artist">{current.artist}</div>
      </div>

      <div className="player-controls">
        <button className="btn-ghost" onClick={prev} title="Previous">‹‹</button>
        <button
          className="btn-primary"
          onClick={() => wsRef.current?.playPause()}
          disabled={loading}
        >
          {loading ? "…" : playing ? "❚❚" : "▶"}
        </button>
        <button className="btn-ghost" onClick={next} title="Next">››</button>
      </div>

      <div className="player-wave-wrap">
        <span className="player-time">{fmt(time)}</span>
        <div ref={containerRef} className="player-wave" />
        <span className="player-time">{fmt(duration)}</span>
      </div>

      <div className="player-right">
        <input
          type="range" min={0} max={1} step={0.01}
          value={volume} onChange={e => setVolume(parseFloat(e.target.value))}
          title="Volume"
        />
        <button className="btn-ghost" onClick={stop} title="Close">✕</button>
      </div>

      {error && <div className="player-error">{error}</div>}
    </div>
  );
}
