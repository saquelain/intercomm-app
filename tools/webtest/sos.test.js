// Run from the repo root with the docs/ folder served on :8765 (see README.md here).
const { chromium } = require(process.env.PLAYWRIGHT || '/opt/node22/lib/node_modules/playwright');
const fs = require('fs');
const fake = fs.readFileSync(__dirname + '/fake-livekit.js', 'utf8');
// Screenshots go to tools/webtest/out/ (gitignored).
const OUT = __dirname + '/out/' + (process.env.OUT_SUB ? process.env.OUT_SUB + '/' : '');
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const browser = await chromium.launch({ args: ['--proxy-server=https=' + process.env.HTTPS_PROXY] });
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: +(process.env.VIEW_W || 400), height: +(process.env.VIEW_H || 860) }, deviceScaleFactor: 2 });
  // The page's Google key only works on the real site: test the OpenStreetMap map here (gmap.test.js does Google).
  await ctx.route(/127\.0\.0\.1:8765\/ride\/(\?|$)/, async (r) => {
    const res = await r.fetch();
    r.fulfill({ response: res, body: (await res.text()).replace(/const GMAPS_KEY = '[^']*';/, "const GMAPS_KEY = '';") });
  });
  await ctx.route(/livekit-client/, (r) => r.fulfill({ contentType: 'application/javascript', body: fake }));
  await ctx.route(/sandbox\/connection-details/, async (r) => {
    const b = JSON.parse(r.request().postData());
    r.fulfill({ contentType: 'application/json', body: JSON.stringify({ server_url: 'wss://fake', participant_token: Buffer.from(JSON.stringify({ identity: b.participant_identity, name: b.participant_name })).toString('base64') }) });
  });
  // Leaflet through the proxy is slow; use a local copy when there is one (see README.md here).
  await ctx.route(/leaflet\.min\.(js|css)$/, (r) => {
    const f = __dirname + '/vendor/leaflet.min.' + (r.request().url().endsWith('css') ? 'css' : 'js');
    return fs.existsSync(f) ? r.fulfill({ path: f }) : r.continue();
  });
  const tiles = __dirname + '/../../app/src/test/resources/maptiles/';
  await ctx.route(/tile\.openstreetmap\.org/, (r) => { const [x, y] = r.request().url().match(/(\d+)\/(\d+)\.png/).slice(1).map(Number); r.fulfill({ path: tiles + 't_' + (x % 3) + '_' + (y % 5) + '.png' }); });
  await ctx.addInitScript(() => {
    window.__spoken = [];
    speechSynthesis.speak = (u) => { if (u.text) window.__spoken.push(u.text); window.__vol = u.volume; };
    navigator.geolocation.getCurrentPosition = (ok) => ok({ coords: { latitude: 12.9716, longitude: 77.5946 } });
    window.__watchers = [];
    navigator.geolocation.watchPosition = (ok) => { window.__watchers.push(ok); if (window.__fix) setTimeout(() => ok({ coords: window.__fix }), 30); return window.__watchers.length; };
    navigator.geolocation.clearWatch = () => {};
    window.__move = (fix) => { window.__fix = fix; window.__watchers.forEach((w) => w({ coords: fix })); };
    window.confirm = () => true;
  });
  const errors = [];
  const mk = async (name, id) => {
    const page = await ctx.newPage();
    page.on('pageerror', (e) => errors.push(name + ': ' + e.message));
    await page.addInitScript((i) => { localStorage.setItem('rc-web-id', i); localStorage.removeItem('rc-web-settings'); localStorage.removeItem('rc-web-info'); localStorage.removeItem('rc-web-vol'); }, id);
    await page.goto('http://127.0.0.1:8765/ride/?code=TSTSXP');
    await page.fill('#nameIn', name);
    await page.click('#joinBtn');
    await page.waitForSelector('#ride:not(.hidden)', { timeout: 10000 });
    return page;
  };
  const spoken = (p) => p.evaluate(() => window.__spoken.splice(0));
  const check = (label, cond) => console.log((cond ? 'PASS ' : 'FAIL ') + label);
  const settings = async (p, fn) => { await p.click('[data-open-settings]:visible'); await fn(); await p.click('#settingsClose'); };

  // Alone in the ride: SOS must not claim "sent to the group".
  const a = await mk('Asha', 'sos-a');
  await a.waitForTimeout(300);
  await a.click('#sosBtn');
  await a.waitForTimeout(5800);
  const mine = await a.textContent('#mySosText');
  check('Alone: SOS says no other rider is online', mine.includes('No other rider is online'));
  check('Alone: spoken the same', (await spoken(a)).some((t) => t.includes('No other rider is online')));
  await a.screenshot({ path: OUT + 'sos1_alone.png' });

  // A rider joins: they get the SOS at once, and it counts as sent.
  const b = await mk('Bilal', 'sos-b');
  await b.waitForTimeout(800);
  check('The rider who joins gets the SOS', (await b.innerHTML('#alerts')).includes('Asha'));
  check('Then it says sent to the group', (await a.textContent('#mySosText')).includes('Sent to the group'));
  check('And says so aloud', (await spoken(a)).some((t) => t.includes('SOS sent to them')));

  console.log(errors.length ? 'Page errors:\n' + errors.join('\n') : 'No page errors');
  await browser.close();
  process.exit(errors.length ? 1 : 0);
})();
