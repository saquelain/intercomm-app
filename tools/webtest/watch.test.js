// Run from the repo root with the docs/ folder served on :8765 (see README.md here).
// Family watching: the watch page (waiting, live map, SOS alarm, reconnecting, the ride ended) and the ride page's
// side of it: watchers aren't riders, and each rider's "Family can watch" decides whether family get their position and SOS.
const { chromium } = require(process.env.PLAYWRIGHT || '/opt/node22/lib/node_modules/playwright');
const fs = require('fs');
const fake = fs.readFileSync(__dirname + '/fake-livekit.js', 'utf8');
const OUT = __dirname + '/out/' + (process.env.OUT_SUB ? process.env.OUT_SUB + '/' : '');
fs.mkdirSync(OUT, { recursive: true });
const BASE = 'http://127.0.0.1:8765/';

(async () => {
  const browser = await chromium.launch({ args: ['--proxy-server=https=' + process.env.HTTPS_PROXY] });
  const VIEW = { width: +(process.env.VIEW_W || 400), height: +(process.env.VIEW_H || 860) };
  const errors = [];
  const check = (label, cond) => console.log((cond ? 'PASS ' : 'FAIL ') + label);
  const pass = (b) => JSON.stringify({ server_url: 'wss://fake', participant_token: Buffer.from(JSON.stringify({ identity: b.participant_identity, name: b.participant_name })).toString('base64') });
  const tiles = __dirname + '/../../app/src/test/resources/maptiles/';
  async function newCtx() {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, viewport: VIEW, deviceScaleFactor: 2 });
    // The Google key only works on the real site: the OpenStreetMap map here.
    await ctx.route(/127\.0\.0\.1:8765\/(ride|watch)\/(\?|$)/, async (r) => {
      const res = await r.fetch();
      r.fulfill({ response: res, body: (await res.text()).replace(/const GMAPS_KEY = '[^']*';/, "const GMAPS_KEY = '';") });
    });
    await ctx.route(/livekit-client/, (r) => r.fulfill({ contentType: 'application/javascript', body: fake }));
    await ctx.route(/sandbox\/connection-details/, (r) => r.fulfill({ contentType: 'application/json', body: pass(JSON.parse(r.request().postData())) }));
    await ctx.route(/nominatim\.openstreetmap\.org/, (r) => r.fulfill({ contentType: 'application/json', body: JSON.stringify([
      { lat: '18.7546', lon: '73.4062', name: 'Lonavala', display_name: 'Lonavala, Maval, Pune, Maharashtra, India' }]) }));
    await ctx.route(/leaflet\.min\.(js|css)$/, (r) => {
      const f = __dirname + '/vendor/leaflet.min.' + (r.request().url().endsWith('css') ? 'css' : 'js');
      return fs.existsSync(f) ? r.fulfill({ path: f }) : r.continue();
    });
    await ctx.route(/tile\.openstreetmap\.org/, (r) => { const [x, y] = r.request().url().match(/(\d+)\/(\d+)\.png/).slice(1).map(Number); r.fulfill({ path: tiles + 't_' + (x % 3) + '_' + (y % 5) + '.png' }); });
    await ctx.addInitScript(() => {
      window.__spoken = [];
      speechSynthesis.speak = (u) => { if (u.text) window.__spoken.push(u.text); };
      navigator.geolocation.getCurrentPosition = (ok) => ok({ coords: window.__fix || { latitude: 18.52, longitude: 73.85 } });
      window.__watchers = [];
      navigator.geolocation.watchPosition = (ok) => { window.__watchers.push(ok); if (window.__fix) setTimeout(() => ok({ coords: window.__fix }), 30); return window.__watchers.length; };
      navigator.geolocation.clearWatch = () => {};
      window.__move = (fix) => { window.__fix = fix; window.__watchers.forEach((w) => w({ coords: fix })); };
      window.confirm = () => true;
      window.__shift = 0;
      const now = Date.now.bind(Date);
      Date.now = () => now() + window.__shift;
      window.__shared = [];
      navigator.share = async (d) => { window.__shared.push({ text: d.text, url: d.url }); };
      window.__buzz = [];
      navigator.vibrate = (p) => { window.__buzz.push(p); return true; };
    });
    return ctx;
  }
  async function rider(ctx, name, id, settings, extra) {
    const p = await ctx.newPage();
    p.on('pageerror', (e) => errors.push(name + ': ' + e.message));
    await p.addInitScript(([i, s, x]) => {
      if (sessionStorage.getItem('set-up')) return;
      sessionStorage.setItem('set-up', '1');
      localStorage.setItem('rc-web-id', i);
      localStorage.setItem('rc-web-settings', s);
      Object.entries(x || {}).forEach(([k, v]) => localStorage.setItem(k, v));
    }, [id, JSON.stringify(settings || {}), extra]);
    await p.goto(BASE + 'ride/?code=TSTWCH');
    await p.fill('#nameIn', name);
    await p.click('#joinBtn');
    await p.waitForSelector('#ride:not(.hidden)', { timeout: 10000 });
    return p;
  }
  async function watcher(ctx, label, storage) {
    const p = await ctx.newPage();
    p.on('pageerror', (e) => errors.push(label + ': ' + e.message));
    if (storage) await p.addInitScript((x) => {
      if (sessionStorage.getItem('set-up')) return;
      sessionStorage.setItem('set-up', '1');
      Object.entries(x).forEach(([k, v]) => localStorage.setItem(k, v));
    }, storage);
    await p.goto(BASE + 'watch/?code=TSTWCH');
    return p;
  }
  const spoken = (p) => p.evaluate(() => window.__spoken.splice(0));
  const box = (w) => w.textContent('#box');
  const state = (w) => w.textContent('#stateText');

  const ctx = await newCtx();
  // ---- Family member opens the link before anyone rides ----
  const w = await watcher(ctx, 'Ammi');
  check('Watch page: code from the link shown, name defaults to "Family"', (await w.textContent('#codeShowText')) === 'TSTWCH' && await w.isHidden('#codeWrap') && (await w.inputValue('#nameIn')) === 'Family');
  await w.screenshot({ path: OUT + 'f1_watch_setup.png' });
  await w.fill('#nameIn', 'Ammi');
  await w.click('#startBtn');
  await w.waitForSelector('#watch:not(.hidden)');
  await w.waitForTimeout(500);
  check('Identity watch-… kept on this phone, name remembered', (await w.evaluate(() => localStorage.getItem('rc-watch-id'))).startsWith('watch-') &&
    (await w.evaluate(() => localStorage.getItem('rc-watch-name'))) === 'Ammi');
  check('Nobody riding: "Waiting for the ride to start"', (await state(w)) === 'Waiting for the ride to start' && (await box(w)).includes('Waiting for the ride to start'));
  await w.screenshot({ path: OUT + 'f2_watch_waiting.png' });

  // ---- Riders join: Asha (Group map on, emergency info) and Bilal (Group map on), both with "Family can watch" off ----
  const info = JSON.stringify({ blood: 'O+', medical: 'Allergic to penicillin', contact: 'Ammi', phone: '+91 98450 12345' });
  const a = await rider(ctx, 'Asha', 'web-a', { map: true }, { 'rc-web-info': info });
  const b = await rider(ctx, 'Bilal', 'web-b', { map: true, look: 'glass' });
  await a.waitForTimeout(600);
  const aSaid = await spoken(a);
  check('Riders don\'t announce the watcher', aSaid.includes('Bilal joined the ride') && !aSaid.some((t) => t.includes('Ammi')));
  check('Riders count only riders: "2 riders"', (await a.textContent('#statusText')).includes('2 riders') && (await a.$$('#riders .rider')).length === 2);
  check('"Family watching: Ammi" on the riders\' screens', (await a.textContent('#famPill')).includes('Family watching: Ammi') && (await a.textContent('#famPill')).includes("they can't see you"));
  check('Watch page: "Live · 2 riders" with both listed', (await state(w)) === 'Live · 2 riders' && (await box(w)).includes('Asha') && (await box(w)).includes('Bilal'));
  check('Watch page takes no audio and plays nothing', await w.evaluate(() => document.querySelectorAll('audio').length === 0));
  // Both ride on: with "Family can watch" off, positions go to the other rider only.
  await a.evaluate(() => window.__move({ latitude: 18.52, longitude: 73.85, accuracy: 8, speed: 10, heading: 0 }));
  await b.evaluate(() => window.__move({ latitude: 18.515, longitude: 73.852, accuracy: 8, speed: 10, heading: 0 }));
  await a.waitForTimeout(6500);
  check('Riders still see each other on the map', (await b.textContent('#mapCard')).includes('2 of 2 riders on the map'));
  check('Family off: the watcher gets no positions', (await w.$$('#map .mk')).length === 0 && ((await box(w)).match(/Not sharing location/g) || []).length === 2);
  await b.screenshot({ path: OUT + 'f3_rider_family_watching_glass.png' });
  // Asha lets family watch.
  await a.click('[data-open-settings]:visible');
  await a.evaluate(() => document.querySelector('[data-setting="family"]').scrollIntoView({ block: 'center' }));
  await a.screenshot({ path: OUT + 'f4_settings_family.png' });
  await a.click('.switch:has([data-setting="family"])');
  await a.click('#settingsClose');
  await w.waitForTimeout(500);
  const wb = await box(w);
  check('Family on: Asha shows on the watch page at once', (await w.$$('#map .mk')).length === 1 && /Asha.*Updated just now · 36 km\/h/.test(wb) && wb.includes('Not sharing location'));
  check('Asha sees that family can see her', (await a.textContent('#famPill')).includes('they see you on the map'));
  // A destination reaches family too.
  await a.click('[data-open-dest]');
  await a.fill('#destQuery', 'Lonavala');
  await a.click('#destSearch');
  await a.click('[data-place="0"]');
  await w.waitForTimeout(400);
  check('Watch page: destination', (await box(w)).includes('Heading to Lonavala') && (await w.$$('#map .pin.dest')).length === 1);
  await w.click('#fitBtn');
  await w.waitForTimeout(700);
  await w.screenshot({ path: OUT + 'f5_watch_live.png' });
  await a.screenshot({ path: OUT + 'f6_rider_family_on.png' });

  // ---- SOS: Bilal's (family off) doesn't reach the watcher; Asha's (family on) does, with her medical info ----
  await spoken(a);
  await b.click('#sosBtn');
  await b.waitForTimeout(5900);
  await w.waitForTimeout(300);
  check('Bilal\'s SOS reaches Asha', (await a.textContent('#alerts')).includes('SOS · Bilal'));
  check('Family off: Bilal\'s SOS doesn\'t reach the watcher', await w.isHidden('#alarm') && !(await box(w)).includes('SOS'));
  await b.click('#okBtn');
  await a.click('#sosBtn');
  await a.waitForTimeout(5900);
  await w.waitForSelector('#alarm:not(.hidden)', { timeout: 3000 }).catch(() => {});
  const alarm = await w.textContent('#alarm');
  check('Family on: Asha\'s SOS sets off the alarm', await w.isVisible('#alarm') && alarm.includes('Asha needs help') && alarm.includes('Sent at'));
  check('Alarm: location link and medical info', (await w.innerHTML('#alarm')).includes('maps.google.com/?q=18.52,73.85') && alarm.includes('O+') && alarm.includes('penicillin') &&
    (await w.innerHTML('#alarm')).includes('tel:+919845012345'));
  check('Alarm buzzes', (await w.evaluate(() => window.__buzz.length)) >= 1);
  await w.screenshot({ path: OUT + 'f7_watch_sos.png' });
  // A second family member opening the link now still gets the SOS (catch-up).
  const w2 = await watcher(ctx, 'Abbu', { 'rc-watch-id': 'watch-abbu', 'rc-watch-name': 'Abbu' });
  await w2.waitForSelector('#alarm:not(.hidden)', { timeout: 6000 }).catch(() => {});
  check('Late watcher (name remembered: starts by itself) gets the SOS too', await w2.isVisible('#alarm') && (await w2.textContent('#alarm')).includes('Asha needs help'));
  check('Riders see both watchers', (await a.textContent('#famPill')).includes('Ammi, Abbu') && (await a.textContent('#statusText')).includes('2 riders'));
  await w.click('#seenBtn');
  await w.waitForTimeout(100);
  const buzzAfter = await w.evaluate(() => window.__buzz.length);
  await w.waitForTimeout(3500);
  check('"Seen" stops the alarm; the SOS stays in the list', await w.isHidden('#alarm') && (await box(w)).includes('SOS · Asha needs help') &&
    (await w.evaluate(() => window.__buzz.length)) === buzzAfter);
  await a.click('#okBtn');
  await w.waitForTimeout(400);
  check('"I\'m OK" clears it on the watch page', !(await box(w)).includes('SOS') && (await w.textContent('#banner')).includes('Asha is OK'));
  await w2.close();

  // ---- Share: with "Family can watch" on, a choice of links ----
  await a.click('#shareBtn');
  check('Share offers "Invite riders" and "Family link"', await a.isVisible('#shareSheet') && (await a.textContent('#shareSheet')).includes('Family link'));
  await a.screenshot({ path: OUT + 'f8_share_sheet.png' });
  await a.click('#shareFamily');
  await a.waitForTimeout(200);
  check('Family link', (await a.evaluate(() => window.__shared.slice(-1)[0].url)) === 'https://saquelain.github.io/intercomm-app/watch/?code=TSTWCH');
  await b.click('#shareBtn');
  await b.waitForTimeout(200);
  check('Family off: Share is the invite, as before', await b.isHidden('#shareSheet') && (await b.evaluate(() => window.__shared.slice(-1)[0].url)) === 'https://saquelain.github.io/intercomm-app/join/?code=TSTWCH');

  // ---- "The lowest id answers" ignores watchers (watch-… sorts before web-…) ----
  await a.click('#leaveBtn');
  await a.click('#leaveGo');
  await b.waitForTimeout(500);
  const c = await rider(ctx, 'Chetan', 'web-c', { map: true });
  await c.waitForTimeout(800);
  check('A new rider still gets the destination from Bilal (not left to the watcher)', (await c.textContent('#destCard')).includes('Heading to Lonavala'));
  check('Watch page: Asha gone, Chetan in', (await state(w)) === 'Live · 2 riders' &&
    JSON.stringify(await w.$$eval('#box [data-rider] .rname', (e) => e.map((x) => x.textContent))) === '["Bilal","Chetan"]');

  // ---- Old positions fade; signal trouble; the ride ends ----
  await b.click('[data-open-settings]:visible');
  await b.click('.switch:has([data-setting="family"])');
  await b.click('#settingsClose');
  await w.waitForTimeout(400);
  await w.evaluate(() => { window.__shift = 3 * 60000; });
  await w.waitForTimeout(5300);
  check('Positions over 2 minutes old are faded', (await w.$$('#map .mk.stale')).length === 1 && (await box(w)).includes('Updated 3 min ago'));
  await w.evaluate(() => { window.__shift = 0; });
  await w.evaluate(() => window.__room.goOffline());
  await w.waitForTimeout(100);
  check('Weak network shown', (await state(w)).includes('reconnecting'));
  await w.evaluate(() => window.__room.goOnline());
  await w.waitForTimeout(300);
  check('Back online', (await state(w)) === 'Live · 2 riders');
  await w.evaluate(() => window.__room.disconnect());
  await w.waitForTimeout(100);
  check('Dropped: "Connection lost, reconnecting…"', (await state(w)) === 'Connection lost, reconnecting…');
  await w.waitForTimeout(3000);
  check('Rejoins by itself', (await state(w)) === 'Live · 2 riders');
  check('After rejoining: destination again (sync)', (await box(w)).includes('Heading to Lonavala'));
  await b.click('#leaveBtn'); await b.click('#leaveGo');
  await c.click('#leaveBtn'); await c.click('#leaveGo');
  await w.waitForTimeout(600);
  check('Everyone left: "The ride has ended"', (await state(w)) === 'The ride has ended' && (await box(w)).includes('Everyone has left the ride'));
  await w.screenshot({ path: OUT + 'f9_watch_ended.png' });
  await ctx.close();

  // ---- The family switch, share sheet and status in Glass and Soft ----
  for (const look of ['glass', 'neu']) {
    const x = await newCtx();
    const r = await rider(x, 'Farah', 'web-f', { map: true, family: true, look });
    const fw = await watcher(x, 'Fam', { 'rc-watch-name': 'Ammi' });
    await fw.waitForSelector('#watch:not(.hidden)');
    await r.waitForTimeout(600);
    check('Status shows the watcher (' + look + ')', (await r.textContent('#famPill')).includes('Family watching: Ammi'));
    await r.screenshot({ path: OUT + 'f10_rider_' + look + '.png' });
    await r.click('#shareBtn');
    await r.waitForTimeout(300);
    await r.screenshot({ path: OUT + 'f11_share_' + look + '.png' });
    await r.click('#shareCancel');
    await r.click('[data-open-settings]:visible');
    await r.evaluate(() => document.querySelector('[data-setting="family"]').scrollIntoView({ block: 'center' }));
    await r.waitForTimeout(300);
    await r.screenshot({ path: OUT + 'f12_settings_' + look + '.png' });
    await x.close();
  }

  // ---- No code in the link: the family member types it ----
  const y = await newCtx();
  const nw = await y.newPage();
  nw.on('pageerror', (e) => errors.push('watch (no code): ' + e.message));
  await nw.goto(BASE + 'watch/');
  check('No code: asks for it', await nw.isVisible('#codeIn'));
  await nw.click('#startBtn');
  check('A wrong code is caught', (await nw.textContent('#err')).includes('6-letter ride code'));
  await nw.fill('#codeIn', 'tstwch');
  await nw.click('#startBtn');
  await nw.waitForSelector('#watch:not(.hidden)');
  check('Typed code works', (await nw.textContent('#title')) === 'Ride TSTWCH' && (await nw.evaluate(() => location.search)) === '?code=TSTWCH');
  await y.close();

  console.log(errors.length ? 'PAGE ERRORS:\n' + errors.join('\n') : 'No page errors');
  await browser.close();
})();
