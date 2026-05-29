import { api, apiUrl } from "./client";
import type { PagedTracks, Track, TrackSort } from "../types";

export function listTracks(page = 1, limit = 200, sort: TrackSort = "recent") {
  return api<PagedTracks>(`/api/tracks?page=${page}&limit=${limit}&sort=${sort}`);
}

export function listPublicTracks(page = 1, limit = 200, search = "", sort: TrackSort = "recent") {
  const params = new URLSearchParams({ page: String(page), limit: String(limit), sort });
  if (search) params.set("search", search);
  return api<PagedTracks>(`/api/tracks/public?${params}`);
}

export function uploadTrack(file: File, title: string, artist: string, isPublic: boolean, duration?: number) {
  const fd = new FormData();
  fd.append("file", file);
  fd.append("title", title);
  fd.append("artist", artist);
  fd.append("isPublic", String(isPublic));
  if (duration !== undefined) fd.append("duration", String(Math.round(duration)));
  return api<Track>("/api/tracks/upload", { formData: fd });
}

export function updateTrack(id: string, patch: { title?: string; artist?: string; isPublic?: boolean }) {
  return api<Track>(`/api/tracks/${id}`, { method: "PATCH", body: patch });
}

export function saveTrack(id: string) {
  return api<void>(`/api/tracks/${id}/save`, { method: "POST" });
}

export function unsaveTrack(id: string) {
  return api<void>(`/api/tracks/${id}/save`, { method: "DELETE" });
}

export function deleteTrack(id: string) {
  return api<void>(`/api/tracks/${id}`, { method: "DELETE" });
}

export function streamUrl(id: string) {
  return apiUrl(`/api/tracks/${id}/stream`);
}
