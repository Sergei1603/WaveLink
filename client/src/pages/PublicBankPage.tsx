import { useCallback, useState } from "react";
import { listPublicTracks, saveTrack } from "../api/tracks";
import type { Track, TrackSort } from "../types";
import { TrackRow } from "../components/TrackRow";
import { LoadMore } from "../components/LoadMore";
import { SearchIcon } from "../components/Icons";
import { useAppShell } from "../app/AppShellContext";
import { usePagedTracks } from "../hooks/usePagedTracks";
import { useDebounced } from "../hooks/useDebounced";

const SORTS: { value: TrackSort; label: string }[] = [
  { value: "recent", label: "Недавние" },
  { value: "artist", label: "Исполнитель" },
  { value: "title", label: "Название" }
];

export function PublicBankPage() {
  const { bumpTracks } = useAppShell();
  // Only what was saved in this session: `track.isSaved` already covers everything else.
  const [justSaved, setJustSaved] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<TrackSort>("recent");
  const query = useDebounced(search.trim(), search ? 300 : 0);

  const fetchPage = useCallback(
    (page: number, limit: number) => listPublicTracks(page, limit, query, sort),
    [query, sort]);

  const { tracks, total, loading, loadingMore, err, hasMore, sentinelRef, loadMore } =
    usePagedTracks(fetchPage);

  const onSave = async (t: Track) => {
    try {
      await saveTrack(t.id);
      setJustSaved(prev => new Set(prev).add(t.id));
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
          {query ? "Ничего не найдено." : "Публичных треков пока нет."}
        </div>
      )}

      <div className="track-list">
        {tracks.map(t => (
          <TrackRow
            key={t.id}
            track={t}
            queue={tracks}
            onSave={onSave}
            isSaved={t.isOwned || t.isSaved || justSaved.has(t.id)}
          />
        ))}
      </div>

      {hasMore && (
        <LoadMore
          sentinelRef={sentinelRef}
          remaining={total - tracks.length}
          loading={loadingMore}
          onLoadMore={loadMore}
        />
      )}
    </div>
  );
}
