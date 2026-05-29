# Деплой WaveLink на Render + хранилище — выжимка

## 1. Архитектура деплоя

- **API** (ASP.NET 10) — Render Web Service (Docker)
- **PostgreSQL** — Render managed database
- **Frontend** (Vite/React) — Render Static Site
- **Хранилище** — внешний S3-совместимый сервис (Render free-tier не даёт persistent disk для своего MinIO)

## 2. Что добавлено в репозиторий

| Файл | Назначение |
|---|---|
| `WaveLink.API/Dockerfile` | Multi-stage сборка .NET 10, биндится на `$PORT` от Render через shell-entrypoint |
| `.dockerignore` | Исключает `bin/obj/node_modules/client/.git` |
| `render.yaml` | Blueprint: Postgres + API + Static Site, JWT-секрет генерируется автоматически |
| `client/.env.example` | Шаблон `VITE_API_URL` |
| `DEPLOY.md` | Инструкция по Render |
| `MINIO_VPS_SETUP.md` | Подробная инструкция по MinIO на VPS |

## 3. Изменения в коде

- **`Program.cs`** — CORS читает список origin-ов из `Cors:AllowedOrigins` (через запятую), дефолт `http://localhost:5173`.
- **`client/src/api/client.ts`** — добавлены `API_BASE` и `apiUrl(path)`, все `fetch` префиксуются `VITE_API_URL`. Пусто = same-origin (для dev с Vite-прокси).
- **`client/src/api/tracks.ts`** — `streamUrl()` тоже через `apiUrl()`, чтобы стрим шёл прямо на API.

## 4. Порядок деплоя на Render

1. Запушить в GitHub.
2. Render Dashboard → New → Blueprint → выбрать репо.
3. Заполнить переменные с `sync: false`:
   - `Minio__Endpoint/AccessKey/SecretKey` — из вашего S3-провайдера
   - `Telegram__BotToken` — от @BotFather
   - `Cors__AllowedOrigins`, `VITE_API_URL` — оставить пустыми
4. После первого деплоя домены становятся известны:
   - `Cors__AllowedOrigins = https://wavelink-client.onrender.com` → передеплой API
   - `VITE_API_URL = https://wavelink-api.onrender.com` → Manual Deploy фронта (env-переменные `VITE_*` встраиваются на этапе сборки)

## 5. Хранилище — MinIO на собственной VPS

### Архитектура

- VPS с Docker + Caddy (TLS) + MinIO
- API на Render ходит на `https://<домен>` → Caddy → MinIO
- Стрим идёт через API (сервер тянет байты из MinIO и отдаёт 206 браузеру) — **CORS на MinIO не нужен**

### Базовый стек на VPS

`~/minio-stack/`:
- `.env` — `MINIO_ROOT_USER/PASSWORD`, `DOMAIN_S3`
- `docker-compose.yml` — `minio` + `caddy`, консоль MinIO биндится только на `127.0.0.1:9001`
- `Caddyfile` — reverse_proxy на minio:9000, multi-line блоки обязательны

### Безопасность

- Root-ключ MinIO **не** использовать в API — создать через консоль IAM-юзера `wavelink-api` с политикой только на `wavelink-tracks`, ему — Service Account → сервисные Access Key/Secret Key → в Render
- Порты 9000/9001 наружу не открывать, только через Caddy на 443
- UFW: 22/80/443
- В консоль ходить через SSH-туннель: `ssh -L 9001:localhost:9001 user@vps`

### Render переменные

| Key | Value |
|---|---|
| `Minio__Endpoint` | `s3.example.com` (без `https://`, без слэшей, без порта если 443) |
| `Minio__AccessKey/SecretKey` | сервисные, не root |
| `Minio__Bucket` | `wavelink-tracks` |
| `Minio__UseSsl` | `true` |

## 6. Проблемы, с которыми столкнулись по факту

### 6.1. Третий уровень домена от хостинга

Решение — два варианта:
- **A**: если хостинг разрешает `*.yourname.host.ru` — добавить A-записи `s3.` и `console.`
- **B** (рекомендован): один домен для S3, консоль через SSH-туннель

### 6.2. Порт 443 занят другим сервисом на VPS

Сначала проверить: `sudo ss -tlnp | grep -E ':(80|443) '`

Варианты:
- **A**: если 443 держит nginx/apache хостинга — использовать **его** как reverse proxy на `127.0.0.1:9000`, Caddy не нужен
- **B**: отключить занимающий 443 сервис, отдать Caddy
- **C**: повесить Caddy на 8443, в `Minio__Endpoint = host:8443`. Маппинг портов **строго 1:1**: `"8443:8443"`, в Caddyfile сайт `host:8443`

### 6.3. Caddyfile syntax: `Unexpected next token after '{'`

Caddy не принимает блок с `{` на одной строке с директивой:

```caddyfile
# НЕ работает:
request_body { max_size 100MB }

# Работает:
request_body {
    max_size 100MB
}
```

### 6.4. NXDOMAIN при ACME-челлендже

Let's Encrypt сначала резолвит имя — если DNS-записи нет, валидация падает с `NXDOMAIN looking up A`. У хостингов типа `play2go.cloud` «красивое имя» часто только в их панели, а в публичном DNS его нет.

Проверка: `dig +short <домен>` — должно вернуть IP.

Варианты:
- **A**: попросить хостинг опубликовать A-запись
- **B** (быстро и бесплатно): **DuckDNS** — регистрация на duckdns.org, `<имя>.duckdns.org` → ваш IP за 2 минуты
- **C**: купить свой домен (~200₽/год)

### 6.5. Порт 80 для ACME HTTP-01

Даже с правильным DNS, Let's Encrypt валидирует через **публичный 80**, не через ваш 8080. Если 80 занят — Caddy не получит сертификат.

Варианты:
- освободить 80
- DNS-01 challenge (требует API DNS-провайдера, для DuckDNS есть Caddy-плагин `caddy-dns/duckdns`)
- готовый сертификат от хостинга → `tls /path/cert.pem /path/key.pem` в Caddyfile

## 7. Чек-лист «всё работает»

- [ ] `dig +short <домен>` возвращает IP VPS
- [ ] `curl -I https://<домен>/minio/health/live` → HTTP/2 200
- [ ] В консоли MinIO бакет `wavelink-tracks` существует
- [ ] Создан IAM-юзер `wavelink-api` с политикой `wavelink-tracks-rw` и сервисными ключами
- [ ] В Render → `wavelink-api` → Logs нет `Could not ensure MinIO bucket`
- [ ] Загрузка трека с фронта → объект появляется в `{userId}/{trackId}/{filename}` в бакете
- [ ] Play работает, в логах API нет ошибок
- [ ] `/list` в Telegram отдаёт инлайн-кнопки, клик → файл приходит
