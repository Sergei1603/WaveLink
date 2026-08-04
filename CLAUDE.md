# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

**WaveLink** is a personal music streaming service. Users register, upload their own audio
files, organize them into collections, and stream them through a browser player built on
`wavesurfer.js`. The same library is reachable through a Telegram bot — users can upload
tracks by sending audio to the bot, and download them with `/get <title>`.

The backend is a single ASP.NET Core 10 Web API (monolith). PostgreSQL stores metadata;
MinIO (S3-compatible) stores audio files; a long-running `BackgroundService` hosts the
Telegram bot inside the same process.

## Repository layout

```
/WaveLink.sln
/global.json                    pins .NET 10 SDK
/docker-compose.yml             postgres + minio for local development
/WaveLink.API/                  ASP.NET Core project
  Program.cs                    composition root (DI, auth, CORS, middleware, swagger)
  Controllers/                  thin HTTP layer — all business logic lives in Services
  Services/                     AuthService, TrackService, CollectionService,
                                MinioStorageService, TelegramBotService,
                                TelegramLinkTokenService
  Entities/                     EF Core entities (POCOs)
  Data/AppDbContext.cs          DbContext + Fluent API mapping
  DTOs/                         request/response records
  Options/                      strongly-typed config (Jwt / Minio / Telegram)
  Middleware/                   global error handling
  Common/                       AppException, CurrentUser helper
/client/                        React + TypeScript + Vite frontend (scaffold separately)
```

## Tech stack

- ASP.NET Core 10 Web API, C# 14
- Entity Framework Core 10 + Npgsql for PostgreSQL
- MinIO .NET SDK (S3-compatible storage)
- JWT bearer auth (`Microsoft.AspNetCore.Authentication.JwtBearer`) + DB-backed refresh tokens
- BCrypt for password hashing
- Telegram.Bot 22.x, hosted via `BackgroundService`
- Swashbuckle for Swagger UI in Development
- Frontend (separate): React + TypeScript + Vite, wavesurfer.js for audio

## Architectural conventions

1. **Controllers stay thin.** They translate HTTP into DTOs, call a service, and return the
   result. No EF queries, no MinIO calls, no business rules in controllers.
2. **Services own the database and storage.** Each service exposes an interface
   (`IAuthService`, `ITrackService`, ...) registered scoped in `Program.cs`. The Telegram
   `BackgroundService` is a singleton and resolves scoped services through
   `IServiceProvider.CreateScope()` for each update.
3. **Errors are domain-typed.** Business failures throw `AppException` with an HTTP status.
   `ErrorHandlingMiddleware` converts every exception to the uniform
   `{ "error": string, "statusCode": int }` shape. Controllers never `try/catch`.
4. **Ownership is enforced in services, not at the DB layer.** Every per-resource service
   method (`GetOwnedAsync`, `AddTrackAsync`, etc.) loads the entity, then compares
   `UserId` against the caller's id (extracted via `CurrentUser.GetId(User)` from the JWT
   `sub` claim). Mismatches throw `AppException.Forbidden()`.
5. **Refresh tokens are rotated and hashed.** Login/register issue a (short access + long
   refresh) pair. Refresh tokens are stored as SHA-256 hashes; on `/refresh` the old one
   is revoked and a new pair is issued. `/logout` revokes the supplied token.
6. **Configuration is bound to options classes.** `JwtOptions`, `MinioOptions`,
   `TelegramOptions` are bound from `appsettings.json` and injected via `IOptions<T>`.
   Never read `IConfiguration` from inside services.
7. **Streaming uses HTTP Range.** `GET /api/tracks/{id}/stream` parses `Range: bytes=...`,
   asks MinIO for that byte slice via `OpenRangeAsync`, and replies `206 Partial Content`
   with `Content-Range` set. Browsers / `wavesurfer.js` will use this for seek and gapless
   playback. Presigned URLs (15 min TTL) are also available via the storage service if you
   prefer to hand the client a direct MinIO link.

## Database schema (EF Core)

- **users** — id (uuid PK), email (unique), password_hash, telegram_chat_id (unique, nullable), created_at
- **tracks** — id, user_id (FK cascade), title, artist, duration (sec), file_key, file_size, mime_type, uploaded_at
- **collections** — id, user_id (FK cascade), name, created_at
- **collection_tracks** — composite (collection_id, track_id) PK, added_at, both FKs cascade
- **refresh_tokens** — id, user_id (FK cascade), token_hash (unique, SHA-256), expires_at, created_at, revoked_at
- **telegram_link_tokens** — id, user_id (FK cascade), token (unique), expires_at, used_at (single-use, 10-min TTL)

Migrations live under `WaveLink.API/Migrations/` once generated (`dotnet ef migrations add Init`).
`Program.cs` runs `Database.MigrateAsync()` at startup.

## API surface

All routes require JWT bearer auth except those under `/api/auth/*`.

