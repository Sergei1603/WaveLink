# MinIO на собственной VPS — пошаговая инструкция

API WaveLink развёрнут на Render и должен ходить в хранилище через интернет,
поэтому MinIO нужен публичный HTTPS-эндпоинт. Эта инструкция собирает всё с
нуля: настройку VPS, домен, TLS, MinIO в Docker, отдельный IAM-юзер и
подключение к Render.

Ожидаемое время — 30–60 минут.

---

## 0. Что должно быть до старта

- VPS с Ubuntu 22.04/24.04 (минимум 1 vCPU / 1 GB RAM / 20 GB диска).
  Подойдёт Hetzner CX11, Selectel Shared Line, любой VDS.
- Домен или поддомен, к которому есть доступ к DNS (например, `s3.example.com`).
- SSH-доступ под root или sudo-пользователя.
- Готовый деплой WaveLink на Render (`wavelink-api` уже создан, но env-переменные
  для хранилища пока не заполнены).

---

## 1. Базовая настройка VPS

### 1.1. Подключиться по SSH

```bash
ssh root@<IP_VPS>
```

### 1.2. Обновить систему

```bash
apt update && apt upgrade -y
```

### 1.3. Создать непривилегированного пользователя (опционально, но желательно)

```bash
adduser wavelink
usermod -aG sudo wavelink
mkdir -p /home/wavelink/.ssh
cp ~/.ssh/authorized_keys /home/wavelink/.ssh/
chown -R wavelink:wavelink /home/wavelink/.ssh
chmod 700 /home/wavelink/.ssh
chmod 600 /home/wavelink/.ssh/authorized_keys
```

Дальше работаем под `wavelink` (`su - wavelink` или новая SSH-сессия).

### 1.4. Firewall

```bash
sudo apt install -y ufw
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp       # SSH
sudo ufw allow 80/tcp       # HTTP (нужен Caddy для ACME-челленджа)
sudo ufw allow 443/tcp      # HTTPS
sudo ufw enable
sudo ufw status
```

Порты `9000` и `9001` (MinIO) наружу **не открываем** — трафик идёт через
Caddy на 443.

### 1.5. Установить Docker и Docker Compose

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# Перелогиниться, чтобы группа применилась
exit
ssh wavelink@<IP_VPS>
docker --version
docker compose version
```

---

## 2. DNS

В панели управления DNS добавить **A-запись**:

| Имя | Тип | Значение | TTL |
|---|---|---|---|
| `s3` | A | `<IP_VPS>` | 300 |
| `s3-console` | A | `<IP_VPS>` | 300 |

Проверить распространение:

```bash
dig +short s3.example.com
dig +short s3-console.example.com
```

Обе команды должны вернуть IP вашей VPS. Если нет — подождать 5–10 минут.

---

## 3. Подготовить директорию проекта на VPS

```bash
mkdir -p ~/minio-stack
cd ~/minio-stack
mkdir -p data
```

### 3.1. `.env`

```bash
nano .env
```

```env
MINIO_ROOT_USER=admin
MINIO_ROOT_PASSWORD=<СЛУЧАЙНАЯ_СТРОКА_32+_СИМВОЛА>
DOMAIN_S3=s3.example.com
DOMAIN_CONSOLE=s3-console.example.com
```

Сгенерировать пароль:
```bash
openssl rand -base64 32
```

Права на файл с секретами:
```bash
chmod 600 .env
```

### 3.2. `docker-compose.yml`

```bash
nano docker-compose.yml
```

```yaml
services:
  minio:
    image: minio/minio:latest
    restart: unless-stopped
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
      MINIO_BROWSER_REDIRECT_URL: https://${DOMAIN_CONSOLE}
      MINIO_SERVER_URL: https://${DOMAIN_S3}
    volumes:
      - ./data:/data
    networks: [web]
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 5s
      retries: 3

  caddy:
    image: caddy:2
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    environment:
      DOMAIN_S3: ${DOMAIN_S3}
      DOMAIN_CONSOLE: ${DOMAIN_CONSOLE}
    networks: [web]
    depends_on:
      - minio

networks:
  web:

volumes:
  caddy_data:
  caddy_config:
```

### 3.3. `Caddyfile`

```bash
nano Caddyfile
```

```
{$DOMAIN_S3} {
    reverse_proxy minio:9000
    request_body {
        max_size 100MB
    }
    encode gzip
}

{$DOMAIN_CONSOLE} {
    reverse_proxy minio:9001
}
```

`max_size` берётся с запасом над лимитом загрузок в коде (50 МБ в
[TrackService.cs](WaveLink.API/Services/TrackService.cs)).

---

## 4. Запустить стек

```bash
docker compose up -d
docker compose ps
docker compose logs -f caddy
```

В логах Caddy должно появиться `certificate obtained successfully` для обоих
доменов (это занимает 10–60 секунд при первом старте). Если ошибка ACME —
проверьте, что 80/443 действительно открыты и DNS уже резолвится.

Проверка снаружи:
```bash
curl -I https://s3.example.com/minio/health/live
# Ожидаем HTTP/2 200
```

---

## 5. Создать бакет и IAM-пользователя

### 5.1. Открыть консоль

В браузере: `https://s3-console.example.com`
Логин — `MINIO_ROOT_USER`, пароль — `MINIO_ROOT_PASSWORD` из `.env`.

### 5.2. Создать бакет

Buckets → Create Bucket → имя **`wavelink-tracks`** → Create.

