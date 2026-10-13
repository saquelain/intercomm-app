# RideComm handoff: where we are, how to carry on

Last updated 13 Oct 2026, at **build #59**, end of the fourth chat (web published, Play Store steps explained,
Points & badges, My garage, Ride Wrapped, a planning skill for Claude). Third chat: looks, Google Maps, small
screens, ride history, planner, family watching, fuel & food finder, low fuel, rain alerts, Play Store groundwork.
Read `CLAUDE.md` first (how to build, test and work with the owner); this file is the project's story
so far and what's next.

## Where things stand right now (read this first)

- **Branches:** development continues on the newest `ccr-…` branch a chat is given (fourth chat:
  `ccr-fd3621d0-ybje5a`, started from `ccr-3351f022-4fqzov`). Start a new chat from whichever branch has
  the newest commit (`git log --all --oneline | head`). GitHub Pages serves `claude/laughing-fermat-oevz8c`,
  last fast-forwarded to `94116d7` on 9 Oct (owner said "publish the web"): the finder, low fuel, rain
  alerts, Points & badges and the privacy page are live. To publish later web changes, only when the owner
  says so: `git push origin HEAD:claude/laughing-fermat-oevz8c` (check it's a fast-forward first).
- **Web not yet published:** My garage, Ride Wrapped and Settings in groups on the iPhone page, the privacy
  page's new lines about points and the garage, and the hazard points rule. They go live with the next
  "publish the web".
- **Waiting on the owner** (nothing to build until they do):
  1. *Google Places for the finder and place search:* enable **Places API (New)** in Google Cloud and tick it on
     both keys' API restrictions. Until then both use OpenStreetMap automatically (the owner noticed place search
     finds less than Google Maps on 13 Oct).
  2. *Private ride server:* Cloudflare account + 6 secrets, then run "Deploy ride server" and send the
     address (steps in README "Private ride server"). Then set `RIDE_SERVER_URL` (secret) and
     `RIDE_SERVER` in `docs/ride/index.html` (also used by the watch page), and turn off the sandbox.
  3. *Play Store:* developer account, upload key secrets, closed test with 12 testers for 14 days
     (all in `PLAY_STORE.md`). On 9 Oct the owner got the steps in plain words and said "leave the key for
     now": Claude can generate the upload key file and the 4 secret values when they ask ("make the key").