| Method | Route | Notes |
| --- | --- | --- |
| POST | `/api/auth/register` | `{ email, password }` → token pair |
| POST | `/api/auth/login` | `{ email, password }` → token pair |
| POST | `/api/auth/refresh` | `{ refreshToken }` → new pair (rotates) |
| POST | `/api/auth/logout` | revokes refresh token |
| GET  | `/api/tracks?page=&limit=` | paginated, newest first |
| POST | `/api/tracks/upload` | multipart: file + title + artist (+ optional duration) |
| DELETE | `/api/tracks/{id}` | removes from MinIO + DB |
| GET  | `/api/tracks/{id}/stream` | HTTP Range supported |
| GET  | `/api/collections` | list with track counts |
| POST | `/api/collections` | `{ name }` |
| POST | `/api/collections/{id}/tracks` | `{ trackId }` |
| DELETE | `/api/collections/{id}/tracks/{trackId}` | |
| DELETE | `/api/collections/{id}` | |
| GET  | `/api/telegram/generate-link-token` | one-time 10-min token |
| POST | `/api/telegram/link` | returns 410; linking is completed via the bot |
| GET  | `/api/telegram/status` | `{ botEnabled, linked }` |
| POST | `/api/telegram/send` | `{ trackId }` → pushes the audio into the caller's bot chat |

## Telegram bot

The bot polls (no webhook) inside `TelegramBotService : BackgroundService`. It is
disabled by default — set `Telegram:Enabled=true` and `Telegram:BotToken` to activate.
The `ITelegramBotClient` itself is owned by the singleton `TelegramClientProvider`
(`Client == null` when disabled), so both the polling service and `TelegramDeliveryService`
(used by `TelegramController`) share one client.

Commands:

- `/start`, `/help` — onboarding + command list
- `/link <token>` — pairs the chat with the WaveLink user via `TelegramLinkTokenService`
- `/list` — shows the 20 most recent tracks
- `/upload` — prompts the user; on any subsequent audio message or audio document, the
  bot downloads the file, uploads it to MinIO, and creates a `Track` row with
  `IsPublic = true` (bot uploads always go straight into the public bank, unlike web
  uploads where the user picks)
- `/get <title>` — case-insensitive ILIKE search; sends the first match as audio

All bot handlers resolve the WaveLink user through `User.TelegramChatId`. Unlinked chats
get a hint to run `/link`.

The reverse direction (web → chat) goes through `POST /api/telegram/send`: the service
resolves `User.TelegramChatId`, checks access via `ITrackService.GetAccessibleAsync`, and
streams the object from MinIO into `SendAudio`. The frontend shows the ✈ button on a
track row only when `GET /api/telegram/status` reports `linked` (cached in
`TelegramContext`).

## File storage (MinIO)

- Bucket: `wavelink-tracks` (auto-created on startup via `EnsureBucketAsync`)
- Object key: `{userId}/{trackId}/{originalFilename}`
- Upload validation: MIME whitelist (`audio/mpeg`, `audio/flac`, `audio/wav`,
  `audio/x-wav`, `audio/ogg`, `audio/vorbis`) and a 50 MB limit
- Deleting a track removes the object first, then the DB row; if the object delete fails,
  the DB row is still removed and the failure is logged (orphan blobs are tolerable;
  dangling DB rows are not)

## Running locally

```powershell
docker compose up -d                    # postgres + minio
dotnet restore
dotnet ef database update --project WaveLink.API   # or let startup MigrateAsync handle it
dotnet run --project WaveLink.API
```

Swagger UI: `http://localhost:5000/swagger`
MinIO console: `http://localhost:9001` (user/pass from `docker-compose.yml`)
Frontend dev server (when scaffolded): `http://localhost:5173`

## EF Core migrations

```powershell
dotnet ef migrations add <Name> --project WaveLink.API
dotnet ef database update --project WaveLink.API
```

If `dotnet-ef` is not installed: `dotnet tool install --global dotnet-ef --version 10.*`.

## Configuration

`appsettings.json` ships with development defaults that match `docker-compose.yml`.
For production, override via environment variables or user-secrets — at minimum:

- `ConnectionStrings__Postgres`
- `Jwt__Secret` (≥ 32 chars, HMAC-SHA256)
- `Minio__AccessKey` / `Minio__SecretKey`
- `Telegram__BotToken` (and `Telegram__Enabled=true`)

Never commit a real `Jwt:Secret` or production MinIO credentials.

## Notes for future changes

- The Telegram `BackgroundService` is a singleton; **always** create a DI scope before
  resolving `AppDbContext` or any scoped service inside a handler.
- `CurrentUser.GetId(User)` reads the `sub` claim — JWT setup in `Program.cs` sets
  `NameClaimType = "sub"`; keep that in sync if the claim scheme changes.
- The streaming controller returns the entire blob into a `MemoryStream` today (MinIO SDK
  callback-stream pattern). For large files or many concurrent listeners, swap to a true
  pass-through stream or use presigned URLs.
- CORS allows only `http://localhost:5173`. Add production origins in `Program.cs` when
  deploying the frontend.