Доступ оставить **Private** — приватные ключи через API.

### 5.3. Создать политику

Identity → Policies → Create Policy → имя `wavelink-tracks-rw` → вставить:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:ListBucket",
        "s3:GetBucketLocation"
      ],
      "Resource": [
        "arn:aws:s3:::wavelink-tracks",
        "arn:aws:s3:::wavelink-tracks/*"
      ]
    }
  ]
}
```

Save.

### 5.4. Создать пользователя

Identity → Users → Create User
- Username: `wavelink-api`
- Password: любой (для входа в консоль он не понадобится)
- Assign Policy: `wavelink-tracks-rw`
- Save.

### 5.5. Сгенерировать сервисный ключ

Кликнуть на созданного пользователя → Service Accounts → Create Access Key →
скопировать **Access Key** и **Secret Key**. Это и есть креды для API.

> Эти ключи будут видны один раз — сохраните в менеджере паролей.

---

## 6. Подключить API на Render

Render Dashboard → сервис `wavelink-api` → **Environment**:

| Key | Value |
|---|---|
| `Minio__Endpoint` | `s3.example.com` (без `https://`, без слэша, без порта) |
| `Minio__AccessKey` | Access Key из шага 5.5 |
| `Minio__SecretKey` | Secret Key из шага 5.5 |
| `Minio__Bucket` | `wavelink-tracks` |
| `Minio__UseSsl` | `true` |
| `Minio__PresignedUrlMinutes` | `15` |

Save Changes → Render автоматически передеплоит сервис.

---

## 7. Верификация

1. **API стартует**: Render → Logs у `wavelink-api` → не должно быть `Could not
   ensure MinIO bucket`. Должно быть `Now listening on: http://[::]:PORT`.
2. **Загрузка трека**: открыть фронт, залогиниться, загрузить mp3.
3. **Объект появился в MinIO**: консоль `https://s3-console.example.com` →
   Object Browser → `wavelink-tracks` → должна быть структура
   `{userId}/{trackId}/{filename}`.
4. **Стрим работает**: нажать Play в плеере, послушать. В логах API не должно
   быть ошибок.
5. **Telegram-бот**: отправить `/list`, потом нажать кнопку — файл придёт.

---

## 8. Бэкапы

MinIO хранит файлы в `~/minio-stack/data` на VPS. Для бэкапа достаточно
копировать эту директорию.

### 8.1. restic в S3-совместимый удалённый бакет (другой провайдер)

```bash
sudo apt install -y restic
export RESTIC_REPOSITORY=s3:https://<другой-s3>/wavelink-backup
export RESTIC_PASSWORD=<секрет>
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
restic init
restic backup ~/minio-stack/data
```

В cron (`crontab -e`) ежедневно в 4:00:
```
0 4 * * * cd /home/wavelink/minio-stack && /usr/bin/restic backup data >> /home/wavelink/restic.log 2>&1
```

### 8.2. Минимум — rsync на свою машину

```bash
rsync -avz --delete wavelink@<IP_VPS>:~/minio-stack/data/ ./minio-backup/
```

---

## 9. Обновление MinIO

```bash
cd ~/minio-stack
docker compose pull
docker compose up -d
docker compose logs -f minio
```

MinIO — rolling-compatible, downtime ≈ 5 секунд (Render API в это время вернёт
500 на стриме, но не упадёт).

---

## 10. Траблшутинг

| Симптом | Где смотреть | Что обычно |
|---|---|---|
| `Could not ensure MinIO bucket` в логах API | Render → wavelink-api → Logs | Неправильный `Minio__Endpoint`/ключи. Проверить, что endpoint **без** `https://` и **без** порта. |
| Caddy не выдаёт сертификат | `docker compose logs caddy` | DNS ещё не распространился, или 80/443 закрыты на VPS. |
| 403 при загрузке | Логи MinIO | У сервисного аккаунта нет политики `wavelink-tracks-rw`. |
| Долгий первый стрим | — | Render free усыпляет API; первый запрос разогревает. |
| `Connection refused` при `curl https://s3.example.com` | UFW / провайдер VPS | Проверить `ufw status`, проверить security-group у провайдера. |
| Растёт `data/` бесконтрольно | MinIO консоль → бакет → Lifecycle | Удалённые треки могут оставлять объекты (см. CLAUDE.md про hard/soft-delete). Раз в месяц — `mc rm --recursive --force` старые объекты. |

---

## 11. Безопасность — чек-лист

- [ ] `MINIO_ROOT_PASSWORD` — длинный случайный, лежит только в `.env` (chmod 600).
- [ ] Root-юзер MinIO **не** используется в API — только сервисный аккаунт.
- [ ] SSH по ключам, парольный вход отключён (`PasswordAuthentication no` в `/etc/ssh/sshd_config`).
- [ ] UFW активен, открыты только 22/80/443.
- [ ] Автообновления безопасности: `sudo dpkg-reconfigure -plow unattended-upgrades`.
- [ ] Бэкапы настроены и проверены восстановлением.

---

## 12. Что НЕ делать

- Не отдавать `wavelink-tracks` в Public. Все файлы должны идти через API
  (он проверяет авторизацию).
- Не открывать порт `9000` или `9001` наружу напрямую — Caddy единая точка
  входа с TLS.
- Не хранить root-ключ MinIO в Render — только сервисный.
- Не использовать `latest`-тег MinIO в проде без подписи на release-notes —
  бывают breaking changes между релизами консоли.
