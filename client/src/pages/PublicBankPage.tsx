import { useEffect, useState } from "react";
import { listPublicTracks, listTracks, saveTrack } from "../api/tracks";
import type { Track, TrackSort } from "../types";
import { TrackRow } from "../components/TrackRow";
import { SearchIcon } from "../components/Icons";
import { useAppShell } from "../app/AppShellContext";

const SORTS: { value: TrackSort; label: string }[] = [
  { value: "recent", label: "Недавние" },
  { value: "artist", label: "Исполнитель" },
  { value: "title", label: "Название" }
];

export function PublicBankPage() {
  const { bumpTracks } = useAppShell();
  const [tracks, setTracks] = useState<Track[]>([]);
  const [myIds, setMyIds] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<TrackSort>("recent");

  useEffect(() => {
    let cancelled = false;
    const timer = setTimeout(async () => {
      setLoading(true); setErr(null);
      try {
        const [pub, mine] = await Promise.all([
          listPublicTracks(1, 200, search, sort),
          listTracks(1, 200, "recent")
        ]);
        if (cancelled) return;
        setTracks(pub.items);
        setMyIds(new Set(mine.items.map(t => t.id)));
      } catch (e: any) {
        if (!cancelled) setErr(e.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }, search ? 300 : 0);

    return () => { cancelled = true; clearTimeout(timer); };
  }, [sort, search]);

  const onSave = async (t: Track) => {
    try {
      await saveTrack(t.id);
      setMyIds(prev => new Set(prev).add(t.id));
      bumpTracks();
    } catch (e: any) { alert(e.message); }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Общий банк</h1>
          <div className="page-sub">
            Треки, опубликованные другими пользователями. Сохранённое попадает в вашу библиотеку.
          </div>
        </div>
        <div className="page-actions">
          <div className="input-search" style={{ width: 240 }}>
            <SearchIcon />
            <input
              className="input"
              placeholder="Название или исполнитель"
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
          </div>
          <div className="seg">
            {SORTS.map(s => (
              <button
                key={s.value}
                type="button"
                className={`seg-opt ${sort === s.value ? "is-active" : ""}`}
                onClick={() => setSort(s.value)}
              >{s.label}</button>
            ))}
          </div>
        </div>
      </div>

      {err && <div className="error">{err}</div>}
      {loading && <div className="muted">Загрузка…</div>}
      {!loading && tracks.length === 0 && (
        <div className="empty">
          {search ? "Ничего не найдено." : "Публичных треков пока нет."}
        </div>
      )}

      <div className="track-list">
        {tracks.map(t => (
          <TrackRow
            key={t.id}
            track={t}
            queue={tracks}
            onSave={onSave}
            isSaved={myIds.has(t.id)}
          />
        ))}
      </div>
    </div>
  );
}
