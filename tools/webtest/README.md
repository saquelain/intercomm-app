# Web ride tests

Drive `docs/ride/index.html` in headless Chromium with two riders (Asha and Bilal) in one browser.
Real LiveKit can't connect from the cloud container, so `fake-livekit.js` replaces the LiveKit library:
pages in the same browser share a "ride" over a BroadcastChannel (text and byte streams, joins, leaves,
mute, a fake signal drop with `goOffline()` / `goOnline()`). GPS is faked too: `window.__move({...})`.

- `rides.test.js`: votes, quick messages, catch-up after a drop, SOS, profile photo, group map,
  regroup points, mute, leaving.
- `features.test.js`: emergency info with SOS, hazards and the "Pothole in 300 meters" warning, lead &
  sweep (and the ahead-of-lead alert), per-rider volume / mute for me, push to talk, night mode.
- `more.test.js` (three riders): talk to one rider (hold in the sheet, hold to reply, hold a rider's row),
  shared destination (search, pasted maps link, arrival, clear), break reminder (clock moved forward), home
  safe (card, leave dialog, later check-in under `~home`), and rides locked with a group key from the link.
- `plans.test.js`: ride history (a ride recorded from fake GPS moves with the clock moved on: distance, moving time,
  top speed, a 4-minute stop; the summary after leaving, the share picture as a download and through the share sheet,
  "Your rides", "See all", delete, Clear ride history, the switch off), the ride planner (making a plan with "My
  location", pasted coordinates and searches; the invite link's `#p=`; the calendar file; Join from the plan, the plan
  card in the ride and "Set as destination"; opening a `#p=` link, with `#k=` too) and the invite page (the plan, the
  app and web links passing `p` and `k`, Add to calendar, no plan, iPhone).
- `watch.test.js`: family watching: the watch page (waiting, live, the SOS alarm and "Seen", a late watcher catching
  up, faded old positions, weak network, dropped and rejoined, the ride ended, typing a code) and the ride page's
  side (watchers not counted or announced, "Family watching: Ammi", positions and SOS reach family only with
  "Family can watch" on, the Share choice and family link, "the lowest id answers" ignoring watchers).
- `road.test.js`: on the road ahead and rain. The Fuel / Food / Mechanic finder (only places ahead: one behind or off to
  the side is left out even when closer; "on your left / right"; nearest in any direction when standing still, or towards
  the destination; Navigate; "Stop here" sets a regroup point for everyone; nothing found; all three OpenStreetMap servers
  down, then only the first), low fuel (the best pump ahead said, "in 2 kilometres", "in 500 metres, on your right" with a
  buzz, a passed pump skipped for the next, no pump found, searching again after a minute and after 8 km, off by itself
  after 90 s stopped at a pump, Got fuel, leaving; the other rider hears "Asha is low on fuel" / "has filled up", watchers
  and check-ins ignored), Google Places with a stand-in key and a stand-in `google.maps.importLibrary('places')` (two
  searches merged) and its fall-back to OpenStreetMap for the rest of the visit, rain alerts (the Open-Meteo request,
  already raining, rain here in 30 minutes with the pill, not repeated, rain ahead, an unlikely shower, one place when
  there's no direction, switched off) and both Settings switches. Overpass, Google and Open-Meteo are all faked by routes.
- `points.test.js`: points & badges. A ride earning points (74 km in two halves with an 11-minute break, a pothole,
  riding sweep, with Bilal), both phones' "Ride points" cards, the summary's Points card and "New badge: First ride",
  home safe later (+15), the join-screen card, the Points & badges page in all three looks, Reset; a ready-made score
  book with the app's test numbers (788 points, a 3-week streak, the badges); the switch off and on mid-ride; points
  with Ride history off.
- `garage.test.js`: My garage. Adding a bike (the usual items, stored like the app), the join card ("All good: next…"),
  editing an item until it's due, papers (ends in 12 days, expired 3 days ago, the calendar file), the reminder when a
  ride starts, a ride's km on the odometer, Done, a second bike and switching, a new odometer, an own item and
  removing it, removing a bike, the switch off (rides add nothing), screenshots in the three looks.
- `wrapped.test.js`: Ride Wrapped with the clock set to October, December and January: the "so far" button, the
  December card, every slide with the app test's numbers, tapping back, the 1080 × 1920 share picture, Watch again, a
  year without rides, the switch off.
- `gmap.test.js`: the Group map on Google Maps (Google's real script in keyless mode, served a stand-in key),
  long press, tap a rider, dark map, switching to OpenStreetMap in Settings, and a refused key falling back.

Set `VIEW_W=320 VIEW_H=640 OUT_SUB=small` to run any of them on the smallest iPhone width, with
screenshots in `out/small/`.

Each prints `PASS` / `FAIL` lines and ends with "No page errors". Screenshots go to `out/`.

```sh
cd docs && python3 -m http.server 8765 &        # from the repo root
mkdir -p tools/webtest/vendor && cd tools/webtest/vendor && \
  curl -sSO https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js && \
  curl -sSO https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css   # optional, faster map
node tools/webtest/rides.test.js
node tools/webtest/features.test.js
node tools/webtest/more.test.js
node tools/webtest/gmap.test.js
node tools/webtest/plans.test.js
node tools/webtest/watch.test.js
node tools/webtest/road.test.js
node tools/webtest/points.test.js
node tools/webtest/garage.test.js
node tools/webtest/wrapped.test.js
```

In the Claude cloud container Playwright lives at `/opt/node22/lib/node_modules/playwright` (set
`PLAYWRIGHT=...` elsewhere) and Chromium needs `--proxy-server=https=$HTTPS_PROXY`, which the tests
already pass. Map tiles come from `app/src/test/resources/maptiles`.
