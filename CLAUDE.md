# RideComm: notes for Claude

Read this first, then `HANDOFF.md` (where things stand, what's next) and `PROTOCOL.md` (the messages
phones send each other). `README.md` describes every feature in detail.

## What this is

RideComm is a group intercom for bike riders (4–6 riders on a ride), built and tested step by step with
the owner, who rides with it on real phones and reports back.

- **Android app** (`app/`): Kotlin, Jetpack Compose, LiveKit Android SDK 2.29.0, Media3, Google Maps (Play services) with osmdroid
  as the fallback.
  minSdk 26, target 35. Package `com.ridecomm.app`.
- **iPhone / browser version** (`docs/ride/index.html`): one self-contained page (HTML + CSS + vanilla JS,
  LiveKit JS 2.22.3 from jsdelivr, Leaflet 1.9.4 from cdnjs), served by GitHub Pages. iPhones can't
  install the APK, so this is how iPhone riders join. It must stay compatible with the app.
- **Invite page** (`docs/join/index.html`): opens the app on Android, the web ride on iPhone.
- **Private ride server** (`server/token-worker/`, Cloudflare Worker): written but not deployed. Rides
  currently use the LiveKit Cloud *sandbox* token server `ridecomm-xnnn94` (anyone with the ID can join).

Links the owner uses:
- APK (always the newest build): https://github.com/saquelain/intercomm-app/releases/download/latest/RideComm.apk
  (also `RideComm-32bit.apk` for old phones, `RideComm-unshrunk.apk` as a fallback without R8)
- Release page: https://github.com/saquelain/intercomm-app/releases/tag/latest
- Web ride (iPhone): https://saquelain.github.io/intercomm-app/ride/ (add `?code=ABCDEF` to prefill a ride)

## Working with the owner

