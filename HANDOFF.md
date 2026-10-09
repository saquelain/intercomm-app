# RideComm handoff: where we are, how to carry on

Last updated 8 Oct 2026, after the "talk to one rider / destination / home safe / break / lock screen /
locked rides" round (second chat). Read `CLAUDE.md` first (how to build, test and work with
the owner); this file is the project's story so far and what's next.

## Starting a new chat (for the owner)

Open a new Claude Code session on `saquelain/intercomm-app`. Claude reads `CLAUDE.md` by itself; point
it at this file too. Copy one of these and fill in the blanks:

**Next feature**
> Read CLAUDE.md and HANDOFF.md. Continue RideComm from where we left off. Next I want: ____.
> Build it in the app and the web page, with a switch in Settings, then give me the APK and web links.

**Bug or change from a ride**
> Read CLAUDE.md and HANDOFF.md. On my phone (Samsung, Android __ / iPhone web), when I ____ I expected
> ____ but got ____. Screenshot attached.

**Ideas**
> Read CLAUDE.md and HANDOFF.md. What are the most useful features to add next? Give me options to pick.

Tips that worked well in the first chat:
- Attach a screenshot whenever something looks wrong; say which phone and whether it's the app or
  the web page.
- Short replies are fine: "Working fine. Next feature?", "3, 4, 6", "keep it off by default".
- Ask for "the APK link" or "the web url" any time.
- To pick up mid-feature in a new chat, say what was being built and paste Claude's last message.

## Links

- APK: https://github.com/saquelain/intercomm-app/releases/download/latest/RideComm.apk
  (`RideComm-32bit.apk` for old phones, `RideComm-unshrunk.apk` fallback). Each push makes a new build.
- Web ride for iPhone: https://saquelain.github.io/intercomm-app/ride/ (`?code=ABCDEF`)
- Invite links: `https://saquelain.github.io/intercomm-app/join/?code=ABCDEF` (app on Android, web on iPhone)
- LiveKit Cloud sandbox token server ID: `ridecomm-xnnn94` (built into the APK via the
  `LIVEKIT_TOKEN_SERVER_ID` secret; the web page uses it directly).

## What's built

Everything below is in both the Android app and the web page unless it says "app only". "Owner-tested"
means the owner reported it working on a real phone; the rest passed unit tests, screenshots and the
fake-LiveKit web tests but has not been confirmed on a ride yet.

