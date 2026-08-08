# Удалённый доступ к БД и файловому хранилищу на VPS

Postgres и MinIO наружу **не публикуются** — они слушают только `127.0.0.1` на самом
сервере и доступны внутри docker-сети `wavelink`. Подключение с локальной машины идёт
через SSH-туннель: SSH пробрасывает локальный порт на loopback-интерфейс VPS.

```
Локальная машина                    VPS
  DBeaver ──▶ localhost:15432 ══SSH══▶ 127.0.0.1:5432 ──▶ wavelink-postgres
  Браузер ──▶ localhost:9001  ══SSH══▶ 127.0.0.1:9001 ──▶ wavelink-minio (консоль)
  mc CLI  ──▶ localhost:9000  ══SSH══▶ 127.0.0.1:9000 ──▶ wavelink-minio (S3 API)
```

Открывать эти порты наружу (`0.0.0.0`) нельзя: Postgres с паролем из `.env.prod` и MinIO
с root-ключами сканеры находят за считанные часы.

---

## 1. Публикация портов на сервере (делается один раз)

В `docker-compose.deploy.yml` на **VPS** у сервиса `postgres` должно быть:

```yaml
    ports:
      - "127.0.0.1:5432:5432"
```

Префикс `127.0.0.1:` обязателен — без него порт откроется на все интерфейсы.
У `minio` консоль (`127.0.0.1:9001:9001`) проброшена изначально; если нужен ещё и S3 API
(для `mc`, скриптов, бэкапов), добавь туда же `"127.0.0.1:9000:9000"`.

После правки пересоздай контейнер:

```bash
docker compose -f docker-compose.deploy.yml --env-file .env.prod up -d postgres
```

`restart` не подойдёт — публикация портов задаётся при создании контейнера, нужен именно
`up -d`. Данные в volume `postgres_data` при этом сохраняются.

Проверка, что порт слушается:

```bash
ss -ltnp | grep -E '5432|9000|9001'
```

Должны быть адреса вида `127.0.0.1:5432`, а не `0.0.0.0:5432`.

> **Важно.** Deploy-job в `.github/workflows/build-images.yml` не копирует compose-файл на
> сервер — он только тянет новые образы и делает `up -d` из файла, который уже лежит в
> `$SERVER_PATH`. Правка `docker-compose.deploy.yml` в репозитории сама по себе на VPS не
> попадает: файл на сервере надо обновлять отдельно. Расхождение этих двух копий — самая
> частая причина «всё настроил, а не подключается».

---

## 2. Поднять туннель

Все три порта одной командой (`-N` — только туннель, без shell):

```bash
ssh -N -L 15432:localhost:5432 -L 9000:localhost:9000 -L 9001:localhost:9001 user@ВАШ_VPS
```

Терминал с этой командой должен оставаться открытым — туннель живёт, пока живёт SSH-сессия.
Только БД:

```bash
ssh -N -L 15432:localhost:5432 user@ВАШ_VPS
```

Локальный порт выбран `15432`, чтобы не конфликтовать с локальным Postgres из
`docker-compose.yml` (дев-стек занимает `5432`).

Если сервер слушает SSH на нестандартном порту, добавь `-p <порт>`.

---

## 3. Подключение к Postgres

Туннель поднят → подключайся к `localhost:15432` любым клиентом (DBeaver, pgAdmin, psql).

| Параметр | Значение |
| --- | --- |
| Host | `localhost` |
| Port | `15432` |
| Database | `POSTGRES_DB` из `.env.prod` |
| User | `POSTGRES_USER` из `.env.prod` |
| Password | `POSTGRES_PASSWORD` из `.env.prod` |
| SSL | не нужен (трафик уже шифрует SSH) |

Через psql:

```bash
psql -h localhost -p 15432 -U wavelink -d wavelink
```

> Альтернатива без туннеля — работать прямо на сервере:
> `docker compose -f docker-compose.deploy.yml exec postgres psql -U wavelink -d wavelink`

---

## 4. Подключение к MinIO

### Веб-консоль

При поднятом туннеле открой `http://localhost:9001`.
Логин — `MINIO_ROOT_USER`, пароль — `MINIO_ROOT_PASSWORD` из `.env.prod`.
Файлы треков лежат в бакете `wavelink-tracks`, ключ объекта — `{userId}/{trackId}/{имя файла}`.

### S3 API через mc CLI

Требует проброшенного порта 9000 (см. раздел 1).

```bash
mc alias set wavelink-prod http://localhost:9000 MINIO_ROOT_USER MINIO_ROOT_PASSWORD
mc ls wavelink-prod/wavelink-tracks
mc du wavelink-prod/wavelink-tracks          # сколько занято
mc cp wavelink-prod/wavelink-tracks/<key> .  # скачать объект
```

Любой S3-совместимый клиент тоже подойдёт: endpoint `http://localhost:9000`,
access/secret key — те же root-креды, path-style addressing, регион любой.

---

## 5. Если не подключается

| Симптом | Причина и что делать |
| --- | --- |
| `Connection reset` / `Connection refused` в клиенте БД | На VPS никто не слушает целевой порт. Проверь `ss -ltnp \| grep 5432`; если пусто — порт не опубликован (раздел 1) или контейнер не пересоздан |
| `channel 2: open failed: connect failed` в выводе ssh | То же самое: SSH дошёл до сервера, но подключиться к `127.0.0.1:5432` там не смог |
| Туннель поднимается, но сразу закрывается | На сервере `AllowTcpForwarding no`. Проверь `sudo sshd -T \| grep -i allowtcpforwarding`, при `no` — поменяй в `/etc/ssh/sshd_config` и `sudo systemctl reload sshd` |
| `bind: Address already in use` | Локальный порт занят (часто дев-Postgres на 5432). Возьми другой левый порт: `-L 15433:localhost:5432` |
| Порт слушается, пароль не подходит | Смотри `.env.prod` **на сервере** — креды могли разойтись с локальными |
| Консоль MinIO не открывается | Проверь `docker compose -f docker-compose.deploy.yml ps` — контейнер `wavelink-minio` должен быть healthy |

Полезные команды на сервере:

```bash
docker compose -f docker-compose.deploy.yml ps          # статус и колонка PORTS
docker compose -f docker-compose.deploy.yml logs postgres
docker compose -f docker-compose.deploy.yml logs minio
```

---

## 6. Безопасность

- Порты биндятся только на `127.0.0.1`. Если в `ss -ltnp` видишь `0.0.0.0:5432` — это дыра,
  правь `ports` в compose и пересоздавай контейнер.
- UFW должен пропускать только 22, 80, 443 (см. `DEPLOY_VPS.md`). Он не заменяет правильный
  биндинг: docker-proxy пишет правила в iptables в обход UFW, поэтому `0.0.0.0`-публикация
  окажется доступна снаружи даже при закрытом файрволе.
- Не храни прод-креды в клиентах на общих машинах; для рутинных задач лучше завести
  отдельного пользователя БД с ограниченными правами вместо root/владельца.
- После работы закрывай туннель (Ctrl+C в терминале с `ssh -N`).
