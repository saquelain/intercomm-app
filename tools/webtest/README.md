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

Each prints `PASS` / `FAIL` lines and ends with "No page errors". Screenshots go to `out/`.

```sh
cd docs && python3 -m http.server 8765 &        # from the repo root
mkdir -p tools/webtest/vendor && cd tools/webtest/vendor && \
  curl -sSO https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js && \
  curl -sSO https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css   # optional, faster map
node tools/webtest/rides.test.js
node tools/webtest/features.test.js
node tools/webtest/more.test.js
```

In the Claude cloud container Playwright lives at `/opt/node22/lib/node_modules/playwright` (set
`PLAYWRIGHT=...` elsewhere) and Chromium needs `--proxy-server=https=$HTTPS_PROXY`, which the tests
already pass. Map tiles come from `app/src/test/resources/maptiles`.
