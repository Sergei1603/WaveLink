# WaveLink

Personal music streaming service: upload your music, organize it into collections,
stream it via a browser player, and interact with the same library through a Telegram bot.

- **Backend:** ASP.NET Core 10 Web API · PostgreSQL · MinIO · JWT auth · Telegram.Bot
- **Frontend:** React + TypeScript + Vite (in `/client`, scaffold separately) · wavesurfer.js

See [CLAUDE.md](CLAUDE.md) for the full architecture description.
Production deploy on your own VPS: [DEPLOY_VPS.md](DEPLOY_VPS.md).

## Quick start

### Требования

- [.NET 10 SDK](https://dotnet.microsoft.com/download)
- [Node.js 18+ LTS](https://nodejs.org/)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (для Postgres + MinIO)

### 1. Инфраструктура

```powershell
docker compose up -d
```

### 2. Backend

```powershell
dotnet run --project WaveLink.API
```

### 3. Frontend

```powershell
cd client
npm install       # только при первом запуске
npm run dev
```

| Сервис | URL |
|---|---|
| API | <http://localhost:5000> |
| Scalar (OpenAPI) | <http://localhost:5000/scalar/v1> |
| Frontend | <http://localhost:5173> |
| MinIO console | <http://localhost:9001> |

Dev-сервер Vite автоматически проксирует `/api/*` на `http://localhost:5000` — отдельно ничего настраивать не нужно.

## Layout

```
WaveLink.API/    ASP.NET Core Web API (monolith)
client/          React + Vite frontend (scaffold separately)
docker-compose.yml
CLAUDE.md
```

## Configuration

Edit `WaveLink.API/appsettings.json` or set environment variables:

- `ConnectionStrings__Postgres`
- `Jwt__Secret` — change before any deploy
- `Minio__AccessKey` / `Minio__SecretKey`
- `Telegram__BotToken` + `Telegram__Enabled=true` to enable the bot
