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
  /** Saved from the public bank into the current user's library. */
  isSaved: boolean;
  uploaderId: string;
  uploaderUsername: string;
  /** Significant listens by the current user. */
  myPlays: number;
  myLastPlayedAt: string | null;
  myCompleted: boolean;
}

export interface TrackStats {
  trackId: string;
  totalPlays: number;
  distinctListeners: number;
  myPlays: number;
  myLastPlayedAt: string | null;
  myCompleted: boolean;
  myListenedSeconds: number;
}

export interface TrackDetail {
  track: Track;
  stats: TrackStats;
}

export type ShuffleMode = "random" | "discover";

/**
 * One page of a shuffled cycle. `seed` pins the order — hand it back with `nextCursor` to get
 * the next page; once `hasMore` is false the cycle is spent and a seedless request starts a new one.
 */
export interface ShufflePage {
  items: Track[];
  seed: number;
  nextCursor: number;
  hasMore: boolean;
  total: number;
}

export interface TopTrackItem {
  trackId: string;
  title: string;
  artist: string;
  plays: number;
  listenedSeconds: number;
  lastPlayedAt: string;
}

export interface TopArtistItem {
  artist: string;
  plays: number;
  trackCount: number;
  listenedSeconds: number;
}

export interface MyStats {
  from: string | null;
  to: string | null;
  totalPlays: number;
  distinctTracks: number;
  totalListenedSeconds: number;
  completedPlays: number;
  topTracks: TopTrackItem[];
  topArtists: TopArtistItem[];
}

export interface ArtistStats {
  artist: string;
  totalPlays: number;
  distinctListeners: number;
  trackCount: number;
  myPlays: number;
  myListenedSeconds: number;
}

/** One finished listening session, as reported to POST /api/plays. */
export interface PlayEventReport {
  clientEventId: string;
  trackId: string;
  startedAt: string;
  listenedSeconds: number;
  trackDuration?: number;
  source: "web";
}

export interface ReportPlaysResponse {
  results: { clientEventId: string; status: string; reason: string | null }[];
  accepted: number;
  duplicates: number;
  rejected: number;
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

export interface TelegramStatus {
  botEnabled: boolean;
  linked: boolean;
}

export interface ApiErrorResponse {
  error: string;
  statusCode: number;
}
