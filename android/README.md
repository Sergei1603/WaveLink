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
Библиотека ─┬─ Треки | Артисты  (a switch inside the tab; Артисты ─ Папка исполнителя)
            ├─ Поиск            (local filter, works offline)
            ├─ Коллекции ─ Коллекция
            └─ Загрузки
Банк
Профиль ────┬─ Статистика ─ Топ-100 (Список / Диаграмма, tracks or artists)
            └─ Загрузки
Плеер        full-bleed level over any tab; the mini-player opens it
Карточка трека  bottom sheet, reachable from the player's ⓘ, from a stats row,
                or from «Инфо» on a single-row selection
```

**Selection.** Holding a row in Библиотека starts a multi-selection instead of opening the track
card — `library/Selection.kt` holds the state (`rememberSaveable`, so a rotation keeps it) and
draws the bar of actions: скачать, в коллекцию, в Telegram, удалить, plus «Инфо» at a selection of
one. Артисты selects whole folders and runs the same actions over every track inside them.
Everywhere else — the bank, the search, a collection — a hold still opens the card, because those
screens pass no `onToggleSelect` to `TrackRow`.

Renaming an artist rewrites the artist on every track in the folder **that you own**: the server
allows `PATCH /api/tracks/{id}` only to the uploader, so tracks saved out of the public bank are
counted out and reported rather than silently skipped. Bulk operations go through
`TrackRepository.deleteMany` / `setArtistMany`, which walk the library once at the end — the
single-track `delete`/`update` each call `refreshLibrary()`, and forty of those is forty full
page-walks of the library.

The artist route encodes the name as URL-safe Base64. Percent-encoding does not work here:
Navigation decodes `%2F` before it matches the route, so «AC/DC» would arrive as two path segments.

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

The debug build defaults to `http://10.0.2.2:5000/` — the emulator's alias for the host machine's
loopback, i.e. the API started with `dotnet run --project WaveLink.API`. On a physical device, type
your machine's LAN address into the **Адрес сервера** field on the sign-in screen (or **Профиль →
Адрес сервера** once signed in). A bare host is expanded to `http://…/`.

The address applies to the next request, not the next launch: Retrofit is built against a
placeholder host and `BaseUrlInterceptor` rewrites every request from the stored setting. Do not
"simplify" that back into `Retrofit.baseUrl(settings.baseUrlBlocking())` — the base URL is then
frozen at DI-graph construction, and the address typed on the sign-in screen has no effect on the
sign-in it was typed for.

Debug builds permit cleartext HTTP to any host (`src/debug/res/xml/network_security_config.xml`),
because the dev server's address differs per machine and `<domain>` cannot express a subnet.
Release builds use `src/main/res/xml/network_security_config.xml`, which permits cleartext for the
single production IP `193.222.99.254` and requires TLS everywhere else — drop that exception once
the server has a domain and a certificate.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

On Windows this fails locally with `ClassNotFoundException` for *every* test class: the repository
lives under `E:\Учеба\Проект\`, and a JVM whose `sun.jnu.encoding` is `Cp1251` hands the test worker
a classpath it cannot decode. Compilation and `assembleDebug` are unaffected. Run the tests through
an ASCII path instead — a junction is enough, no copy required:

```bash
cmd /c mklink /J C:\wl-android "E:\Учеба\Проект\WaveLink\android" && cd /d C:\wl-android && gradlew.bat :app:testDebugUnitTest
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
  `TrackRepository.refreshLibrary` walks *every* page of `GET /api/tracks` (the server caps a page
  at 200) and swaps Room once at the end — the whole library has to be mirrored, or the list, the
  search and offline shuffle would all silently stop at the first page.
- `publicbank` — the one list that is *not* mirrored: the bank belongs to everybody, so it stays a
  server-side query and grows a page at a time behind «Показать ещё». Paging asks for the next page
  of the query the rows on screen came from, which is not the same as the query in the field — that
  one runs 300 ms ahead of the request.
- `player` — `PlaybackService : MediaSessionService` owns the single ExoPlayer; `PlayerConnection`
  republishes its state to Compose. `LocalShuffle` is the offline twin of the server's shuffle.
  `WaveLinkNotificationProvider` gives the media notification a dark generated artwork: from
  Android 12 on, SystemUI tints the shade and lock-screen widget from the large icon and falls
  back to the device theme — a white panel — when a track has none. The session carries a
  `setSessionActivity` PendingIntent onto `MainActivity`; without it `DefaultMediaNotificationProvider`
  leaves the content intent null and the shade widget is dead to a tap. `MainActivity` is
  `singleTop`, so the tap returns to the running app rather than rebuilding it.
- `playtracking` — measures what was *actually heard* (merged intervals, so a seek credits
  nothing), queues one event per finished session, and flushes it with WorkManager.
  `PlayStatsRules` duplicates the server's thresholds and must stay in sync with
  `PlayStats` in `WaveLink.API/appsettings.json`.
- `downloads` — Media3 `DownloadManager` writing into a `SimpleCache` of its **own**, separate
  from the streaming one. That split is the whole point: Media3 has no pinned span — `CacheSpan`
  carries no such flag and `LeastRecentlyUsedCacheEvictor` reclaims strictly by last-touch time —
  so one shared cache lets an evening of listening eat the tracks saved for a flight, silently,
  because `DownloadManager` never re-checks the cache and goes on reporting `STATE_COMPLETED`.
  Online that only costs a re-stream; offline the track is simply dead. So: `NoOpCacheEvictor` on
  the download cache (`filesDir/media`, the historic directory — renaming it would orphan every
  track already on the phone), LRU under the user's limit on the stream cache
  (`filesDir/media-stream`), and playback reads through both — `PlaybackModule.cacheDataSourceFactory`
  chains a **read-only** `CacheDataSource` over the downloads in front of the streaming one, so
  nothing but the `DownloadManager` can write into a cache that has no evictor.
  `DownloadsRepository.reconcile` runs once per process from `MainActivity` and settles the three
  ways the two can drift while the app is dead: it rewrites the Room mirror from the download
  index, prunes the pinned cache down to what a download still claims (nothing else ever frees
  it), and re-queues any `STATE_COMPLETED` download whose bytes are no longer fully cached —
  showing «в очереди» is the honest answer, and beats pretending the track is on the phone.
- `telegram` — delivery only (`GET /api/telegram/status`, `POST /api/telegram/send`). Pairing a
  chat stays a web-and-bot job; the server answers `410` to `POST /api/telegram/link` on purpose.
  The status is cached in a singleton because the player, the track card and the profile all ask.
