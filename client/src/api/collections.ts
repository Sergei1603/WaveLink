import { api } from "./client";
import type { CollectionDetail, CollectionSummary } from "../types";

export const listCollections = () =>
  api<CollectionSummary[]>("/api/collections");

export const createCollection = (name: string) =>
  api<CollectionSummary>("/api/collections", { body: { name } });

export const getCollection = (id: string) =>
  api<CollectionDetail>(`/api/collections/${id}`);

export const addTrackToCollection = (collectionId: string, trackId: string) =>
  api<void>(`/api/collections/${collectionId}/tracks`, { body: { trackId } });

export const removeTrackFromCollection = (collectionId: string, trackId: string) =>
  api<void>(`/api/collections/${collectionId}/tracks/${trackId}`, { method: "DELETE" });

export const deleteCollection = (id: string) =>
  api<void>(`/api/collections/${id}`, { method: "DELETE" });
