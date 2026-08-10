import { api } from "./client";
import type {
  ArtistStats, MyStats, PlayEventReport, ReportPlaysResponse, ShuffleMode, Track, TrackDetail
} from "../types";

export function reportPlays(events: PlayEventReport[]) {
  return api<ReportPlaysResponse>("/api/plays", { body: { events } });
}

export function getTrackDetail(id: string) {
  return api<TrackDetail>(`/api/tracks/${id}`);
}

export function getShuffle(mode: ShuffleMode, limit = 50, collectionId?: string) {
  const params = new URLSearchParams({ mode, limit: String(limit) });
  if (collectionId) params.set("collectionId", collectionId);
  return api<Track[]>(`/api/tracks/shuffle?${params}`);
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
