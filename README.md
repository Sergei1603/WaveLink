# WaveLink

Personal music streaming service: upload your music, organize it into collections,
stream it via a browser player, and interact with the same library through a Telegram bot.

- **Backend:** ASP.NET Core 10 Web API · PostgreSQL · MinIO · JWT auth · Telegram.Bot
- **Frontend:** React + TypeScript + Vite (in `/client`, scaffold separately) · wavesurfer.js

See [CLAUDE.md](CLAUDE.md) for the full architecture description.

## Quick start

```powershell
docker compose up -d                      # postgres + minio
dotnet run --project WaveLink.API
```

- API: <http://localhost:5000>
- Swagger: <http://localhost:5000/swagger>
- MinIO console: <http://localhost:9001>

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
