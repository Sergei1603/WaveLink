export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
}

export interface Track {
  id: string;
  title: string;
  artist: string;
  duration: number;
  fileSize: number;
  mimeType: string;
  uploadedAt: string;
  isPublic: boolean;
  isOwned: boolean;
}

export type TrackSort = "recent" | "artist" | "title";

export interface PagedTracks {
  items: Track[];
  page: number;
  limit: number;
  total: number;
}

export interface CollectionSummary {
  id: string;
  name: string;
  trackCount: number;
  createdAt: string;
}

export interface CollectionDetail {
  id: string;
  name: string;
  createdAt: string;
  tracks: Track[];
}

export interface TelegramLinkToken {
  token: string;
  expiresAt: string;
}

export interface ApiErrorResponse {
  error: string;
  statusCode: number;
}
