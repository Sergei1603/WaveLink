# Развёртывание WaveLink на собственном VPS

Полностью контейнеризованный деплой: один `docker compose` поднимает весь стек —
PostgreSQL, MinIO (локальное S3-хранилище), API (ASP.NET Core) и фронтенд за nginx.
Наружу открыт только nginx (порт 80/443), всё остальное живёт во внутренней docker-сети.

```
Интернет ──▶ nginx (web, :80/:443)
                 ├── /          → статика React (SPA)
                 └── /api/*      → api:8080  (ASP.NET Core)
                                     ├── postgres:5432
                                     └── minio:9000
```

Фронт и API работают на одном домене (same-origin), поэтому CORS и отдельный URL API
не нужны.

---

## 0. Что уже готово в проекте

- `WaveLink.API/Dockerfile` — сборка и запуск API.
- `client/Dockerfile` + `client/deploy/nginx.conf` — сборка SPA и раздача через nginx
  с reverse-proxy на API.
- `docker-compose.prod.yml` — весь прод-стек.
- `.env.prod.example` — шаблон секретов.
- Миграции БД применяются автоматически при старте API (`Database.MigrateAsync()`).
- Бакет MinIO `wavelink-tracks` создаётся автоматически при старте.

То есть ставить .NET SDK, Node или вручную накатывать миграции на сервере **не нужно** —
всё делается внутри контейнеров.

---

## 1. Требования к VPS

- Ubuntu 22.04/24.04 (или любой Linux с Docker).
- Минимум **2 ГБ RAM**, если собираешь образ **на самом сервере** (`docker-compose.prod.yml`).
  Если собираешь образы в GitHub Actions и тянешь готовые (раздел 11) — хватает **~1 ГБ**.
- ~5 ГБ свободного диска + место под загружаемые треки.
- Открытые порты: 80 и 443 (для HTTPS), 22 (SSH).
- Домен, направленный A-записью на IP сервера (нужен для HTTPS; для теста можно по IP).

---

## 2. Установка Docker на сервере

```bash
ssh user@ВАШ_IP

# Docker + compose plugin
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# перелогиниться, чтобы группа применилась:
exit
ssh user@ВАШ_IP

docker --version
docker compose version
```

---

## 3. Доставка кода на сервер

Вариант А — через git (рекомендуется):

```bash
git clone <URL_вашего_репозитория> wavelink
cd wavelink
```

Вариант Б — скопировать с локальной машины (без node_modules/bin/obj):

```bash
# с локальной машины
scp -r WaveLink.API client docker-compose.prod.yml .env.prod.example \
       nuget.config global.json user@ВАШ_IP:~/wavelink/
```

---

## 4. Настройка секретов

```bash
cp .env.prod.example .env.prod
nano .env.prod
```

Заполни **все** значения. Сгенерировать секреты:

```bash
openssl rand -base64 48   # для JWT_SECRET
openssl rand -base64 24   # для паролей Postgres / MinIO
```

| Переменная | Что это |
| --- | --- |
| `POSTGRES_PASSWORD` | пароль БД (любой длинный) |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | логин/пароль MinIO (пароль ≥ 8 символов) |
| `JWT_SECRET` | ключ подписи JWT, **≥ 32 символа** |
| `TELEGRAM_ENABLED` | `true`, если нужен бот |
| `TELEGRAM_BOT_TOKEN` | токен от @BotFather (если бот включён) |

> `.env.prod` уже в `.gitignore` — он не попадёт в репозиторий.

---

## 5. Первый запуск

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

Первая сборка займёт несколько минут (тянется .NET SDK и npm-зависимости). Дальше:

```bash
# статус контейнеров
docker compose -f docker-compose.prod.yml ps

# логи API (тут видно применение миграций и создание бакета)
docker compose -f docker-compose.prod.yml logs -f api
```

Открой `http://ВАШ_IP` — должен загрузиться интерфейс WaveLink. Зарегистрируй
пользователя, загрузи трек, проверь воспроизведение.

---

## 6. HTTPS (обязательно для прода)