- They test every change on a real Android phone (and iPhone via the web page) and come back with short,
  informal feedback, often with a screenshot ("Working fine. Next feature?", "the dot should show my
  photo"). Typos are normal; read for intent.
- When they ask "what next?" or "any more features?", offer a short menu of options with
  AskUserQuestion (multi-select), with the recommended one first and a one-line "why" each. They like
  picking several at once and sometimes answer "Other: …" with extra wishes.
- Every new feature gets **its own on/off switch in Settings**, in the app and on the web. Anything that
  shares location is **off by default** ("keep it in setting turned off, I want it to go live polished").
- Build the app *and* the web version of a feature together, with the same messages (see PROTOCOL.md).
- Reply in plain language: what changed, what they'll see, the APK link and/or web link, what was and
  wasn't tested (nothing is tested on a real phone from here: say so). No jargon walls.
- After pushing, wait for the CI release (build number goes up by one per push) and only then say it's
  ready. Check with:
  `until curl -sS https://github.com/saquelain/intercomm-app/releases/tag/latest | grep -q "build #N"; do sleep 15; done`
  (run it in the background).
- Be honest about limits (e.g. iPhone Safari can't change one rider's volume, so web offers mute only).

## Git and CI

- All work so far is on the branch `claude/laughing-fermat-oevz8c` (the repo's only branch; GitHub Pages
  serves its `docs/` folder). A new session may be given a different branch: start it from this one, and
  remember Pages only updates from `claude/laughing-fermat-oevz8c`, so web changes on another branch must
  be merged there (or the Pages source changed) before iPhone riders see them.
- Never open a pull request unless asked.
- Commit messages: a short title, a plain-language body, then the attribution lines the session asks for.
- Every push runs `.github/workflows/build-apk.yml`: `testReleaseUnitTest assembleRelease assembleUnshrunk`,
  then replaces the `latest` pre-release with the three APKs. A failing unit test means no new APK.
- `LIVEKIT_TOKEN_SERVER_ID` / `RIDE_SERVER_URL` / `GOOGLE_MAPS_API_KEY` come from repo secrets; `versionCode` is the run number.

## Build, test, look (in the cloud container)

- Maven Central rate-limits the container: `~/.gradle/init.d/mirror.init.gradle.kts` must route through
  `https://maven-central.storage-download.googleapis.com/maven2/` (recreate it in a new container; the
  content is in HANDOFF.md).
- Compile: `./gradlew compileDebugKotlin`
- Unit tests (about 170, all must pass): `./gradlew testDebugUnitTest`
- Same as CI: `./gradlew testReleaseUnitTest assembleRelease`
- Lint: `./gradlew lintDebug`
- Screenshots of screens (Robolectric + Roborazzi, real OSM tiles from `app/src/test/resources/maptiles`):
  `./gradlew recordRoborazziDebug --tests '*ScreenshotTest*'` → `app/screenshots/*.png` (gitignored).
  Add a test in `ScreenshotTest.kt` for every new screen and look at the PNG before pushing.
- Tests that need `org.json` must run with `@RunWith(RobolectricTestRunner::class)`.
- Web page: serve `docs/` with `python3 -m http.server 8765` and drive it with Playwright
  (`/opt/node22/lib/node_modules/playwright`, launch Chromium with
  `--proxy-server=https=$HTTPS_PROXY` so localhost isn't proxied). Real LiveKit can't connect from the
  container (WSS blocked), so tests swap in a fake LiveKit over BroadcastChannel; see HANDOFF.md for the
  harness. Syntax-check the page's script with `node --check`.
- Don't use `pkill -f` (it kills its own shell); use `fuser -k 8765/tcp`.

## Code map (Android, `app/src/main/java/com/ridecomm/app/`)

- `ride/RideManager.kt`: owns the LiveKit room, connect/retry loop, mute, push-to-talk, catch-up after a
  drop, voice bitrate (data saver). Every feature manager is attached/detached/released here.
- `ride/RideService.kt`: foreground service, notification, floating button and overlays.
- `Prefs.kt`: every setting (SharedPreferences). `ui/HomeScreen.kt` → `SettingsDialog` shows them.
- `ui/RideScreen.kt` (`RideContent` takes plain state so screenshots can render any situation),
  `ui/Glass.kt` (shared glass components), `ui/Theme.kt` (`Palette`), `ui/NightFilter.kt`,
  `ui/RideExtras.kt` (destination, break, home safe cards), `ui/WhisperUi.kt`, `ui/Look.kt` (Classic / Glass
  look: see "Glass look" in HANDOFF.md; screens opt in with `LookScope`, mock-ups in `design/glass/`),
  `ui/Neu.kt` (the Soft / neumorphism look; `Palette` colours depend on the look, see HANDOFF.md).
- Features: `audio/` (wind noise gate, MicGate), `vote/`, `sos/` (SOS, SMS fallback, emergency info),
  `music/` (shared songs), `group/` (positions, map logic, regroup, lead & sweep), `ui/map/` (Google Maps in
  `GoogleGroupMap.kt` when the build has a key, else the osmdroid map; MarkerPainter), `hazard/`, `trip/` (speed, distance), `alerts/` (joins, batteries, calls),
  `voice/` (voice commands), `headset/`, `crash/`, `night/`, `overlay/` (floating button), `profile/`,
  `whisper/` (talk to one rider), `home/` (home safe), `group/RideDestination` (shared destination),
  `trip/BreakReminder`, `sos/LockScreenInfo`.
- Pattern: pure logic in a plain-Kotlin class/object (unit-tested, e.g. `HazardLogic`, `RoleWatch`,
  `SunTimes`, `TalkButton`), and a manager object with `attach(room)` / `detach()` / `release()` /
  `requestSync()` / `onRiderJoined()` that sends JSON over a LiveKit text-stream topic.
- Spoken output goes through `Announcer.speak` (quieter in night mode). Icons are Material Symbols
  Rounded filled vectors in `res/drawable/ms_*.xml` (paths from
  `https://cdn.jsdelivr.net/npm/@material-symbols/svg-400@0.23.0/rounded/<name>-fill.svg`).

## Style

Match the surrounding code: short KDoc on each class/function saying *why*, plain-English UI text
written for riders (no jargon), no new dependencies without a good reason, keep the web page a single
file. The web page mirrors app names (`HAZ`, `ROLES`, `G` for group map state, `S` for ride state,
`SET` for settings).
