# WaveLink Android

Kotlin + Jetpack Compose + Media3 client for WaveLink. Feature parity with the web client
**except** uploading audio from the phone and Telegram linking — both stay web-only.

What it does: sign in, browse your library and the public bank, manage collections, stream with
background playback and a media notification, cache and download tracks for offline listening,
report listening statistics, and shuffle in two modes («Перемешивание» и «Умное перемешивание»,
which favours what you have played least).

## Screens

The UI follows the Claude Design spec *WaveLink Mobile* (project `bfdf725a`), built on the
**Nocturne** design system. Its tokens live in [`ui/theme/Theme.kt`](app/src/main/java/ru/wavelink/app/ui/theme/Theme.kt)
and its component classes in [`ui/components/Nocturne.kt`](app/src/main/java/ru/wavelink/app/ui/components/Nocturne.kt);
screens compose those rather than restating colours and paddings. The mock is drawn at 412 × 892,
which is Compose's dp grid on a 412dp phone, so its px values are used verbatim as dp/sp.

Navigation is **three tabs**, with everything else as a level inside one of them:

```
Библиотека ─┬─ Поиск            (local filter, works offline)
            ├─ Коллекции ─ Коллекция
            └─ Загрузки
Банк
Профиль ────┬─ Статистика ─ Топ-100 (Список / Диаграмма, tracks or artists)
            └─ Загрузки
Плеер        full-bleed level over any tab; the mini-player opens it
Карточка трека  bottom sheet, reachable from the player's ⓘ or by holding a row
```

Two deliberate departures from the mock, both because the mock had no answer:

- **Entry points into Коллекции and Загрузки.** Screen 02 draws neither, so the library header
  carries two small accent links.
- **The server address before sign-in.** Screen 01 says «Адрес сервера — в настройках», but
  settings live behind the login. That line is tappable and opens the same editor, otherwise the
  first sign-in on a physical device can never succeed.

The photographic grounds in `res/drawable-nodpi/` are the design's own assets, and every
`WlBackdrop` call passes the mock's own `opacity` / `saturate` / `brightness` / `object-position`
figures verbatim (`object-position: 50% N%` becomes a vertical bias of `2N − 1`).

Two of the four exceed the design API's 256 KiB per-file read and came back truncated, so
`backdrop_bank` and `backdrop_player` were supplied out of band. **A truncated image file is not a
cosmetic problem here:** `painterResource` throws `NullPointerException: null cannot be cast to
BitmapDrawable` when the decoder gives up, and that takes the whole activity down. If you swap a
backdrop, check the file actually ends — `FFD9` for JPEG, a final RIFF chunk landing exactly on
EOF for WebP.

The track card (screen 12) is a real `ModalBottomSheet`, so what shows through behind it is the
screen you opened it from rather than the mock's dimmed copy of `backdrop.jpg`.

## Requirements

- JDK 17
- Android SDK with platform 35 and build-tools 35.0.0
- An emulator or device on API 26+

`local.properties` must point at your SDK:

```
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

## Build and run

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:installDebug
```

The debug build talks to `http://10.0.2.2:5000/` — the emulator's alias for the host machine's
loopback, i.e. the API started with `dotnet run --project WaveLink.API`. On a physical device, set
your machine's LAN address instead: tap «настройках» on the login screen, or **Профиль → Адрес
сервера** once signed in. Either way the change takes effect after an app restart.

Cleartext HTTP is permitted only for `10.0.2.2`, `localhost` and private LAN ranges, and only in
debug builds (`src/debug/res/xml/network_security_config.xml`).

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

Covers the parts that are easy to get subtly wrong: interval coverage (`CoverageSetTest`), the
significance rule (`PlayStatsRulesTest`), and the discover-shuffle distribution
(`LocalShuffleTest`, which asserts the weights actually match `w = 1/(plays+1)^0.7`).

> **Non-ASCII paths.** This repository lives under `E:\Учеба\Проект\WaveLink`. AGP refuses such
> paths by default; `android.overridePathCheck=true` in `gradle.properties` lets the APK build,
> and that works fine. **Unit tests do not** — Gradle's test worker fails to load the compiled
> classes and every test reports `ClassNotFoundException`. Run them from CI, or from a clone in an
> ASCII path. Everything else (assemble, install, lint) is unaffected.

## How it fits together

- `core/net` — Retrofit + OkHttp. One client for both the API and audio streaming, so the
  `401 → refresh → retry once` flow covers playback too. Auth endpoints carry an `X-WaveLink-No-Auth`
  marker so the authenticator cannot recurse into them.
- `core/prefs` — `TokenStore` (EncryptedSharedPreferences; the refresh token is a long-lived
  credential) and `SettingsStore` (base URL, cache size, Wi-Fi-only downloads, background
  playback). The last two are not decoration: the Wi-Fi switch sets the `DownloadManager`'s
  `Requirements`, and the background switch is what `PlaybackService.onTaskRemoved` reads before
  deciding whether to keep playing after the app is swiped away.
- `core/db` — Room mirror of the library plus the outbox of unsent play events. Screens read
  from Room, never from the network directly, so the app opens fully usable while offline.
- `player` — `PlaybackService : MediaSessionService` owns the single ExoPlayer; `PlayerConnection`
  republishes its state to Compose. `LocalShuffle` is the offline twin of the server's shuffle.
- `playtracking` — measures what was *actually heard* (merged intervals, so a seek credits
  nothing), queues one event per finished session, and flushes it with WorkManager.
  `PlayStatsRules` duplicates the server's thresholds and must stay in sync with
  `PlayStats` in `WaveLink.API/appsettings.json`.
- `downloads` — Media3 `DownloadManager` writing into the *same* `SimpleCache` as streaming.
  Downloaded spans are pinned, so the LRU evictor only ever reclaims opportunistically cached
  bytes. That is why the downloads screen shows the cache limit as applying to streaming only,
  and why «Очистить кэш стриминга» removes every cache key *except* the ones a download owns.
- `telegram` — delivery only (`GET /api/telegram/status`, `POST /api/telegram/send`). Pairing a
  chat stays a web-and-bot job; the server answers `410` to `POST /api/telegram/link` on purpose.
  The status is cached in a singleton because the player, the track card and the profile all ask.
