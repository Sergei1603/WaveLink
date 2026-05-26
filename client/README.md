# WaveLink client

React + TypeScript + Vite frontend for WaveLink.

## Requirements

- Node.js 18+ (install from https://nodejs.org/ — LTS recommended)

## Run

```powershell
cd client
npm install
npm run dev
```

Open http://localhost:5173

The dev server proxies `/api/*` to `http://localhost:5000`, so make sure the API is
running (`dotnet run --project WaveLink.API` from the repo root).

## Build

```powershell
npm run build      # outputs to dist/
npm run preview    # serve the built bundle
```

## Structure

```
src/
  api/        — typed fetch wrappers (auth, tracks, collections, telegram)
  auth/       — AuthContext (JWT + refresh token storage in localStorage)
  player/     — PlayerContext (current track + queue)
  components/ — Layout, PlayerBar (wavesurfer.js), UploadModal, TrackRow, ...
  pages/      — Login, Register, Library, Collections, CollectionDetail, Telegram
  App.tsx     — routes
  main.tsx    — entry
  index.css   — dark theme, no UI library
```

## How auth works

1. Login/register issues `{ accessToken, refreshToken, accessTokenExpiresAt }`, stored in
   `localStorage` under `wavelink.tokens`.
2. Every request attaches `Authorization: Bearer <accessToken>`.
3. On 401, the client transparently calls `/api/auth/refresh` and retries once.

## How the player works

Wavesurfer.js needs the whole audio buffer to draw the waveform, so the client fetches
the track with `Authorization` header, builds a `Blob` URL, and hands it to wavesurfer.
HTTP Range support on `/api/tracks/{id}/stream` is still useful for native `<audio>` use
cases but we don't need it here.