| Feature | Where | Status |
|---|---|---|
| Group voice call over LiveKit (Opus 24 kbps, DTX, RED), auto-reconnect through dead zones | app + web | owner-tested |
| Glass UI, ride code, invite links, quick rejoin, recent rides | app (+ web join) | owner-tested |
| Shared music: DJ sends songs, every phone plays locally, ducks for voices; Spotify keeps playing ducked | app only (web just shows "X is playing") | owner-tested |
| Floating ride button over other apps (slide menu, drag onto ✕ to hide), who's-talking overlay | app only | owner-tested |
| Votes (Break / Fuel / Food) and quick messages (Slow down, Wait for me) | app + web | owner-tested |
| SOS (countdown, siren, location, I'm OK), SMS fallback without internet | app (+ web without SMS) | owner-tested |
| Crash detection → SOS countdown | app only | built, not seen in a real crash |
| Helmet headset button (1 mute, 2 music, 3 SOS) | app only | built |
| Wind noise gate with presets, fine-tune sliders and a visual before/after test | app only | owner-tested |
| Rider alerts (joined, dropped, back, low battery, on a phone call) | app + web | owner-tested |
| Voice commands ("RideComm, break / pothole / where is everyone …"), Android 13+ | app only | owner-tested |
| Speed alert, ride updates, speed/distance/time row | app only | owner-tested |
| Data saver (Off / Auto / Always), data used on screen | app only | built |
| Catch-up after a drop (only open votes and active SOS) | app + web | owner-tested |
| Phone calls don't end the ride (mic off during the call) | app only | built |
| Profile photos | app + web | owner-tested |
| Group map (off by default): live map, distances, separation alerts, regroup points with Navigate | app + web | owner-tested (screenshot from a ride) |
| My own photo on the map ("You") | app + web | build #36, new |
| Hazard alerts: mark pothole / speed breaker / slippery / police / accident / animal; "Pothole in 300 meters" | app + web | built, needs a road test |
| Lead & sweep: badges; lead/sweep alerts with Group map on | app + web | badges seen working; alerts need a road test |
| Push to talk (hold, or tap to lock; headset and floating button toggle) | app + web | built |
| Per-rider volume 20–200% and Mute for me | app (web: mute; slider not on iPhone) | built |
| Night mode Off / Auto / On (red screen, quieter voice; Auto from sunset) | app + web | built |
| Emergency info (blood group, medical, contact) with SOS + helper card with Call 112 | app + web | built |
| Talk to one rider (hold a rider; others' phones silence you; hold to reply) | app + web | built, needs a ride test |
| Shared destination (search / paste link / map long-press, Navigate, "You've reached") | app + web | built |
| Home safe check-in (button, Leave dialog, auto at saved home, later check-in, "Getting home" card) | app + web | built |
| Break reminder (Off / 1–3 h, Ask for a break vote, reset by a passed Break vote or 10-min stop) | app + web | built |
| Emergency info on the lock screen (notification, off by default) | app only (iPhone: Medical ID) | built |
| Lock rides to my group (switch, key in invite links, worker CORS for the web page) | app + web | ready; server not deployed |
| Ride history & summary (SQLite / localStorage, route map, share picture) | app + web | built |
| Ride planner (plan in the invite link, Upcoming rides, reminders, calendar, Today's plan card) | app + web | built |
| Family can watch (watch page, per-rider switch, watchers never count as riders) | app + web | built |

Chronology (git log): step 1 voice call → music → floating button → votes → SOS → glass redesign →
group tracking → Spotify ducking → crash detection, photos → invite links → private ride server →
headset button → wind gate → R8 + split APKs → rejoin → rider alerts → voice commands → visual wind test →
fine-tune + drag-to-hide → speed & trip → data saver, catch-up, phone calls → web version for iPhone →
group map + regroup → web photo/map/settings → hazards, lead & sweep, PTT, volume, night, emergency info
(app, then web) → own photo on the map → talk to one rider, shared destination, home safe, break
reminder, lock-screen emergency info, lock rides to the group (app and web together).

## Glass look (in progress)

The owner asked for a second look: **Settings → Look: Classic / Glass** (app and web). They design each
screen with ChatGPT (a mock-up image + one HTML file) and send it; Claude turns it into the Glass version of
that screen in the app and the web page, keeping every feature. Home screen came first (build #39), then the
ride screen; the owner then asked for Glass on **every** screen without sending more designs, so the rest
(Settings, rider card, pop-ups, map panels) use the same style. Mock-ups: `design/glass/home.html`,
`design/glass/ride.html`. Not Glass: overlays over other apps (floating button, speaker photos, SOS
overlay) and notifications, which are Android views. Further screen designs can still be dropped in.

How it's built:
- App: `ui/Look.kt` has `UiLook`, `LookSetting` (saved choice), `LocalLook`, `LookScope` (Glass also switches
  the type to Inter), `GlassTokens`, `GlassScene` (background) and `Modifier.frost` / `glow`. The shared
  components in `Glass.kt` (GlassCard, PrimaryButton, GlassButton, GlassIconButton, GlassTextField,
  SectionLabel, GlassDialog, and `Modifier.glass` itself) draw Glass when `LocalLook` is Glass; `ActionRow`
  is the Glass row with an icon tile and an arrow. `MainActivity` wraps both screens in `LookScope(look)`;
  the background is `GlassScene` with `GlassSceneStyle.HOME` or `RIDE`. Cards paint a softened copy of the
  scene (`frostedBackdrop`) instead of a real blur; dialogs don't (separate window).
- Web: `html.look-glass` (from `SET.look`) plus `html.in-ride` while riding (ride background); all Glass
  CSS is under `html.look-glass`.

## Soft look (neumorphism)

Third look, asked for after Glass: Settings → Look → **Soft** (app + web). Light: pale lavender page with
pastel corners, raised cards/buttons (white shadow top-left, lavender-grey bottom-right), pressed-in text
fields, navy text, blue primary actions and mic. References: `design/soft/ride.jpg` (the owner's screenshot)
and `design/soft/neukit.html` (NeuKit tokens). How it's built:
- `ui/Neu.kt`: `NeuTokens`, `neuTypography`, `Modifier.neuRaised` / `neuInset` (blurred shadows, Android 9+;
  a faint flat shadow below that), `NeuScene`, `lookSliderColors()` / `lookSwitchColors()`.
- `Palette` text and accent colours are now `@Composable` getters that return dark/darker versions on Soft
  (`Palette.XxxBright` are the fixed bright ones, for places outside a look). `Palette.OnSurface` and
  `LocalCardInk` give the right icon/text colour on a card; `Palette.Accent` is a selected choice.
- Components in `Glass.kt` branch on `UiLook.NEU`; ride-screen parts (stats tiles, dock, mic, SOS) too.
- Web: `html.look-neu` CSS block (variables `--neu-out`, `--neu-in`…).

## Google Maps for the Group map

The owner found the OpenStreetMap Group map didn't look good and chose real Google Maps (app + web), with
Settings → **Map**: Google Maps / OpenStreetMap. OpenStreetMap stays as the fallback, so nothing breaks
without a key.
- App: `ui/map/GoogleGroupMap.kt` (`GoogleLiveMap`, Play services `MapView` 20.0.0 in an `AndroidView`;
  maps-compose needs Kotlin 2.4, too new for this project). Markers are the same pictures as on
  OpenStreetMap: `MarkerPainter.icon()` draws one marker into a bitmap (`extent()` sizes it,
  `MarkerIconTest` checks nothing is cut off). The map is padded by the top bar and bottom panel so
  Google's logo shows; dragging stops "Follow me"; tap a marker to centre it. Dark = `res/raw/map_night.json`
  (a style JSON, so no map ID is needed and Android maps stay free).
- Key: GitHub secret `GOOGLE_MAPS_API_KEY` → `BuildConfig.HAS_GOOGLE_MAPS` and the manifest's
  `com.google.android.geo.API_KEY`. `GoogleMapSetup.use()` also needs Google Play services on the phone.
  Restrict the key in Google Cloud to Android apps, package `com.ridecomm.app`, SHA-1
  `52:A7:1F:7B:2B:E0:C6:C3:42:8E:89:1A:D2:A6:11:40:54:7A:D6:43` (the committed test keystore), API "Maps
  SDK for Android". A wrong key shows a blank grey map: the rider can switch to OpenStreetMap in Settings.
- Web: `GMAPS_KEY` near the top of the page's script (a *separate* key restricted to websites
  `https://saquelain.github.io/*`, API "Maps JavaScript API"; it is public by design). A small "engine"
  (`leafletEngine` / `googleEngine`) hides which map is showing; Google markers are HTML `OverlayView`s
  with the same `.mk` / `.pin` / `.mkhz` HTML, long press is timed by the page, and the map ends above the
  bottom panel so Google's logo shows. If Google refuses the key (`gm_authFailure`) the page switches to
  OpenStreetMap and says so. Test: `tools/webtest/gmap.test.js` (Google's real map in keyless mode).
- Keys are in (Oct 2026): the owner added `GOOGLE_MAPS_API_KEY` (Android) to the repo secrets, and the web key
  is in the page (checked: Google accepts it at the github.io address and refuses it elsewhere).
- Cost: Android maps are free; the web map has a free monthly allowance (cap it with a quota in Google
  Cloud). Place search still uses OpenStreetMap (Nominatim), Navigate still opens the Google Maps app.

## Ride history, planner, family watching (Oct 2026)

The owner asked where data would live: **only on each phone** (their choice over a cloud database), so:
- **History** `trip/RideLog.kt` (pure: `RouteRecorder` keeps a point every 25 m and finds stops,
  Google polyline codec), `trip/RideHistory.kt` (SQLite `rides.db` via `SQLiteOpenHelper`, no new
  dependency; also records the ride in progress, saving every minute). `RideManager` calls
  `begin`/`finish`, `TripTracker` feeds fixes. UI: `ui/RideHistoryUi.kt` (home card, list, summary page),
  `ui/map/RouteMap.kt` (Google lite map or osmdroid, both still), `ui/RidePicture.kt` (share image, shared
  through a `FileProvider`, authority `<appId>.files`).
- **Planner** `plan/RidePlan.kt` (format + rules, see PROTOCOL.md "Ride plans"), `plan/RidePlans.kt`
  (kept in Prefs, AlarmManager reminders via `RidePlans$Reminder`, re-set after reboot, calendar intent),
  `ui/PlannerUi.kt`. `InviteLink.url(code, key, plan)` puts `p=` and `k=` after "#"; the app link is
  `ridecomm://join/CODE?p=…&k=…`.
- **Family** `ride/Riders.kt`: `isRider` / `isWatcher` / `privateAudience`. Every place that used to list
  remote participants now uses `room.riderIds()`. Positions (`GroupTracker.sendMyPosition`) and SOS
  (`SosManager.broadcast`) go only to riders unless "Family can watch" is on. Web: `docs/watch/index.html`.
- `SmallScreenTest` covers the new screens too (320 dp, text at 130%).

## Ideas not built yet (from the last "what next" list)

- **Fuel range reminder**: set the bike's range; warn at 80% and offer a Fuel vote.
- **Rider chat log**: scrollable history of the ride's messages, votes and alerts.
- **Private ride server**: everything is ready (app switch, web settings, keys in invite links, CORS); the
  owner still has to add the Cloudflare/LiveKit secrets, run "Deploy ride server", put the address in
  `RIDE_SERVER_URL` (APK) and `RIDE_SERVER` (web page), and switch off the LiveKit sandbox token server.
  Important before sharing the app widely: the sandbox token server lets anyone with the ID join.
- Smaller: a "Hazard" option on the floating button, voice commands on the web page, emergency info on
  the lock screen, night mode for the floating overlays, Play Store release (needs a signing setup and
  privacy policy).

Ideas looked at and dropped: Bluetooth or Wi-Fi Direct voice instead of the internet (10–100 m range,
phones can't do a mesh group call; voice is only ~10 MB an hour anyway).

## Known limits

- iPhone: Safari pauses the mic and location when the screen locks or another app is in front; keep the
  page open ("Keep screen on" is on by default). Safari can't change one rider's volume (mute works).
- The sandbox token server is open to anyone with its ID (see Private ride server above).
- SOS by SMS opens Messages with the text ready; the rider taps Send (silent SMS needs SEND_SMS, which
  Play Protect blocks for sideloaded apps).
- The web page only follows location for hazards after someone marks one (to avoid a location prompt
  for everyone), and only on the phone itself.
- Talk to one rider is enforced by the listeners' phones (they silence the talker), not by the server: fine
  for a group of friends, not a secret channel. Riders on an old app version still hear everything.
- Nothing can be tested against real LiveKit from the cloud container; every feature is tested with unit
  tests, screenshots and the fake-LiveKit web tests (`tools/webtest/`), then by the owner on a ride.

## Setting up a fresh cloud container

1. Gradle mirror (Maven Central rate-limits the container): create
   `~/.gradle/init.d/mirror.init.gradle.kts` with
   ```kotlin
   // Local-only: route Maven Central through Google's mirror (repo.maven.apache.org rate-limits this container).
   val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
   beforeSettings {
       pluginManagement.repositories { maven(mirror) }
       dependencyResolutionManagement.repositories { maven(mirror) }
   }
   allprojects {
       buildscript.repositories { maven(mirror) }
   }
   ```
2. Android SDK: a fresh container may have none. Install command-line tools into `~/android-sdk`
   (`commandlinetools-linux-*_latest.zip` from dl.google.com), then
   `sdkmanager "platforms;android-36" "platform-tools" "build-tools;35.0.0"` and write
   `sdk.dir=$HOME/android-sdk` to `local.properties` (gitignored).
3. `./gradlew testDebugUnitTest` should pass (about 170 tests).
4. Web tests: see `tools/webtest/README.md`.
5. GitHub access goes through the GitHub MCP tools (no `gh` CLI in the container).