Без TLS логин/пароль и токены идут по сети открытым текстом. Самый простой путь —
поставить Caddy перед стеком (автоматические Let's Encrypt сертификаты).

1. В `docker-compose.prod.yml` у сервиса `web` убери публикацию порта 80 наружу
   (поменяй `ports` на `expose`):

   ```yaml
   web:
     # ports:
     #   - "80:80"
     expose:
       - "80"
   ```

2. Добавь сервис Caddy в тот же compose-файл:

   ```yaml
   caddy:
     image: caddy:2-alpine
     container_name: wavelink-caddy
     restart: unless-stopped
     ports:
       - "80:80"
       - "443:443"
     volumes:
       - ./Caddyfile:/etc/caddy/Caddyfile
       - caddy_data:/data
       - caddy_config:/config
     depends_on:
       - web
     networks: [wavelink]
   ```

   и в `volumes:` добавь `caddy_data:` и `caddy_config:`.

3. Создай файл `Caddyfile` рядом с compose:

   ```
   music.example.com {
       reverse_proxy web:80
   }
   ```

4. Перезапусти:

   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
   ```

Caddy сам получит и будет продлевать сертификат. Сайт станет доступен по
`https://music.example.com`.

> Альтернатива — nginx + certbot на хосте, но Caddy для одного домена проще.

---

## 7. Telegram-бот (необязательно)

1. Получи токен у [@BotFather](https://t.me/BotFather).
2. В `.env.prod`: `TELEGRAM_ENABLED=true` и `TELEGRAM_BOT_TOKEN=<токен>`.
3. Перезапусти: `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d`.

Бот работает на long-polling (вебхук не нужен, входящие порты открывать не надо).
Привязка аккаунта: в веб-интерфейсе получи токен привязки → в боте `/link <токен>`.

---

## 8. Обновление приложения

```bash
cd ~/wavelink
git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

Новые миграции применятся автоматически при старте API.

---

## 9. Резервное копирование

Все данные — в docker volumes `postgres_data` и `minio_data`.

```bash
# дамп БД
docker compose -f docker-compose.prod.yml exec postgres \
  pg_dump -U wavelink wavelink > backup_$(date +%F).sql

# бэкап файлов MinIO (содержимое тома)
docker run --rm -v wavelink_minio_data:/data -v $(pwd):/backup alpine \
  tar czf /backup/minio_$(date +%F).tar.gz -C /data .
```

(Имя тома — с префиксом проекта, проверь через `docker volume ls`.)

---

## 10. Шпаргалка по диагностике

| Симптом | Где смотреть |
| --- | --- |
| Сайт не открывается | `docker compose ... ps`, `... logs web` |
| 502 на `/api/...` | API не поднялся: `... logs api` (чаще — БД или JWT_SECRET) |
| API падает на старте | проверь `ConnectionStrings__Postgres`, что postgres healthy |
| Загрузка трека > 50 МБ не идёт | лимит 50 МБ зашит в API; nginx разрешает 60 МБ |
| Не играет аудио | `... logs api` — ошибки MinIO/бакета |
| Полный рестарт | `docker compose -f docker-compose.prod.yml --env-file .env.prod down && ... up -d` |

> `down` **без** флага `-v` данные не удаляет. Не добавляй `-v`, если не хочешь стереть БД и файлы.

---

## 11. Альтернатива: сборка образов в GitHub Actions

Чтобы не нагружать VPS сборкой (тяжёлый .NET SDK), образы можно собирать на бесплатных
раннерах GitHub и пушить в GitHub Container Registry (ghcr.io). Сервер тогда только тянет
готовые образы — хватает ~1 ГБ RAM.

В репозитории уже лежат:
- `.github/workflows/build-images.yml` — собирает `wavelink-api` и `wavelink-web`,
  пушит в `ghcr.io/sergei1603/*` на каждый push в `main`/`master` (и на теги `v*`).
- `docker-compose.deploy.yml` — прод-стек на готовых образах (без `build:`).

### 11.1. Запустить сборку

1. Запушь код в GitHub. Workflow стартует сам (вкладка **Actions**), либо запусти
   вручную через **Run workflow** (`workflow_dispatch`).
2. После успеха образы появятся в **Packages** профиля/репозитория.
3. Сделай оба пакета доступными для скачивания на сервере — проще всего открыть их:
   GitHub → пакет `wavelink-api` → **Package settings** → **Change visibility → Public**
   (то же для `wavelink-web`). Тогда `docker pull` на VPS не требует логина.

   > Если хочешь оставить образы приватными — на сервере выполни `docker login ghcr.io`
   > с GitHub-токеном (PAT с правом `read:packages`).

### 11.2. Развернуть на VPS из готовых образов

```bash
cd ~/wavelink
cp .env.prod.example .env.prod && nano .env.prod   # как в разделе 4

docker compose -f docker-compose.deploy.yml --env-file .env.prod pull
docker compose -f docker-compose.deploy.yml --env-file .env.prod up -d
```

На сервере при этом достаточно иметь только `docker-compose.deploy.yml` и `.env.prod` —
исходники клонировать не обязательно.

### 11.3. Обновление

```bash
docker compose -f docker-compose.deploy.yml --env-file .env.prod pull
docker compose -f docker-compose.deploy.yml --env-file .env.prod up -d
```

По умолчанию тянется тег `latest`. Чтобы закрепить конкретную версию, добавь в `.env.prod`
строку `IMAGE_TAG=<sha-коммита>` (или тег `v*`) — compose подставит её в имя образа.

> HTTPS (раздел 6), Telegram (раздел 7) и бэкапы (раздел 9) работают так же — отличается
> только compose-файл (`docker-compose.deploy.yml` вместо `docker-compose.prod.yml`).

---

## Замечания по безопасности

- Консоль MinIO в `docker-compose.prod.yml` проброшена только на `127.0.0.1:9001` —
  снаружи недоступна. Зайти можно через SSH-туннель:
  `ssh -L 9001:127.0.0.1:9001 user@ВАШ_IP`, затем `http://localhost:9001`.
- Postgres и MinIO наружу не публикуются вообще — только внутренняя сеть `wavelink`.
- Настрой файрвол (UFW): разреши только 22, 80, 443.
  ```bash
  sudo ufw allow 22 && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable
  ```
- Не коммить `.env.prod`. Меняй секреты, если они засветились.