- **Fourth chat, in order:** published the web (finder, low fuel, rain) → Play Store steps → Points & badges
  (build #55, web published) → My garage + Ride Wrapped (build #58, web not published) → the
  `claude-delegate-and-budget` skill (build #59, no app change) → owner's feedback from a real ride (13 Oct):
  Settings in groups, the ride summary map fixed, the points bug fixed (see below).
- **Owner's last feedback:** Google Maps group map "working fine". Nothing reported yet from a real ride on
  small-screen fixes, history, planner, family, finder, rain, points, garage or Wrapped. The owner's phone
  uses big text: check `app/screenshots/small/` for every new screen.
- **How the owner picks features:** they skipped two menus of safety/map ideas with "Something else" and picked
  from a "something different" menu (fun, bike care). Offer fresh kinds of ideas, not the same list again.
  Offered and not picked yet: see "Ideas not built yet".

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
| Crash detection → SOS countdown | app only | built, not seen in a real crash; Settings → Safety → Test crash detection |
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
| Family can watch (watch page, per-rider switch, watchers never count as riders) | app + web | built (page published) |
| Looks: Classic / Glass / Soft (Settings → Look) on every screen | app + web | owner-tested Glass |
| Google Maps group map (Settings → Map: Google / OpenStreetMap), dark style, marker pictures | app + web | owner-tested |
| Small phones / large text (`FitText`, `ButtonRow`, `SmallScreenTest`, 320 px web CSS) | app + web | built (owner's screenshot fixed) |
| Fuel & food finder ("On the road ahead": fuel / food / mechanic, never behind you, left/right, Stop here) | app + web | built |
| Low fuel guide (button or "RideComm, low fuel"; rc-fuel; warns at 2 km / 500 m; ends at the pump) | app + web | built, needs a road test |
| Rain alerts (Open-Meteo here + 25 km ahead, off by default) | app + web | built |
| Play Store groundwork (targetSdk 36, `play` build + CI bundle, privacy page, store pictures, guide) | app | ready; owner's steps pending |
| Points & badges (points never for speed, 7 levels, 16 badges, This year, Ride points leaderboard, `rc-score`) | app + web | built (4th chat, build #55), web published |
| My garage (bikes, odometer from rides, service by km/months with Done, insurance / PUC / licence dates and reminders) | app + web | built (4th chat); web not published |
| Ride Wrapped (year in review slides, rider type, share picture 1080 × 1920; card 1 Dec – 15 Jan) | app + web | built (4th chat); web not published |

Chronology (git log): step 1 voice call → music → floating button → votes → SOS → glass redesign →
group tracking → Spotify ducking → crash detection, photos → invite links → private ride server →
headset button → wind gate → R8 + split APKs → rejoin → rider alerts → voice commands → visual wind test →
fine-tune + drag-to-hide → speed & trip → data saver, catch-up, phone calls → web version for iPhone →
group map + regroup → web photo/map/settings → hazards, lead & sweep, PTT, volume, night, emergency info
(app, then web) → own photo on the map → talk to one rider, shared destination, home safe, break
reminder, lock-screen emergency info, lock rides to the group (app and web together) → (third chat)
Glass look, Soft look, Google Maps group map, small-screen fixes, ride history / planner / family watching,
fuel & food finder + low fuel + rain alerts, Play Store groundwork → (fourth chat) Points & badges with a ride
leaderboard, My garage, Ride Wrapped.

## Glass look

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

## Road ahead, rain alerts, Play Store (Oct 2026)

- `nearby/AheadLogic.kt` (pure: "ahead" cone 40°, ranking along + 2 × across, left/right, parsers),
  `nearby/PoiSearch.kt` (Google Places (New) REST with the Android key's package/cert headers, falls back to
  Overpass on 3 servers; a 400–403 from Google switches to OSM until restart), `nearby/LowFuel.kt`
  (`TravelDirection`, low fuel guide, `rc-fuel` message), `ui/RoadUi.kt`. Voice: "RideComm, low fuel".
  Google Places needs the owner to enable **Places API (New)** and add it to both keys' API restrictions;
  until then OSM is used (5,000 free searches a month on Google).
- `weather/RainWatch.kt` (`RainLogic` pure + manager polling Open-Meteo every 15 min).
- Play Store: `PLAY_STORE.md` (owner's steps, listing text, data safety answers), `docs/privacy/`,
  `design/play/` (store pictures from `StoreShotTest`, run with `STORE_SHOTS=1`), build type `play`
  signed with the upload key from secrets, CI artifact `RideComm-play-N.aab`. targetSdk is 36; Robolectric
  runs tests on SDK 35 (`app/src/test/resources/robolectric.properties`) because SDK 36 needs JDK changes.

## Points & badges (Oct 2026, fourth chat)

The owner picked "Badges & yearly stats" and asked to "gamify it, points system and all". Rules in PROTOCOL.md
"Points & badges" (app and web must agree; the web test `points.test.js` checks the app's own test numbers).
- App: `score/ScoreLogic.kt` (pure: `ScoreEntry` facts, `ScoreRules` points, `Levels`, `Badge`, `ScoreBook` badges /
  year / week streak, `BreakCounter`), `score/Score.kt` (score book `files/score.json`, the ride going on, `rc-score`),
  `ui/PointsUi.kt` (home card, Points & badges page, Ride points card, the summary's Points card). Hooks: `RideManager`
  (begin/finish/attach/…), `Hazards.mark` (a new spot), `HomeSafe.imHome` / `checkInLater`. Distance and riding time
  come from `TripTracker`, so points work with Ride history off. Linked to a history ride by its start (within a minute).
- Web: the `SC` block in `docs/ride/index.html` (`rc-web-score`), counting from the ride recording `REC`, which now
  runs when Ride history *or* Points is on (rides are only written with history on).
- Points never reward speed on purpose; caps (5 riders, 3 breaks, 5 hazards) stop farming.

## My garage (Oct 2026, fourth chat)

Picked from a "something different" menu with Ride Wrapped. Rules in PROTOCOL.md "My garage".
- App: `garage/GarageLogic.kt` (pure: `Garage`, `Bike`, `ServiceItem`, urgency, notes, alerts, the ride-start
  reminder, reminder times, JSON shared with the web), `garage/MyGarage.kt` (Prefs `garage_json`, AlarmManager
  notifications for papers via `MyGarage$Reminder`, re-set after reboot), `ui/GarageUi.kt` (home card, page, dialogs;
  dates with the platform `DatePickerDialog`, "Clear" removes one). `RideManager` calls `onRideStart` /
  `onRideEnd(km from TripTracker)`.
- Web: the `GR` / `GV` block (`rc-web-garage`), editing inside the sheet; `<input type="date">` for papers and an .ics
  with 30- and 7-day alarms. The ride recording (`REC`) now also runs for the garage.

## Ride Wrapped (Oct 2026, fourth chat)

Rules in PROTOCOL.md "Ride Wrapped". App: `score/Wrapped.kt` (`WrappedLogic.build` from the score book + history
routes, comparisons, `RiderType`), `ui/WrappedUi.kt` (`WrappedCard`, `WrappedScreen` / `WrappedStory` slides as
composable lambdas, `WrappedPicture` share image). Opened from the Points & badges page and, in season, the home
card. Web: the `WV` block (`#wrapView` overlay, `wrappedPicture` canvas). Both tests (`WrappedLogicTest`,
`wrapped.test.js`) use the same rides and expect the same numbers.

## Owner's feedback of 13 Oct and the fixes

- **Settings too long** → Settings in groups, app and web: profile, then Look & map, Talking & sound, Alerts,
  Group & location, Safety & SOS, Ride tools, Points & badges, Over other apps (app only), Ride server (not in a
  ride on the app). App: `SettingsSection` + `SettingsRow` in `HomeScreen.kt`, the old controls moved into a
  `when (section)`; `GlassDialog(scrollKey)` scrolls a new page to the top; `startGroup` opens one for screenshots.
  Web: `SET_GROUPS` / `SG` (every switch stays in the page, other groups' ones get `.sethide`); web tests open a
  group with `[data-setgroup][data-keys~="<key>"]` before clicking a switch (`settings.test.js` checks the groups).
- **Ride summary map showed the whole world** (the camera was fitted before the pop-up had its size) and a tap
  opened the Google Maps app with nothing in it → fitted on layout (`doOnLayout`), taps no longer leave the app,
  and "Explore route" opens the route full screen to pan and zoom (`RouteMap(interactive = true)`).
- **A ride that never moved got 16 km of points**: `Score` read the trip counter before `TripTracker` had reset
  it for the new ride, so it kept the last ride's 16 km and 36 min. Now it only reads a counter started for this
  ride; `Score.repairOnce` mends entries with more km than the ride history recorded (once, flag
  `score_repaired_1`). Hazard points now also need a ride of 5 km or more (no farming while parked). The web page
  never had the distance bug (it counts from its own recording).

## Owner's feedback of 13 Oct (second round)

- **Night mode turned everything red** → Settings → Look & map → **Night look: Dark** (new default: the dark
  page, a little dimmer) or **Red** (the old red filter).
  App: `NightStyle`, `NightMode.style`, `nightFilter(on, style)`; web: `SET.nightStyle`, classes `night-dark` /
  `night-red`, `#nightDim`.
- **Soft look jumped to Classic at night** (the first Dark night look did that) → **dark Soft**: the Soft look
  stays Soft and turns dark (deep slate page, same raised cards, light text, bright accents). App:
  `NeuTokens.dark` (a snapshot state set by MainActivity; every token is a getter with a light and a dark value,
  `neuTypography` too; use `NeuTokens.SurfaceTop` instead of `Color.White` in Soft gradients); screenshots
  150–158. Web: the "Dark Soft" block of `html.look-neu.night-dark` styles (variables + the hard-coded tints).
- **Night mode locked the phone's brightness** ("brightness is controlled by RideComm") → removed: the app no
  longer sets the window's `screenBrightness`; the dark colours (and the 18% dim layer) are enough, and the
  rider keeps the brightness slider.
- **Place search found much less than Google Maps, no way to pick on a map** → PROTOCOL.md "Choosing a place":
  search as you type, Google Places first (needs **Places API (New)** on both keys, still waiting on the owner),
  then Photon, then Nominatim; "Pick on the map" (app: `MapPicker` + `ui/map/PickerMap.kt`; web: `#pickView`,
  engines got `center()` / `onIdle()`); short Google Maps links followed in the app. App code: `group/PlaceSearch`,
  `group/GoogleApi` (the key + Android headers, shared with `PoiSearch`).

## Ideas not built yet (offered, not picked yet)

- **Group challenges**: a monthly goal for the group ("1,000 km together in October") and a month leaderboard
  among riding friends (builds on Points & badges).
- **Hindi language**: app, web page and spoken alerts in Hindi (a Settings choice).
- **Missed voice replay**: what was said while you were in a dead zone plays when you're back
  (catch-up today only covers open votes and SOS).
- **Fuel range reminder**: set the bike's range; warn at 80% and offer a Fuel vote (Low fuel exists).
- **Rider chat log**: scrollable history of the ride's messages, votes and alerts.
- **Private ride server**: everything is ready (app switch, web settings, keys in invite links, CORS,
  watch page); the owner still has to deploy it (see "Where things stand"). Important before sharing the
  app widely: the sandbox token server lets anyone with the ID join.
- From the fourth chat's menus: **Shared route + off-route alert** (the lead's route on everyone's map), **Find
  a rider** (compass arrow and distance), **Daylight warning** ("Sunset in 30 minutes, 45 km to go"), **Trip
  expenses split**, **Read my messages aloud** (WhatsApp/SMS with voice reply), **Ride moments** (photo or spot
  saved on the route), **Ride reel** (a short video of the route drawing itself), **Custom quick messages**,
  **Weather for planned rides**.
- Smaller: a "Hazard" option on the floating button, voice commands on the web page, night mode for the
  floating overlays.

Ideas looked at and dropped: Bluetooth or Wi-Fi Direct voice instead of the internet (10–100 m range,
phones can't do a mesh group call; voice is only ~10 MB an hour anyway).

## Production push (13 Oct, after build #64)

The owner asked whether the app is production ready (Android only); the honest answer was "ready for your group
and a closed beta, not yet the public": sandbox token server open to anyone, tested on one phone, safety features
unproven, ~40 features little ridden, crashes not reaching the developer, Play review of background mic/location
and the overlay, costs at scale. The plan: private ride server → feature freeze → closed test with 12+ riders on
different brands → crash reports → test SOS/crash on purpose → fix → Play Store. Done in this round:
- **Crash detection didn't fire** when the owner threw the phone: at home GPS says "standing still", which the
  rule reads as "dropped at a stop"; 50 Hz sampling also missed short hits; and it only runs during a ride. Now
  100 Hz, a fall + landing also counts, 10 km/h in the last 45 s, gives up early when the phone is picked up, and
  **Test crash detection** (`CrashTestUi.kt`, `CrashDetector.startTest`, `CrashLogic(requireRiding = false)`).
- **Battery savers** (`ride/BatteryGuard.kt`, `BatteryTips`): home card + Settings button, brand steps.
- **First join in a dead zone** ended the ride at once: now ~1 minute of retries (`FIRST_JOIN_TRIES`), only a
  setting the rider must fix (`RidePassError.retryable = false`) ends it straight away.
- **SOS alone in the ride** said "Sent to the group" to nobody: now SMS fallback + "No other rider is online", and
  a joining rider gets the SOS (`SosManager.onRiderJoined`; web `onJoined`). Test: `tools/webtest/sos.test.js`.
- **Foreground service** falls back to microphone-only if Android refuses the location type (no crash).
- **Microphone refused for good** ("Don't allow"): opens App info instead of a dead end.
- **Report a problem** + crash report via `Support.kt` (`Support.EMAIL` empty until the owner gives an address).
- Night mode no longer sets the window brightness (the owner couldn't raise it); dark Soft instead of Classic.

## Known limits

- iPhone: Safari pauses the mic and location when the screen locks or another app is in front; keep the
  page open ("Keep screen on" is on by default). Safari can't change one rider's volume (mute works).
- The sandbox token server is open to anyone with its ID (see Private ride server above).
- SOS by SMS opens Messages with the text ready; the rider taps Send (silent SMS needs SEND_SMS, which
  Play Protect blocks for sideloaded apps).
- The web page asks for location when joining (Ride history is on by default); if refused, everything
  else still works without a route.
- Places search distances are straight-line, and the public OpenStreetMap search servers are sometimes
  busy (three are tried in turn). A pump on the far side of a divided highway can still be picked.
- A family link to a ride locked to the group can't connect until the private ride server is deployed.
- Robolectric runs the unit tests on SDK 35 (it can't simulate 36 on this JDK); the app targets 36.
- Talk to one rider is enforced by the listeners' phones (they silence the talker), not by the server: fine
  for a group of friends, not a secret channel. Riders on an old app version still hear everything.
- Points, garage and Wrapped live only on each phone: uninstalling the app (or clearing the browser on iPhone)
  loses them, and two phones don't merge. Points only started counting at build #55, so 2026's Wrapped only
  covers rides since then. Switching Points on in the middle of a ride counts from the next ride in the app (the
  web page starts at once). The Ride points board only shows riders on build #55 or newer.
- The garage odometer adds my GPS distance per ride: rides with location off, or GPS lost in tunnels, add less,
  so the rider should correct the odometer now and then (Edit bike). On the web page, paper reminders are a
  calendar file (no notifications).
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
3. `./gradlew testDebugUnitTest` should pass (about 570 tests).
4. Store pictures (only when needed): `STORE_SHOTS=1 ./gradlew recordRoborazziDebug --tests '*StoreShotTest*'`.
5. Web tests: see `tools/webtest/README.md` (13 suites: rides, features, more, gmap, plans, watch, road, points,
   garage, wrapped, settings, places, sos; all print "No page errors"). Suites that search places fake Photon as down
   so Nominatim (faked) answers.
6. Skills for Claude in this repo: `.claude/skills/claude-delegate-and-budget` (linked to `.agents/skills/`,
   installed with `npx skills add piplupjs/skills --skill claude-delegate-and-budget`, pinned in
   `skills-lock.json`): when to hand work to helper agents and which model / effort to use.
6. GitHub access goes through the GitHub MCP tools (no `gh` CLI in the container).
