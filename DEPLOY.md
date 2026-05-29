# Deploy to Render.com

Render Blueprint (`render.yaml`) описывает три ресурса: managed Postgres,
ASP.NET API (Docker) и статический фронт. Хранилище аудио — внешний
S3-совместимый сервис (Cloudflare R2 / Backblaze B2 / AWS S3).

## 1. Подготовка S3-хранилища

Render free-tier не предоставляет persistent disks, поэтому MinIO в кластере
не выживет деплой. Самый дешёвый вариант — **Cloudflare R2** (10 ГБ free):

1. В Cloudflare dashboard → R2 → Create bucket → `wavelink-tracks`.
2. R2 → Manage R2 API Tokens → Create Token (Object Read & Write) → запомнить
   Access Key и Secret Key.
3. Endpoint: `<account-id>.r2.cloudflarestorage.com` (без `https://`).

Любой другой S3-совместимый сервис тоже подойдёт — MinIO .NET SDK работает с
сырым S3 API.

## 2. Деплой через Blueprint

1. Запушить репозиторий в GitHub.
2. В Render dashboard → **New → Blueprint** → выбрать репозиторий.
3. Render прочитает `render.yaml` и предложит создать ресурсы.
4. В мастере заполнить переменные с `sync: false`:
   - `Minio__Endpoint`, `Minio__AccessKey`, `Minio__SecretKey` — из R2.
   - `Telegram__BotToken` — токен бота от @BotFather.
   - `Cors__AllowedOrigins` — пока пусто, заполним после.
   - `VITE_API_URL` — пока пусто, заполним после.
5. Запустить Apply.

## 3. Связать фронт и API

После первого деплоя у сервисов появятся домены вида
`https://wavelink-api.onrender.com` и `https://wavelink-client.onrender.com`.

1. **wavelink-api** → Environment → `Cors__AllowedOrigins` =
   `https://wavelink-client.onrender.com` (несколько — через запятую).
   Сохранить → API перезапустится.
2. **wavelink-client** → Environment → `VITE_API_URL` =
   `https://wavelink-api.onrender.com`. Сохранить → запустить Manual Deploy
   (env-переменные `VITE_*` встраиваются в бандл на этапе сборки).

## 4. Проверка

- API healthcheck: `GET https://wavelink-api.onrender.com/openapi/v1.json` → 200.
- Открыть фронт, зарегистрироваться, загрузить трек — он должен лечь в R2 bucket
  и проигрываться.
- Telegram: бот должен ответить `/start` (логи API: `Telegram bot started`).

## Известные ограничения free-tier

- API на free Web Service "засыпает" после 15 минут без трафика, первый
  запрос — холодный старт ~30 секунд.
- Postgres free: 1 GB, удаляется через 90 дней.
- Cloudflare R2 free: 10 GB и 1 млн class-A операций/мес.
- Стриминг с холодного старта может приводить к таймаутам wavesurfer.js —
  при необходимости перейти на платный тариф.

## Локальная разработка не меняется

`docker compose up -d` + `dotnet run` + `npm run dev` — как раньше.
`VITE_API_URL` пустой = same-origin + Vite proxy на `localhost:5000`.
