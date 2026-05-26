import { api } from "./client";
import type { PagedTracks, Track } from "../types";

export function listTracks(page = 1, limit = 50) {
  return api<PagedTracks>(`/api/tracks?page=${page}&limit=${limit}`);
}

export function uploadTrack(file: File, title: string, artist: string, duration?: number) {
  const fd = new FormData();
  fd.append("file", file);
  fd.append("title", title);
  fd.append("artist", artist);
  if (duration !== undefined) fd.append("duration", String(Math.round(duration)));
  return api<Track>("/api/tracks/upload", { formData: fd });
}

export function deleteTrack(id: string) {
  return api<void>(`/api/tracks/${id}`, { method: "DELETE" });
}

export function streamUrl(id: string) {
  return `/api/tracks/${id}/stream`;
}
