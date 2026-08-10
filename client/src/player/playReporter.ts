import { apiUrl, getStoredTokens } from "../api/client";
import { reportPlays } from "../api/plays";
import type { PlayEventReport } from "../types";

const QUEUE_KEY = "wavelink.pendingPlays";
/** The server caps a batch at PlayStats:MaxBatchSize. */
const MAX_BATCH = 200;
/** Guard against unbounded localStorage growth if the API stays unreachable. */
const MAX_QUEUE = 500;

function readQueue(): PlayEventReport[] {
  try {
    const raw = localStorage.getItem(QUEUE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed as PlayEventReport[] : [];
  } catch {
    return [];
  }
}

function writeQueue(queue: PlayEventReport[]): void {
  try {
    if (queue.length) localStorage.setItem(QUEUE_KEY, JSON.stringify(queue.slice(-MAX_QUEUE)));
    else localStorage.removeItem(QUEUE_KEY);
  } catch { /* quota or private mode — losing a play event is not worth breaking playback */ }
}

export function enqueuePlay(event: PlayEventReport): void {
  writeQueue([...readQueue(), event]);
}

let flushing = false;

/**
 * Drains the offline queue. Replaying is always safe — the server dedups on `clientEventId` —
 * so on any failure the queue is left intact for the next attempt.
 */
export async function flushPlays(): Promise<void> {
  if (flushing) return;
  if (!getStoredTokens()) return;
  if (readQueue().length === 0) return;

  flushing = true;
  try {
    const batch = readQueue().slice(0, MAX_BATCH);
    const res = await reportPlays(batch);
    // accepted / duplicate / rejected are all terminal: drop everything the server answered for.
    const settled = new Set(res.results.map(r => r.clientEventId));
    writeQueue(readQueue().filter(e => !settled.has(e.clientEventId)));
  } catch {
    /* keep the queue */
  } finally {
    flushing = false;
  }
}

/**
 * Reports one session immediately. Uses `fetch` with `keepalive` rather than
 * `navigator.sendBeacon` — beacon cannot set an `Authorization` header — so the request survives
 * the page being closed. Anything that fails (including an expired access token) falls into the
 * queue, where `flushPlays` retries it through the normal refresh-aware client.
 *
 * Resolves to true when the server accepted the report.
 */
export async function reportPlayNow(event: PlayEventReport): Promise<boolean> {
  const tokens = getStoredTokens();
  if (!tokens) return false;

  try {
    const res = await fetch(apiUrl("/api/plays"), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${tokens.accessToken}`
      },
      body: JSON.stringify({ events: [event] }),
      keepalive: true
    });
    if (!res.ok) { enqueuePlay(event); return false; }
    return true;
  } catch {
    enqueuePlay(event);
    return false;
  }
}
