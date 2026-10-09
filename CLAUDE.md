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
  LiveKit JS 2.22.3 from jsdelivr, Google Maps JS with Leaflet 1.9.4 as the fallback), served by GitHub Pages. iPhones can't
  install the APK, so this is how iPhone riders join. It must stay compatible with the app.
- **Invite page** (`docs/join/index.html`): opens the app on Android, the web ride on iPhone, shows a ride plan.
- **Family watch page** (`docs/watch/index.html`): family at home follow the ride on a map (no audio).
- **Privacy policy** (`docs/privacy/index.html`) for the Play Store; Play steps in `PLAY_STORE.md`.
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

- Development is on the newest `ccr-…` branch (see HANDOFF.md "Where things stand").
  GitHub Pages serves `docs/` from `claude/laughing-fermat-oevz8c`, so iPhone riders, invite, family and
  privacy pages only change when that branch is fast-forwarded to the work branch
  (`git push origin HEAD:claude/laughing-fermat-oevz8c`), and **only when the owner says "publish the
  web"**. A new session may be given a different branch: start it from the branch with the newest commit.
- Pushes also build the APK; pushing the Pages branch builds one too (same code).
- Never open a pull request unless asked.
- Commit messages: a short title, a plain-language body, then the attribution lines the session asks for.
- Every push runs `.github/workflows/build-apk.yml`: `testReleaseUnitTest assembleRelease assembleUnshrunk`,
  then replaces the `latest` pre-release with the three APKs. A failing unit test means no new APK.
- `LIVEKIT_TOKEN_SERVER_ID` / `RIDE_SERVER_URL` / `GOOGLE_MAPS_API_KEY` come from repo secrets; `versionCode` is the run number.
  With the `UPLOAD_KEYSTORE_*` secrets, CI also builds the Play bundle (`bundlePlay`) as a run artifact.
- The web page's Google key (`GMAPS_KEY` in `docs/ride/index.html` and `docs/watch/index.html`) is public by
  design and only works on `saquelain.github.io`; tests blank it out via a route.
- Big jobs went faster with a background helper agent doing the web side (docs/ + tools/webtest only)
  from a written spec in PROTOCOL.md while the main session did `app/`; review and rerun its tests.

## Build, test, look (in the cloud container)

- Maven Central rate-limits the container: `~/.gradle/init.d/mirror.init.gradle.kts` must route through
  `https://maven-central.storage-download.googleapis.com/maven2/` (recreate it in a new container; the
  content is in HANDOFF.md).
- Compile: `./gradlew compileDebugKotlin`
- Unit tests (about 320, all must pass): `./gradlew testDebugUnitTest` (Robolectric runs them on SDK 35,
  see `app/src/test/resources/robolectric.properties`; the app targets 36)
- Same as CI: `./gradlew testReleaseUnitTest assembleRelease`
- Lint: `./gradlew lintDebug`
- Screenshots of screens (Robolectric + Roborazzi, real OSM tiles from `app/src/test/resources/maptiles`):
  `./gradlew recordRoborazziDebug --tests '*ScreenshotTest*'` → `app/screenshots/*.png` (gitignored).
  Add a test in `ScreenshotTest.kt` for every new screen and look at the PNG before pushing.
  `SmallScreenTest` re-renders all of them on a 320 dp phone with text at 130% (`app/screenshots/small/`):
  the owner's phone uses big text, so check those too. Labels that may not fit use `FitText` (shrinks,
  never splits a word), pairs of buttons use `ButtonRow` (stacks them when narrow).
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
  `trip/BreakReminder`, `sos/LockScreenInfo`, `trip/RideHistory` + `RideLog` (history on the phone),
  `plan/` (ride planner), `ride/Riders` (who is a rider: family watchers `watch-…` and check-ins `…~home`
  are not; use `room.riderIds()`, never raw `remoteParticipants`), `nearby/` (fuel & food ahead, low fuel),
  `weather/` (rain alerts), `score/` (points & badges), `garage/` (my garage). Play Store: `PLAY_STORE.md`, build type `play`, `design/play/`.
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
