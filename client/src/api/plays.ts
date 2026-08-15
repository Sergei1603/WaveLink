import { api } from "./client";
import type {
  ArtistStats, MyStats, PlayEventReport, ReportPlaysResponse, ShuffleMode, ShufflePage, TrackDetail
} from "../types";

export function reportPlays(events: PlayEventReport[]) {
  return api<ReportPlaysResponse>("/api/plays", { body: { events } });
}

export function getTrackDetail(id: string) {
  return api<TrackDetail>(`/api/tracks/${id}`);
}

export interface ShuffleQuery {
  limit?: number;
  collectionId?: string;
  /** Omit to start a new cycle; pass the seed of the running one to page through it. */
  seed?: number;
  cursor?: number;
}

export function getShuffle(mode: ShuffleMode, opts: ShuffleQuery = {}) {
  const params = new URLSearchParams({ mode, limit: String(opts.limit ?? 50) });
  if (opts.collectionId) params.set("collectionId", opts.collectionId);
  if (opts.seed !== undefined) params.set("seed", String(opts.seed));
  if (opts.cursor) params.set("cursor", String(opts.cursor));
  return api<ShufflePage>(`/api/tracks/shuffle?${params}`);
}

export function getMyStats(from?: string, to?: string, limit = 10) {
  const params = new URLSearchParams({ limit: String(limit) });
  if (from) params.set("from", from);
  if (to) params.set("to", to);
  return api<MyStats>(`/api/stats/me?${params}`);
}

export function getArtistStats(name: string) {
  return api<ArtistStats>(`/api/stats/artist?name=${encodeURIComponent(name)}`);
}
