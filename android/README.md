# WaveLink Android

Kotlin + Jetpack Compose + Media3 client for WaveLink. Feature parity with the web client
**except** uploading audio from the phone and Telegram linking — both stay web-only.

What it does: sign in, browse your library and the public bank, manage collections, stream with
background playback and a media notification, cache and download tracks for offline listening,
report listening statistics, and shuffle in two modes (plain и «Открыть новое», which favours
what you have played least).

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
loopback, i.e. the API started with `dotnet run --project WaveLink.API`. On a physical device,
open **Настройки** in the app and enter your machine's LAN address instead; the change takes
effect after an app restart.

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
  credential) and `SettingsStore` (base URL, cache size).
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
  bytes. That is why the settings screen shows the cache limit as applying to streaming only.
