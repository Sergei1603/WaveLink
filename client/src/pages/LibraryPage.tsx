import { useCallback, useState } from "react";
import { deleteTrack, listTracks, unsaveTrack } from "../api/tracks";
import type { Track, TrackSort } from "../types";
import { TrackRow } from "../components/TrackRow";
import { ShuffleButtons } from "../components/ShuffleButtons";
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

function totalDuration(tracks: Track[]) {
  const sec = tracks.reduce((a, t) => a + (t.duration || 0), 0);
  const h = Math.floor(sec / 3600);
  const m = Math.round((sec % 3600) / 60);
  return h ? `${h} ч ${m} мин` : `${m} мин`;
}

export function LibraryPage() {
  const { tracksVersion } = useAppShell();
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<TrackSort>("recent");
  const query = useDebounced(search.trim(), search ? 300 : 0);

  const fetchPage = useCallback(
    (page: number, limit: number) => listTracks(page, limit, sort, query),
    [sort, query]);

  const {
    tracks, total, loading, loadingMore, err, hasMore, sentinelRef, loadMore, drop, replace
  } = usePagedTracks(fetchPage, tracksVersion);

  const onDelete = async (t: Track) => {
    if (!confirm(`Удалить «${t.title}»?`)) return;
    try { await deleteTrack(t.id); drop(t.id); }
    catch (e: any) { alert(e.message); }
  };

  const onUnsave = async (t: Track) => {
    if (!confirm(`Убрать «${t.title}» из библиотеки?`)) return;
    try { await unsaveTrack(t.id); drop(t.id); }
    catch (e: any) { alert(e.message); }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Библиотека</h1>
          <div className="page-sub">
            {total} треков{hasMore ? "" : ` · ${totalDuration(tracks)}`}
          </div>
        </div>
        <div className="page-actions">
          <ShuffleButtons />
          <div className="input-search search">
            <SearchIcon />
            <input
              className="input"
              placeholder="Поиск…"
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
          {query
            ? "Треки не найдены."
            : "Библиотека пуста. Загрузите трек или посмотрите Общий банк."}
        </div>
      )}

      <div className="track-list">
        {tracks.map((t, i) => (
          <TrackRow
            key={t.id}
            track={t}
            index={i + 1}
            queue={tracks}
            onDelete={onDelete}
            onUpdated={replace}
            onUnsave={onUnsave}
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
