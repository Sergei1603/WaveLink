import { useState } from "react";
import { getShuffle } from "../api/plays";
import { usePlayer } from "../player/PlayerContext";
import type { ShuffleMode } from "../types";

/**
 * «Перемешать» is a uniform shuffle; «Открыть новое» weights tracks the user has played least,
 * so the library's forgotten corners come up first.
 */
export function ShuffleButtons({ collectionId }: { collectionId?: string }) {
  const { play } = usePlayer();
  const [busy, setBusy] = useState<ShuffleMode | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const start = async (mode: ShuffleMode) => {
    setErr(null); setBusy(mode);
    try {
      const queue = await getShuffle(mode, 50, collectionId);
      if (queue.length === 0) { setErr("Нечего перемешивать"); return; }
      play(queue[0], queue);
    } catch (e: any) {
      setErr(e?.message ?? "Не удалось перемешать");
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
      <button
        className="btn btn-secondary"
        disabled={busy !== null}
        onClick={() => start("random")}
        title="Случайный порядок"
      >{busy === "random" ? "…" : "Перемешать"}</button>

      <button
        className="btn btn-secondary"
        disabled={busy !== null}
        onClick={() => start("discover")}
        title="Чаще выпадает то, что ты слушал меньше всего"
      >{busy === "discover" ? "…" : "Открыть новое"}</button>

      {err && <span className="error small">{err}</span>}
    </>
  );
}
